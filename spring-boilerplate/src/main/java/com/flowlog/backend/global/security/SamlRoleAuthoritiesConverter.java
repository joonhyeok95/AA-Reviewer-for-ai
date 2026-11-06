package com.flowlog.backend.global.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.schema.XSAny;
import org.opensaml.core.xml.schema.XSString;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * [SAML2] Assertion → 권한 변환.
 *
 * <ul>
 *   <li>{@code sub} 속성(Keycloak user id, 변경 불가) → DB 권한 매핑 기준.
 *       NameID(사용자명)는 표시용으로만 사용한다.</li>
 *   <li>{@code Role} 속성 중 API 권한 형식({@code xxx.list|add|edit|del},
 *       동의어 write/update/delete - {@link ScopeActions}) = IdP 허용 scope.
 *       SAML 에는 OAuth2 scope 가 없으므로 Keycloak 의 같은 이름 역할로 대신한다.</li>
 *   <li>최종 권한은 {@link AuthorityResolver} (IdP scope ∩ DB 권한)</li>
 * </ul>
 *
 * <p>로그인 후에는 {@link SessionTokenAuthorizationFilter} 가 요청마다 같은 방식으로 다시 계산한다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Component
@RequiredArgsConstructor
public class SamlRoleAuthoritiesConverter
    implements Converter<Assertion, Collection<GrantedAuthority>> {

  /** Keycloak SAML role list mapper 속성 이름. */
  public static final String ROLE_ATTRIBUTE = "Role";

  /** Keycloak user id 속성 이름 (user property mapper: id). */
  public static final String SUBJECT_ATTRIBUTE = "sub";

  private final AuthorityResolver authorityResolver;

  @Override
  public Collection<GrantedAuthority> convert(Assertion assertion) {
    List<String> roles = new ArrayList<>();
    String subject = null;
    for (AttributeStatement statement : assertion.getAttributeStatements()) {
      for (Attribute attribute : statement.getAttributes()) {
        List<String> values = attribute.getAttributeValues().stream()
            .map(SamlRoleAuthoritiesConverter::textOf).filter(Objects::nonNull).toList();
        if (ROLE_ATTRIBUTE.equals(attribute.getName())) {
          roles.addAll(values);
        } else if (SUBJECT_ATTRIBUTE.equals(attribute.getName()) && !values.isEmpty()) {
          subject = values.get(0);
        }
      }
    }
    return authorityResolver.forUser(subject, apiScopes(roles));
  }

  /**
   * 세션에 저장된 SAML 속성으로 권한을 다시 계산한다.
   *
   * @param attributes SAML 속성 (Saml2AuthenticatedPrincipal / Saml2ResponseAssertionAccessor)
   * @return 권한
   */
  public Collection<GrantedAuthority> convert(Map<String, List<Object>> attributes) {
    List<Object> subjects = attributes.getOrDefault(SUBJECT_ATTRIBUTE, List.of());
    String subject = subjects.isEmpty() ? null : String.valueOf(subjects.get(0));
    List<String> roles = attributes.getOrDefault(ROLE_ATTRIBUTE, List.of()).stream()
        .map(String::valueOf).toList();
    return authorityResolver.forUser(subject, apiScopes(roles));
  }

  private static List<String> apiScopes(List<String> roles) {
    return roles.stream().filter(ScopeActions::isApiScope).toList();
  }

  private static String textOf(XMLObject value) {
    String text = null;
    if (value instanceof XSString string) {
      text = string.getValue();
    } else if (value instanceof XSAny any) {
      text = any.getTextContent();
    }
    return text;
  }
}
