package com.flowlog.gateway;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * 원본 요청 경로를 그대로 보내는 최소 HTTP/1.1 클라이언트. (테스트 전용)
 *
 * <p>WebTestClient/URI 는 경로를 인코딩·검증하므로 {@code /./}, {@code %2e}, 역슬래시 같은
 * 우회 형식을 그대로 보낼 수 없다. 소켓으로 직접 요청 라인을 쓴다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
public final class RawHttp {

  private RawHttp() {
  }

  /**
   * 응답.
   *
   * @param status 상태 코드
   * @param body 응답 전체(헤더 포함)
   */
  public record Response(int status, String body) {
  }

  /**
   * 요청 경로를 가공 없이 전송한다.
   *
   * @param port 포트
   * @param method 메서드
   * @param rawPath 요청 라인의 경로 (그대로 전송)
   * @return 응답
   */
  public static Response send(int port, String method, String rawPath) {
    try (Socket socket = new Socket("localhost", port)) {
      socket.setSoTimeout(10_000);
      OutputStream out = socket.getOutputStream();
      String request = method + " " + rawPath + " HTTP/1.1\r\n"
          + "Host: localhost:" + port + "\r\n"
          + "Content-Length: 0\r\n"
          + "Connection: close\r\n\r\n";
      out.write(request.getBytes(StandardCharsets.ISO_8859_1));
      out.flush();
      InputStream in = socket.getInputStream();
      String response = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      int status = Integer.parseInt(response.substring(9, 12));
      return new Response(status, response);
    } catch (IOException e) {
      throw new IllegalStateException("요청 실패: " + rawPath, e);
    }
  }
}
