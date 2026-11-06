package com.flowlog.backend.global.security;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 운영 보안 설정 기동 검사. ({@code app.security.require-https=true} 인 프로필 - prod)
 *
 * <p>잘못된 설정으로 "조용히" 기동되는 것을 막는다. 하나라도 위반하면 기동 실패.
 * <ul>
 *   <li>IdP issuer / SAML 메타데이터 / SP entityId 는 https (메타데이터의 서명 인증서 위조 방지)</li>
 *   <li>세션 쿠키 Secure</li>
 *   <li>client secret / 세션 암호화 키가 local 기본값이 아님</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Component
public class SecurityStartupValidator implements InitializingBean {

  /** local 전용 기본값 (운영 사용 금지). */
  static final List<String> LOCAL_DEFAULT_SECRETS = List.of(
      "flowlog-bff-local-secret", "Zmxvd2xvZy1sb2NhbC1zZXNzaW9uLWtleS0zMmJ5dGU=");

  private final AppSecurityProperties properties;
  private final String issuerUri;
  private final boolean secureCookie;

  /**
   * 생성자.
   *
   * @param properties 보안 설정
   * @param issuerUri Keycloak issuer
   * @param secureCookie 세션 쿠키 Secure 여부
   */
  public SecurityStartupValidator(
      AppSecurityProperties properties,
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri,
      @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie) {
    this.properties = properties;
    this.issuerUri = issuerUri;
    this.secureCookie = secureCookie;
  }

  @Override
  public void afterPropertiesSet() {
    if (properties.requireHttps()) {
      List<String> errors = new ArrayList<>();
      requireHttps(errors, "issuer-uri", issuerUri);
      requireHttps(errors, "app.security.saml.metadata-uri", properties.saml().metadataUri());
      requireHttps(errors, "app.security.saml.entity-id", properties.saml().entityId());
      if (!secureCookie) {
        errors.add("server.servlet.session.cookie.secure 는 true 여야 합니다.");
      }
      if (LOCAL_DEFAULT_SECRETS.contains(properties.oauth2().clientSecret())) {
        errors.add("app.security.oauth2.client-secret 에 local 기본값을 사용할 수 없습니다.");
      }
      String key = properties.session() == null ? null : properties.session().encryptionKey();
      if (StringUtils.isBlank(key) || LOCAL_DEFAULT_SECRETS.contains(key)) {
        errors.add("app.security.session.encryption-key 에 운영 키를 지정해야 합니다.");
      }
      if (!errors.isEmpty()) {
        throw new IllegalStateException("운영 보안 설정 오류: " + String.join(" / ", errors));
      }
    }
  }

  private static void requireHttps(List<String> errors, String name, String value) {
    if (!StringUtils.startsWithIgnoreCase(value, "https://")) {
      errors.add(name + " 는 https 여야 합니다. (현재: " + value + ")");
    }
  }
}
