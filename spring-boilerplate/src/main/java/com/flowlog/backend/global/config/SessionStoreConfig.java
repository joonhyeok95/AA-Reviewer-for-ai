package com.flowlog.backend.global.config;

import com.flowlog.backend.global.security.AppSecurityProperties;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

/**
 * Redis 세션 저장 형식 : <b>JDK 직렬화 + 클래스 허용목록 + AES-256-GCM 암호화</b>.
 *
 * <p>세션에는 Access/Refresh Token 이 들어 있다. Redis 가 노출되거나 침해되었을 때를 대비해
 * <ul>
 *   <li><b>기밀성</b> : 값 전체를 AES-GCM 으로 암호화 → Redis 에서 토큰을 읽을 수 없다.</li>
 *   <li><b>무결성</b> : GCM 인증 태그 검증 → 키 없이 위·변조한 값은 복호화 단계에서 거부되므로
 *       역직렬화 공격(gadget chain) 입력 자체가 도달하지 않는다.</li>
 *   <li><b>역직렬화 허용목록</b> : 그래도 복원 가능한 클래스는 {@link #ALLOWED_CLASSES} 로 제한
 *       (심층 방어)</li>
 * </ul>
 * (Spring Security Jackson 직렬화는 SAML 인증 객체 복원 문제로 사용하지 않는다)
 *
 * <p>키는 {@code app.security.session.encryption-key} (Base64 32바이트, 환경변수
 * {@code SESSION_ENCRYPTION_KEY}). 키를 바꾸면 기존 세션은 복호화되지 않아 재로그인이 필요하다.
 * 여러 인스턴스는 같은 키를 사용해야 세션이 공유된다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Slf4j
@Configuration
public class SessionStoreConfig {

  /** 역직렬화 허용 클래스 (패키지 패턴, 그 외 전부 거부). */
  static final String ALLOWED_CLASSES = String.join(";",
      "maxdepth=40", "maxrefs=20000", "maxbytes=1048576", "maxarray=10000",
      "java.lang.*", "java.util.*", "java.time.*", "java.net.URL", "java.net.URI",
      "org.springframework.security.**", "org.springframework.session.**",
      "org.springframework.util.LinkedMultiValueMap",
      "org.springframework.util.LinkedCaseInsensitiveMap",
      "com.flowlog.backend.**", "!*");

  /**
   * Spring Session Redis 기본 직렬화기 (bean 이름 고정).
   *
   * @param properties 보안 설정
   * @return 암호화 직렬화기
   */
  @Bean("springSessionDefaultRedisSerializer")
  public RedisSerializer<Object> springSessionDefaultRedisSerializer(
      AppSecurityProperties properties) {
    String key = properties.session() == null ? null : properties.session().encryptionKey();
    if (StringUtils.isBlank(key)) {
      throw new IllegalStateException(
          "세션 암호화 키(app.security.session.encryption-key / SESSION_ENCRYPTION_KEY)가 없습니다.");
    }
    return new EncryptedJdkRedisSerializer(Base64.getDecoder().decode(key.trim()));
  }

  /** AES-256-GCM 암호화 + 허용목록 JDK 직렬화. */
  static final class EncryptedJdkRedisSerializer implements RedisSerializer<Object> {

    private static final byte VERSION = 1;
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();
    private final ObjectInputFilter filter =
        ObjectInputFilter.Config.createFilter(ALLOWED_CLASSES);

    EncryptedJdkRedisSerializer(byte[] key) {
      if (key.length != 32) {
        throw new IllegalArgumentException("세션 암호화 키는 32바이트(AES-256)여야 합니다.");
      }
      this.key = new SecretKeySpec(key, "AES");
    }

    @Override
    public byte[] serialize(Object value) {
      byte[] result = null;
      if (value != null) {
        try {
          byte[] iv = new byte[IV_LENGTH];
          random.nextBytes(iv);
          Cipher cipher = Cipher.getInstance(TRANSFORMATION);
          cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
          byte[] encrypted = cipher.doFinal(toBytes(value));
          result = ByteBuffer.allocate(1 + IV_LENGTH + encrypted.length)
              .put(VERSION).put(iv).put(encrypted).array();
        } catch (GeneralSecurityException | IOException e) {
          throw new SerializationException("세션 값 암호화 실패", e);
        }
      }
      return result;
    }

    @Override
    public Object deserialize(byte[] bytes) {
      Object result = null;
      if (bytes != null && bytes.length > 0) {
        if (bytes[0] != VERSION || bytes.length <= 1 + IV_LENGTH) {
          throw new SerializationException("알 수 없는 세션 값 형식");
        }
        try {
          Cipher cipher = Cipher.getInstance(TRANSFORMATION);
          cipher.init(Cipher.DECRYPT_MODE, key,
              new GCMParameterSpec(TAG_BITS, bytes, 1, IV_LENGTH));
          byte[] plain = cipher.doFinal(bytes, 1 + IV_LENGTH, bytes.length - 1 - IV_LENGTH);
          result = fromBytes(plain);
        } catch (GeneralSecurityException e) {
          // 키 불일치 또는 위·변조 → 역직렬화하지 않는다
          throw new SerializationException("세션 값 복호화/무결성 검증 실패", e);
        } catch (IOException | ClassNotFoundException e) {
          throw new SerializationException("세션 값 역직렬화 실패", e);
        }
      }
      return result;
    }

    private static byte[] toBytes(Object value) throws IOException {
      ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
      try (ObjectOutputStream stream = new ObjectOutputStream(out)) {
        stream.writeObject(value);
      }
      return out.toByteArray();
    }

    private Object fromBytes(byte[] bytes) throws IOException, ClassNotFoundException {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      try (ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(bytes)) {
        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc)
            throws IOException, ClassNotFoundException {
          try {
            return Class.forName(desc.getName(), false, loader);
          } catch (ClassNotFoundException e) {
            return super.resolveClass(desc);
          }
        }
      }) {
        stream.setObjectInputFilter(filter);
        return stream.readObject();
      } catch (InvalidClassException e) {
        log.error("[Session] 허용되지 않은 클래스 역직렬화 차단 - {}", e.getMessage());
        throw e;
      }
    }
  }
}
