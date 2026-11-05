package com.flowlog.gateway.security;

import com.flowlog.gateway.exception.GatewayErrorCode;
import com.flowlog.gateway.exception.GatewayResponseWriter;
import java.util.List;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

/**
 * 요청 경로 보안 필터. (라우팅 전에 실행되는 WebFilter)
 *
 * <ol>
 *   <li><b>우회 형식 차단 (400 G0006)</b> : 원본 경로(raw path)에 {@code ;}, {@code //},
 *       {@code /./}, {@code /../}, 역슬래시, 인코딩된 {@code . / \ ; %}({@code %2e %2f %5c %3b %25})
 *       가 있으면 거부한다. Gateway 는 경로를 정규화하지 않고 넘기지만 백엔드(Tomcat)는 정규화·
 *       디코딩하므로, 그 차이를 이용하면 아래 차단 목록을 우회할 수 있기 때문이다.
 *       (예: {@code /cm/./h2-console}, {@code /cm/h2-console;x}, {@code /cm/%2e/h2-console})</li>
 *   <li><b>차단 경로 (404 G0001)</b> : {@code app.gateway.blocked-paths} 와 일치하면(대소문자 무시)
 *       백엔드를 호출하지 않고 "경로 없음" 으로 응답한다. (존재 여부도 노출하지 않음)</li>
 * </ol>
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Slf4j
@Component
@EnableConfigurationProperties(PathSecurityProperties.class)
public class PathSecurityWebFilter implements WebFilter, Ordered {

  /** 경로 우회에 쓰이는 형식. (대소문자 무시) */
  private static final Pattern SUSPICIOUS_PATH = Pattern.compile(
      ";|//|/\\./|/\\.\\./|/\\.{1,2}$|\\\\|%2e|%2f|%5c|%3b|%25", Pattern.CASE_INSENSITIVE);

  private final List<PathPattern> blockedPatterns;
  private final GatewayResponseWriter responseWriter;

  /**
   * 생성자.
   *
   * @param properties 차단 경로 설정
   * @param responseWriter 표준 응답 출력
   */
  public PathSecurityWebFilter(
      PathSecurityProperties properties, GatewayResponseWriter responseWriter) {
    PathPatternParser parser = new PathPatternParser();
    parser.setCaseSensitive(false);
    this.blockedPatterns = properties.blockedPaths().stream().map(parser::parse).toList();
    this.responseWriter = responseWriter;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String rawPath = exchange.getRequest().getURI().getRawPath();
    Mono<Void> result;
    if (rawPath == null || SUSPICIOUS_PATH.matcher(rawPath).find()) {
      log.warn("[PathSecurity] 우회 형식 경로 거부 path={}", sanitize(rawPath));
      result = responseWriter.write(exchange, GatewayErrorCode.INVALID_PATH);
    } else if (isBlocked(rawPath)) {
      log.warn("[PathSecurity] 차단 경로 요청 path={}", sanitize(rawPath));
      result = responseWriter.write(exchange, GatewayErrorCode.NO_ROUTE);
    } else {
      result = chain.filter(exchange);
    }
    return result;
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  private boolean isBlocked(String rawPath) {
    PathContainer path = PathContainer.parsePath(rawPath);
    return blockedPatterns.stream().anyMatch(pattern -> pattern.matches(path));
  }

  /** 로그 인젝션 방지 (개행 등 제어문자 제거, 길이 제한). */
  private static String sanitize(String value) {
    String safe = value == null ? "" : value.replaceAll("[\\p{Cntrl}]", "_");
    return safe.length() > 200 ? safe.substring(0, 200) : safe;
  }
}
