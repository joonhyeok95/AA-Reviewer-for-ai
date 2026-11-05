package com.flowlog.backend.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.saml2.provider.service.authentication.Saml2AssertionAuthentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2ResponseAssertionAccessor;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 세션 로그인(OAuth2 BFF / SAML2) 요청의 권한을 <b>요청마다</b> 다시 계산하는 필터.
 *
 * <p>[OAuth2 BFF] (AuthorizationFilter 직전에 실행)
 * <ol>
 *   <li>SESSION 쿠키 → Spring Session 이 Redis 에서 세션 로드 → SecurityContext 복원</li>
 *   <li>세션에 저장된 Access Token 을 꺼낸다. 만료가 가까우면 refresh token 으로 갱신한다.
 *       같은 세션의 동시 요청이 각각 갱신하지 않도록 세션 단위로 직렬화하고, 다른 요청/인스턴스가
 *       먼저 갱신해 Redis 에 저장한 토큰이 있으면 그것을 사용한다. (refresh token rotation 대비)</li>
 *   <li>토큰 scope ∩ DB 권한 → 이번 요청의 권한 ({@link AuthorityResolver})</li>
 *   <li>토큰이 없거나 만료·갱신 실패면 세션을 폐기하고 401 (C0009)</li>
 * </ol>
 *
 * <p>[SAML2] Assertion 의 sub/Role 속성 ∩ DB 권한을 요청마다 다시 계산한다. (DB 권한 회수 즉시 반영)
 * SAML 은 토큰 갱신이 없어 IdP 역할 변경을 알 수 없으므로, 로그인 후 {@code max-auth-age} 가 지나면
 * 세션을 폐기해 재로그인(SSO)으로 최신 Assertion 을 받게 한다.
 *
 * <p>계산된 인증 객체는 이번 요청에만 사용하고 세션에 다시 저장하지 않는다.
 * 토큰 값은 응답·로그 어디에도 노출하지 않는다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Slf4j
@RequiredArgsConstructor
public class SessionTokenAuthorizationFilter extends OncePerRequestFilter {

  /** 로그인 시각 세션 속성 (epoch millis). */
  public static final String AUTH_TIME_ATTRIBUTE =
      SessionTokenAuthorizationFilter.class.getName() + ".AUTH_TIME";

  /** HttpSessionOAuth2AuthorizedClientRepository 의 세션 속성 이름. */
  private static final String AUTHORIZED_CLIENTS_ATTRIBUTE =
      HttpSessionOAuth2AuthorizedClientRepository.class.getName() + ".AUTHORIZED_CLIENTS";

  /** 세션 단위 refresh 직렬화용 lock (striped). */
  private static final ReentrantLock[] LOCKS = new ReentrantLock[128];

  static {
    for (int i = 0; i < LOCKS.length; i++) {
      LOCKS[i] = new ReentrantLock();
    }
  }

