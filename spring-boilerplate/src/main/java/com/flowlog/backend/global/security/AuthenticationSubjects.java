package com.flowlog.backend.global.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2ResponseAssertionAccessor;

/**
 * 인증 객체 → IdP 사용자 고유ID(sub) 추출.
 *
 * <p>사용자 매핑은 변경 불가한 sub(Keycloak user id) 기준이다. 사용자명은 표시용으로만 쓴다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
public final class AuthenticationSubjects {

  private AuthenticationSubjects() {
  }

  /**
   * IdP 사용자 고유ID.
   *
   * @param authentication 인증 객체
   * @return sub (Bearer: 토큰 sub, BFF: ID Token sub, SAML: sub 속성), 없으면 null
   */
  public static String subjectOf(Authentication authentication) {
    Object subject = null;
    if (authentication instanceof JwtAuthenticationToken jwt) {
      subject = jwt.getToken().getSubject();
    } else if (authentication instanceof OAuth2AuthenticationToken oauth2) {
      subject = oauth2.getPrincipal().getAttribute("sub");
    } else if (authentication instanceof Saml2Authentication saml) {
      if (saml.getPrincipal() instanceof Saml2AuthenticatedPrincipal principal) {
        subject = principal.getFirstAttribute(SamlRoleAuthoritiesConverter.SUBJECT_ATTRIBUTE);
      } else if (saml.getCredentials() instanceof Saml2ResponseAssertionAccessor assertion) {
        subject = assertion.getFirstAttribute(SamlRoleAuthoritiesConverter.SUBJECT_ATTRIBUTE);
      }
    }
    return subject == null ? null : String.valueOf(subject);
  }
}
