package com.flowlog.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * 브라우저 쿠키 동작 (실제 서버 포트).
 *
 * <p>MockMvc 의 {@code springSecurity()} 는 CSRF 저장소를 테스트용(요청 속성)으로 바꾸므로, 쿠키 발급은
 * 실제 HTTP 로 검증한다.
 *
 * <ul>
 *   <li>XSRF-TOKEN : 첫 요청부터 발급, 경로 / (Gateway 의 /cm 밖에서 서비스되는 화면도 읽을 수 있음)</li>
 *   <li>미인증 요청은 세션을 만들지 않음 (요청 캐시 미사용) - 익명 요청으로 Redis 세션을 늘리거나
 *       Gateway Rate Limit 세션 버킷을 새로 받을 수 없음</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-06
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrowserCookieWebTest {

  private final HttpClient client = HttpClient.newHttpClient();

  @LocalServerPort
  private int port;

  private HttpResponse<Void> get(String path, String cookie) throws IOException,
      InterruptedException {
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    if (cookie != null) {
      request.header("Cookie", cookie);
    }
    return client.send(request.build(), HttpResponse.BodyHandlers.discarding());
  }

  private static List<String> setCookies(HttpResponse<?> response) {
    return response.headers().allValues("Set-Cookie");
  }

  @Test
  @DisplayName("XSRF-TOKEN 은 첫 요청부터 경로 / 로 발급, HttpOnly 아님 (화면 스크립트가 읽어 헤더로 전송)")
  void csrfCookie_issuedOnFirstRequest() throws Exception {
    HttpResponse<Void> response = get("/v3/api-docs", null);

    String csrf = setCookies(response).stream().filter(c -> c.startsWith("XSRF-TOKEN="))
        .findFirst().orElseThrow();
    assertThat(csrf).contains("Path=/").doesNotContain("Path=/cm").doesNotContainIgnoringCase(
        "HttpOnly");

    // 이미 쿠키가 있으면 다시 발급하지 않는다
    HttpResponse<Void> again = get("/v3/api-docs", csrf.substring(0, csrf.indexOf(';')));
    assertThat(setCookies(again)).noneMatch(c -> c.startsWith("XSRF-TOKEN="));
  }

  @Test
  @DisplayName("미인증 API 요청(401)은 세션을 만들지 않는다 - SESSION 쿠키 없음")
  void anonymousRequest_noSession() throws Exception {
    HttpResponse<Void> response = get("/api/v1/langs", null);

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(setCookies(response)).noneMatch(c -> c.startsWith("SESSION="));
  }
}
