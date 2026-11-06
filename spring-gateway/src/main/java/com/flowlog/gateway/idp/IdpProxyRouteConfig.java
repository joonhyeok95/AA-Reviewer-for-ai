package com.flowlog.gateway.idp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

/**
 * 로그인 화면(Keycloak) 경유 라우트 - 같은 네트워크의 다른 PC/모바일에서 IP 로 접속할 때만 사용. (기본 꺼짐)
 *
 * <p>local Keycloak 은 127.0.0.1:7000 에만 열려 있어 다른 기기의 브라우저가 로그인 화면에 갈 수 없다.
 * Keycloak 포트를 직접 여는 대신 Gateway(9909) 로 브라우저용 경로만 전달한다.
 *
 * <ul>
 *   <li>{@code /realms/{realm}/**} : 로그인·토큰·SAML 등 해당 realm 엔드포인트만 (master realm 미노출)</li>
 *   <li>{@code /resources/**} : 로그인 화면 정적 리소스</li>
 *   <li>관리 콘솔({@code /admin/**})은 전달하지 않는다 → 계속 http://localhost:7000 에서만 접속</li>
 *   <li>Keycloak 은 로그인 쿠키를 항상 {@code Secure; SameSite=None} 으로 내려준다. 브라우저는 http://localhost 만
 *       예외로 받아주고 http://{IP} 에서는 버리므로 로그인이 "쿠키를 찾을 수 없습니다" 로 실패한다.
 *       화면·API·로그인이 모두 같은 Origin(9909) 이라 {@code SameSite=Lax} 로 충분하므로
 *       {@code strip-secure-cookie=true}(기본) 이면 이 라우트의 Set-Cookie 에서 Secure 를 빼고 Lax 로 바꾼다.
 *       (HTTP 개발용. HTTPS 로 접속하는 환경에서는 false)</li>
 * </ul>
 *
 * <p>Keycloak 은 {@code KC_HOSTNAME=http://{IP}:9909}, 백엔드는 {@code KEYCLOAK_ISSUER_URI=http://{IP}:9909/realms/flowlog}
 * 로 맞춘다. (scripts/dev-up -Lan 이 설정)
 *
 * @author flowlog
 * @since 2026-11-07
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.gateway.idp-proxy", name = "enabled", havingValue = "true")
public class IdpProxyRouteConfig {

  /** 화면 라우트(order 100)보다 먼저 매칭 */
  static final int ORDER = 50;

  @Bean
  RouteLocator idpProxyRoute(RouteLocatorBuilder builder,
      @Value("${app.gateway.idp-proxy.uri}") String uri,
      @Value("${app.gateway.idp-proxy.realm}") String realm,
      @Value("${app.gateway.idp-proxy.strip-secure-cookie:true}") boolean stripSecureCookie) {
    return builder.routes()
        .route("idp", route -> route.order(ORDER)
            .path("/realms/" + realm + "/**", "/resources/**")
            .filters(filters -> stripSecureCookie
                ? filters
                    .rewriteResponseHeader(HttpHeaders.SET_COOKIE, "(?i);\\s*Secure(?=;|$)", "")
                    .rewriteResponseHeader(HttpHeaders.SET_COOKIE, "(?i)SameSite=None", "SameSite=Lax")
                : filters)
            .uri(uri))
        .build();
  }
}
