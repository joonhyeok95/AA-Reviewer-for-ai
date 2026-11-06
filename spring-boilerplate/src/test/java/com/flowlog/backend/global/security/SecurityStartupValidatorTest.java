package com.flowlog.backend.global.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 운영 보안 설정 기동 검사 테스트.
 *
 * @author flowlog
 * @since 2026-11-05
 */
class SecurityStartupValidatorTest {

  private static final String PROD_KEY = "c2VjcmV0LXByb2Qta2V5LWZvci10ZXN0LTMyYnl0ZXM=";

  private static AppSecurityProperties properties(
      boolean requireHttps, String metadataUri, String clientSecret, String sessionKey) {
    return new AppSecurityProperties(
        new AppSecurityProperties.OAuth2("keycloak", "flowlog-bff", clientSecret,
            List.of("openid"), "/api/v1/auth/me", Duration.ofSeconds(10)),
        new AppSecurityProperties.Saml("keycloak", "https://app.example.com/saml2/sp",
            metadataUri, "/api/v1/auth/me", Duration.ofHours(1)),
        List.of(), "", Duration.ofSeconds(60), requireHttps,
        new AppSecurityProperties.Session(sessionKey));
  }

  @Test
  @DisplayName("require-https=false (local/dev) 이면 검사하지 않는다")
  void notRequired() {
    SecurityStartupValidator validator = new SecurityStartupValidator(
        properties(false, "http://kc/saml", "flowlog-bff-local-secret", null),
        "http://kc/realms/flowlog", false);

    assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("prod - http IdP URL, Secure 아닌 쿠키, local 기본 시크릿/키는 기동 실패")
  void insecureProdSettings() {
    SecurityStartupValidator validator = new SecurityStartupValidator(
        properties(true, "http://kc/saml", "flowlog-bff-local-secret",
            "Zmxvd2xvZy1sb2NhbC1zZXNzaW9uLWtleS0zMmJ5dGU="),
        "http://kc/realms/flowlog", false);

    assertThatThrownBy(validator::afterPropertiesSet)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("issuer-uri")
        .hasMessageContaining("metadata-uri")
        .hasMessageContaining("secure")
        .hasMessageContaining("client-secret")
        .hasMessageContaining("encryption-key");
  }

  @Test
  @DisplayName("prod - https + Secure 쿠키 + 운영 시크릿이면 정상 기동")
  void secureProdSettings() {
    SecurityStartupValidator validator = new SecurityStartupValidator(
        properties(true, "https://kc.example.com/saml", "prod-secret", PROD_KEY),
        "https://kc.example.com/realms/flowlog", true);

    assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
  }
}
