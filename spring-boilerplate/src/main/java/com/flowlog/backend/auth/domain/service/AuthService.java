package com.flowlog.backend.auth.domain.service;

import com.flowlog.backend.auth.domain.service.dto.AuthUserResponse;
import com.flowlog.backend.code.domain.service.LangService;
import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.constant.YesNo;
import com.flowlog.backend.global.security.AuthenticationSubjects;
import com.flowlog.backend.global.security.KeycloakJwtAuthenticationConverter;
import com.flowlog.backend.role.domain.service.RoleAuthorityService;
import com.flowlog.backend.user.domain.model.User;
import com.flowlog.backend.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 사용자 서비스.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Logging
@Service("authService")
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuthService {

  /** 인증 방식: OAuth2 Bearer JWT (Client Credentials, 서버 간 호출). */
  public static final String AUTH_TYPE_OAUTH2_BEARER = "OAUTH2_BEARER";

  /** 인증 방식: OAuth2 BFF 로그인 (토큰은 서버 세션에만 보관). */
  public static final String AUTH_TYPE_OAUTH2_SESSION = "OAUTH2_SESSION";

  /** 인증 방식: SAML2 (세션). */
  public static final String AUTH_TYPE_SAML2 = "SAML2";

  /** 인증 방식: 기타. */
  public static final String AUTH_TYPE_OTHER = "OTHER";

  private static final String CLIENT_ID_CLAIM = "azp";

  private final UserRepository userRepository;
  private final RoleAuthorityService roleAuthorityService;
  private final KeycloakJwtAuthenticationConverter jwtConverter;
  private final LangService langService;

  /**
   * 현재 로그인 사용자 정보를 조회한다.
   *
   * @param authentication 인증 객체
   * @param locale 요청 Locale (Accept-Language)
   * @return 로그인 사용자 정보
   */
  public AuthUserResponse getCurrentUser(Authentication authentication, Locale locale) {
    String authType = AUTH_TYPE_OTHER;
    String clientId = null;
    String subject = AuthenticationSubjects.subjectOf(authentication);
    boolean serviceAccount = false;
    if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
      authType = AUTH_TYPE_OAUTH2_BEARER;
      clientId = jwtAuthentication.getToken().getClaimAsString(CLIENT_ID_CLAIM);
      serviceAccount = jwtConverter.isServiceAccount(jwtAuthentication.getToken());
    } else if (authentication instanceof OAuth2AuthenticationToken oauth2) {
      authType = AUTH_TYPE_OAUTH2_SESSION;
      clientId = oauth2.getPrincipal().getAttribute(CLIENT_ID_CLAIM);
    } else if (authentication instanceof Saml2Authentication) {
      authType = AUTH_TYPE_SAML2;
    }

    // 사용자 매핑은 IdP 고유ID(sub) 기준 (사용자명은 표시용)
    Optional<User> user = serviceAccount || subject == null ? Optional.empty()
        : userRepository.findByIdpSubjectAndUseYn(subject, YesNo.Y);
    return new AuthUserResponse(
        authType,
        user.map(User::getUserId).orElse(authentication.getName()),
        user.map(User::getUserNm).orElse(null),
        user.isPresent(),
        serviceAccount,
        clientId,
        user.map(found -> roleAuthorityService.findEffectiveRoles(found.getUserId()))
            .orElse(List.of()),
        authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .sorted()
            .toList(),
        langService.resolveUserLang(authentication, locale),
        user.map(User::getLangCd).filter(langService::isActive).orElse(null));
  }
}