  private final OAuth2AuthorizedClientManager authorizedClientManager;
  private final OAuth2AuthorizedClientRepository authorizedClientRepository;
  private final ObjectProvider<SessionRepository<? extends Session>> sessionRepository;
  private final AuthorityResolver authorityResolver;
  private final SamlRoleAuthoritiesConverter samlConverter;
  private final AppSecurityProperties properties;
  private final AuthenticationEntryPoint authenticationEntryPoint;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof OAuth2AuthenticationToken oauth2) {
      OAuth2AuthorizedClient client = authorizeSerialized(oauth2, request, response);
      if (isUsable(client)) {
        applyOAuth2Authorities(oauth2, client.getAccessToken());
        chain.doFilter(request, response);
      } else {
        rejectSession(authentication, request, response, "세션의 인증 토큰이 만료되었습니다.");
      }
    } else if (authentication instanceof Saml2Authentication saml) {
      if (isSamlAuthExpired(request)) {
        rejectSession(authentication, request, response, "SAML 로그인 유효시간이 지났습니다.");
      } else {
        applySamlAuthorities(saml);
        chain.doFilter(request, response);
      }
    } else {
      chain.doFilter(request, response);
    }
  }

  // ------------------------------------------------------------------ OAuth2 BFF

  private OAuth2AuthorizedClient authorizeSerialized(
      OAuth2AuthenticationToken oauth2, HttpServletRequest request,
      HttpServletResponse response) {
    HttpSession session = request.getSession(false);
    String sessionId = session == null ? "" : session.getId();
    ReentrantLock lock = LOCKS[Math.floorMod(sessionId.hashCode(), LOCKS.length)];
    lock.lock();
    try {
      adoptNewerStoredClient(oauth2, sessionId, request, response);
      return authorize(oauth2, request, response);
    } catch (OAuth2AuthorizationException e) {
      // refresh token 만료/폐기, 또는 다른 인스턴스가 먼저 갱신(rotation)해 기존 refresh token 이 무효화됨
      log.warn("[SessionToken] 토큰 갱신 실패 user={} error={}", oauth2.getName(),
          e.getError().getErrorCode());
      return storedClient(oauth2, sessionId).filter(this::isUsable).orElse(null);
    } finally {
      lock.unlock();
    }
  }

  private OAuth2AuthorizedClient authorize(
      OAuth2AuthenticationToken oauth2, HttpServletRequest request,
      HttpServletResponse response) {
    return authorizedClientManager.authorize(OAuth2AuthorizeRequest
        .withClientRegistrationId(oauth2.getAuthorizedClientRegistrationId())
        .principal(oauth2)
        .attribute(HttpServletRequest.class.getName(), request)
        .attribute(HttpServletResponse.class.getName(), response)
        .build());
  }

  /** 현재 요청의 세션 토큰이 갱신 대상이면, Redis 에 이미 더 새로운 토큰이 있는지 확인해 사용한다. */
  private void adoptNewerStoredClient(
      OAuth2AuthenticationToken oauth2, String sessionId, HttpServletRequest request,
      HttpServletResponse response) {
    OAuth2AuthorizedClient current = authorizedClientRepository.loadAuthorizedClient(
        oauth2.getAuthorizedClientRegistrationId(), oauth2, request);
    if (current != null && expiresWithin(current, properties.oauth2().refreshSkew())) {
      storedClient(oauth2, sessionId)
          .filter(stored -> isNewer(stored, current))
          .ifPresent(stored ->
              authorizedClientRepository.saveAuthorizedClient(stored, oauth2, request, response));
    }
  }

  @SuppressWarnings("unchecked")
  private Optional<OAuth2AuthorizedClient> storedClient(
      OAuth2AuthenticationToken oauth2, String sessionId) {
    Optional<OAuth2AuthorizedClient> stored = Optional.empty();
    SessionRepository<? extends Session> repository = sessionRepository.getIfAvailable();
    if (repository != null && !sessionId.isEmpty()) {
      try {
        Session session = repository.findById(sessionId);
        Object clients =
            session == null ? null : session.getAttribute(AUTHORIZED_CLIENTS_ATTRIBUTE);
        if (clients instanceof Map<?, ?> map) {
          stored = Optional.ofNullable((OAuth2AuthorizedClient) ((Map<String, Object>) map)
              .get(oauth2.getAuthorizedClientRegistrationId()));
        }
      } catch (RuntimeException e) {
        log.warn("[SessionToken] 저장된 세션 토큰 조회 실패 - {}", e.getClass().getSimpleName());
      }
    }
    return stored;
  }

  private static boolean expiresWithin(OAuth2AuthorizedClient client, Duration skew) {
    Instant expiresAt = client.getAccessToken().getExpiresAt();
    return expiresAt != null && expiresAt.minus(skew).isBefore(Instant.now());
  }

  private static boolean isNewer(OAuth2AuthorizedClient stored, OAuth2AuthorizedClient current) {
    Instant storedAt = stored.getAccessToken().getExpiresAt();
    Instant currentAt = current.getAccessToken().getExpiresAt();
    return storedAt != null && currentAt != null && storedAt.isAfter(currentAt);
  }

  private boolean isUsable(OAuth2AuthorizedClient client) {
    boolean usable = false;
    if (client != null && client.getAccessToken() != null) {
      Instant expiresAt = client.getAccessToken().getExpiresAt();
      usable = expiresAt == null || expiresAt.isAfter(Instant.now());
    }
    return usable;
  }

  private void applyOAuth2Authorities(OAuth2AuthenticationToken oauth2, OAuth2AccessToken token) {
    String subject = oauth2.getPrincipal().getAttribute("sub");
    Collection<GrantedAuthority> authorities =
        authorityResolver.forUser(subject, List.copyOf(token.getScopes()));
    setRequestAuthentication(new OAuth2AuthenticationToken(
        oauth2.getPrincipal(), authorities, oauth2.getAuthorizedClientRegistrationId()));
  }

  // ------------------------------------------------------------------ SAML2

  private boolean isSamlAuthExpired(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    boolean expired = false;
    if (session != null) {
      Object authTime = session.getAttribute(AUTH_TIME_ATTRIBUTE);
      if (authTime instanceof Long millis) {
        expired = Instant.ofEpochMilli(millis).plus(properties.saml().maxAuthAge())
            .isBefore(Instant.now());
      } else {
        // 로그인 시각이 없는 세션(이전 버전 세션 등)은 지금부터 계산
        session.setAttribute(AUTH_TIME_ATTRIBUTE, System.currentTimeMillis());
      }
    }
    return expired;
  }

  private void applySamlAuthorities(Saml2Authentication saml) {
    Map<String, List<Object>> attributes = Map.of();
    if (saml.getPrincipal() instanceof Saml2AuthenticatedPrincipal principal) {
      attributes = principal.getAttributes();
    } else if (saml.getCredentials() instanceof Saml2ResponseAssertionAccessor assertion) {
      attributes = assertion.getAttributes();
    }
    Collection<GrantedAuthority> authorities = samlConverter.convert(attributes);
    Authentication recalculated;
    if (saml instanceof Saml2AssertionAuthentication assertionAuthentication) {
      recalculated = assertionAuthentication.toBuilder()
          .authorities(current -> {
            current.clear();
            current.addAll(authorities);
          })
          .build();
    } else {
      recalculated = new Saml2Authentication(
          (AuthenticatedPrincipal) saml.getPrincipal(), saml.getSaml2Response(), authorities);
    }
    setRequestAuthentication(recalculated);
  }

  // ------------------------------------------------------------------ 공통

  private static void setRequestAuthentication(Authentication authentication) {
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
  }

  private void rejectSession(
      Authentication authentication, HttpServletRequest request, HttpServletResponse response,
      String reason) throws IOException, ServletException {
    log.warn("[SessionToken] {} → 세션 폐기 user={}", reason, authentication.getName());
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    SecurityContextHolder.clearContext();
    authenticationEntryPoint.commence(request, response,
        new InsufficientAuthenticationException(reason));
  }
}
