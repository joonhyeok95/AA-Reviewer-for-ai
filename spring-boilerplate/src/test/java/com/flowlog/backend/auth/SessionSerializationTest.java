package com.flowlog.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowlog.backend.global.config.SessionStoreConfig;
import com.flowlog.backend.global.security.AppSecurityProperties;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.saml2.provider.service.authentication.Saml2AssertionAuthentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2ResponseAssertion;

/**
 * Redis 세션 저장 형식 검증. (JDK 직렬화 + 클래스 허용목록 + AES-256-GCM 암호화)
 *
 * <p>세션에 들어가는 인증 객체(OAuth2 로그인 SecurityContext, 토큰(OAuth2AuthorizedClient),
 * SAML 인증)가 암호화 저장 후 복원되는지, 그리고 위·변조/다른 키/허용되지 않은 클래스가 거부되는지 확인한다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
class SessionSerializationTest {

  private static final String KEY =
      Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

  private final RedisSerializer<Object> serializer = serializer(KEY);

  private static RedisSerializer<Object> serializer(String key) {
    return new SessionStoreConfig().springSessionDefaultRedisSerializer(new AppSecurityProperties(
        null, null, List.of(), "", Duration.ofSeconds(60), false,
        new AppSecurityProperties.Session(key)));
  }

  @Test
  @DisplayName("OAuth2 BFF - SecurityContext + 토큰(Access/Refresh) 직렬화 후 복원")
  void oauth2SessionRoundTrip() {
    OidcIdToken idToken = OidcIdToken.withTokenValue("id")
        .subject("s").claim("preferred_username", "tx.viewer")
        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
        new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken,
            "preferred_username"), List.of(), "keycloak");
    ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
        .clientId("flowlog-bff").clientSecret("secret")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
        .authorizationUri("http://kc/auth").tokenUri("http://kc/token").build();
    OAuth2AuthorizedClient client = new OAuth2AuthorizedClient(registration, "tx.viewer",
        new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access",
            Instant.now(), Instant.now().plusSeconds(60), Set.of("sample.list")),
        new OAuth2RefreshToken("refresh", Instant.now()));

    SecurityContextImpl restoredContext = (SecurityContextImpl) serializer.deserialize(
        serializer.serialize(new SecurityContextImpl(authentication)));
    OAuth2AuthorizedClient restoredClient =
        (OAuth2AuthorizedClient) serializer.deserialize(serializer.serialize(client));

    assertThat(restoredContext.getAuthentication().getName()).isEqualTo("tx.viewer");
    assertThat(restoredClient.getAccessToken().getScopes()).containsExactly("sample.list");
    assertThat(restoredClient.getRefreshToken().getTokenValue()).isEqualTo("refresh");
  }

  @Test
  @DisplayName("SAML2 - 실제 로그인 결과 타입(Saml2AssertionAuthentication) 직렬화 후 복원")
  void samlSessionRoundTrip() {
    Saml2ResponseAssertion assertion = Saml2ResponseAssertion
        .withResponseValue("<saml2p:Response/>")
        .nameId("tx.manager")
        .attributes(Map.of("Role", List.of("sample.add")))
        .build();
    // 실제 로그인(ResponseAuthenticationConverter)과 동일하게 principal = NameID
    Saml2AssertionAuthentication authentication = new Saml2AssertionAuthentication(
        "tx.manager", assertion, List.of(new SimpleGrantedAuthority("SCOPE_sample.add")),
        "keycloak");

    SecurityContextImpl restored = (SecurityContextImpl) serializer.deserialize(
        serializer.serialize(new SecurityContextImpl(authentication)));

    assertThat(restored.getAuthentication().getName()).isEqualTo("tx.manager");
    assertThat(restored.getAuthentication().getAuthorities())
        .extracting(a -> a.getAuthority()).containsExactly("SCOPE_sample.add");
  }

  @Test
  @DisplayName("Redis 에 저장되는 값은 암호문 - 토큰 원문이 보이지 않는다")
  void storedValueIsEncrypted() {
    byte[] stored = serializer.serialize(Map.of("token", "plain-access-token-value"));

    assertThat(new String(stored, StandardCharsets.ISO_8859_1))
        .doesNotContain("plain-access-token-value");
    assertThat(serializer.deserialize(stored)).isEqualTo(Map.of("token", "plain-access-token-value"));
  }

  @Test
  @DisplayName("위·변조된 값은 복호화(무결성 검증) 단계에서 거부 - 역직렬화하지 않음")
  void tamperedValueRejected() {
    byte[] stored = serializer.serialize("value");
    stored[stored.length - 1] ^= 0x01;

    assertThatThrownBy(() -> serializer.deserialize(stored))
        .isInstanceOf(SerializationException.class)
        .hasMessageContaining("무결성");
  }

  @Test
  @DisplayName("다른 키로 암호화된 값은 거부 (키가 다른 인스턴스/공격자가 넣은 값)")
  void otherKeyRejected() {
    String otherKey =
        Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes());
    byte[] stored = serializer(otherKey).serialize("value");

    assertThatThrownBy(() -> serializer.deserialize(stored))
        .isInstanceOf(SerializationException.class);
  }

  @Test
  @DisplayName("허용목록 밖의 클래스는 역직렬화 차단 (예: java.io.File)")
  void disallowedClassRejected() {
    byte[] stored = serializer.serialize(new File("x"));

    assertThatThrownBy(() -> serializer.deserialize(stored))
        .isInstanceOf(SerializationException.class)
        .hasMessageContaining("역직렬화");
  }

  @Test
  @DisplayName("암호화 키가 없거나 32바이트가 아니면 기동 실패")
  void invalidKey() {
    assertThatThrownBy(() -> serializer(""))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> serializer(Base64.getEncoder().encodeToString("short".getBytes())))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
