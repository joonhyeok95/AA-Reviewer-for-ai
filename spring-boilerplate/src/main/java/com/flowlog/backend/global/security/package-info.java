/**
 * 인증/인가 (Keycloak).
 *
 * <ul>
 *   <li>{@link com.flowlog.backend.global.security.SecurityConfig} : Filter Chain
 *       (OAuth2 Resource Server + SAML2 SP)</li>
 *   <li>{@link com.flowlog.backend.global.security.KeycloakJwtAuthenticationConverter} :
 *       JWT → SCOPE_* + DB ROLE_*</li>
 *   <li>{@link com.flowlog.backend.global.security.SamlRoleAuthoritiesConverter} :
 *       SAML NameID → DB ROLE_*</li>
 *   <li>{@link com.flowlog.backend.global.security.SecurityErrorHandler} :
 *       401(C0009) / 403(C0010) 표준 응답</li>
 * </ul>
 *
 * <p>Keycloak 로컬 환경: {@code infra/keycloak} (docker compose)
 * <br>가이드: {@code docs/design/02_auth_keycloak.md}
 */
package com.flowlog.backend.global.security;
