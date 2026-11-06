package com.flowlog.gateway.exception;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.context.MessageSource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * Gateway 표준 응답(spring-boilerplate 의 MessageResponse 와 동일 형식) 생성/출력.
 *
 * <p>오류 핸들러, Rate Limit 필터, 서킷브레이커 fallback 이 공통으로 사용한다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Component
@RequiredArgsConstructor
public class GatewayResponseWriter {

  private static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");
  private static final List<Locale> SUPPORTED_LOCALES = List.of(Locale.KOREA, Locale.US);

  private final MessageSource messageSource;
  private final JsonMapper jsonMapper;

  /**
   * 표준 응답 본문을 만든다.
   *
   * @param exchange 현재 요청
   * @param errorCode 오류 코드
   * @return 표준 응답
   */
  public GatewayResponse body(ServerWebExchange exchange, GatewayErrorCode errorCode) {
    Locale locale = resolveLocale(exchange);
    String messageId = errorCode.getMessageId();
    return new GatewayResponse(
        locale.toString(),
        OffsetDateTime.now(ZONE_ID),
        errorCode.getStatus().value(),
        messageId,
        messageSource.getMessage(messageId + ".title", null, messageId, locale),
        messageSource.getMessage(messageId + ".content", null, messageId, locale),
        originalPath(exchange),
        null);
  }

  /**
   * 표준 응답을 응답 스트림에 쓴다. (필터/예외 핸들러용)
   *
   * @param exchange 현재 요청
   * @param errorCode 오류 코드
   * @return 쓰기 완료 신호
   */
  public Mono<Void> write(ServerWebExchange exchange, GatewayErrorCode errorCode) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(errorCode.getStatus());
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    DataBuffer buffer = response.bufferFactory()
        .wrap(jsonMapper.writeValueAsBytes(body(exchange, errorCode)));
    return response.writeWith(Mono.just(buffer));
  }

  /** fallback 으로 forward 된 경우에도 클라이언트가 호출한 원래 경로를 반환한다. */
  private String originalPath(ServerWebExchange exchange) {
    Set<URI> originals =
        exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
    String path = exchange.getRequest().getURI().getRawPath();
    if (originals != null && !originals.isEmpty()) {
      path = originals.iterator().next().getRawPath();
    }
    return path;
  }

  private Locale resolveLocale(ServerWebExchange exchange) {
    List<Locale.LanguageRange> ranges = exchange.getRequest().getHeaders().getAcceptLanguage();
    Locale matched = Locale.lookup(ranges, SUPPORTED_LOCALES);
    Locale locale = Locale.KOREA;
    if (matched != null) {
      locale = matched;
    } else if (!ranges.isEmpty()) {
      locale = SUPPORTED_LOCALES.stream()
          .filter(l -> ranges.get(0).getRange().toLowerCase(Locale.ROOT)
              .startsWith(l.getLanguage()))
          .findFirst()
          .orElse(Locale.KOREA);
    }
    return locale;
  }
}
