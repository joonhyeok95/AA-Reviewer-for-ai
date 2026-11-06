package com.flowlog.backend.global.component;

import com.flowlog.backend.global.common.domain.service.MessageService;
import com.flowlog.backend.global.common.domain.service.dto.PageResponse;
import com.flowlog.backend.global.common.model.MessageResponse;
import com.flowlog.backend.global.constant.MessageCode;
import com.flowlog.backend.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.LocaleResolver;

/**
 * 표준 응답({@link MessageResponse}) 생성 컴포넌트.
 *
 * <p>Controller 와 ExceptionHandler 는 반드시 이 컴포넌트를 통해 응답을 생성한다.
 * 현재 요청(path, HTTP Method, Locale)은 {@link RequestContextHolder} 에서 조회하므로
 * {@code HttpServletRequest} 를 파라미터로 넘길 필요가 없다.
 * Locale 은 {@link LocaleResolver} 로 결정하므로 Security Filter 단계(401/403)에서도 다국어가 적용된다.
 *
 * <table>
 *   <caption>HTTP Method 별 기본 응답</caption>
 *   <tr><th>Method</th><th>Status</th><th>messageId</th></tr>
 *   <tr><td>GET</td><td>200</td><td>S0001 (목록은 {@link #successPage} 사용 → S0002)</td></tr>
 *   <tr><td>POST</td><td>201</td><td>S0003</td></tr>
 *   <tr><td>PUT / PATCH</td><td>200</td><td>S0004</td></tr>
 *   <tr><td>DELETE</td><td>200</td><td>S0005</td></tr>
 *   <tr><td>기타</td><td>200</td><td>S0000</td></tr>
 * </table>
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Component
@RequiredArgsConstructor
public class MessageHttpResponse {

  private static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");
  private static final Locale DEFAULT_LOCALE = Locale.KOREA;
  private static final String DEFAULT_PATH = "/";

  private final MessageService messageService;
  private final LocaleResolver localeResolver;

  /**
   * 결과 데이터 없이 성공 응답을 생성한다. (HTTP Method 별 기본 메시지)
   *
   * @return 표준 응답
   */
  public ResponseEntity<MessageResponse<Void>> success() {
    return success(null);
  }

  /**
   * 성공 응답을 생성한다. (HTTP Method 별 기본 메시지/상태코드)
   *
   * @param result 결과 데이터
   * @param <T> 결과 데이터 타입
   * @return 표준 응답
   */
  public <T> ResponseEntity<MessageResponse<T>> success(T result) {
    String method = currentRequest() == null ? "" : currentRequest().getMethod();
    return build(resolveStatus(method), resolveMessageId(method), result);
  }

  /**
   * 메시지 ID 를 직접 지정하여 성공 응답을 생성한다.
   *
   * @param result 결과 데이터
   * @param messageId 메시지 ID
   * @param args 메시지 치환 인자
   * @param <T> 결과 데이터 타입
   * @return 표준 응답
   */
  public <T> ResponseEntity<MessageResponse<T>> success(
      T result, String messageId, Object... args) {
    String method = currentRequest() == null ? "" : currentRequest().getMethod();
    return build(resolveStatus(method), messageId, result, args);
  }

  /**
   * 목록(페이징) 조회 성공 응답을 생성한다. 메시지: S0002 ("{전체건수}건 조회되었습니다.")
   *
   * @param page 페이징 결과
   * @param <T> 데이터 타입
   * @return 표준 응답
   */
  public <T> ResponseEntity<MessageResponse<PageResponse<T>>> successPage(PageResponse<T> page) {
    return build(HttpStatus.OK, MessageCode.SUCCESS_GET_LIST, page, page.totalElements());
  }

  /**
   * 오류 응답을 생성한다.
   *
   * @param errorCode 오류 코드
   * @param result 오류 상세 데이터 (없으면 null)
   * @param args 메시지 치환 인자
   * @param <T> 결과 데이터 타입
   * @return 표준 응답
   */
  public <T> ResponseEntity<MessageResponse<T>> error(
      ErrorCode errorCode, T result, Object... args) {
    return build(errorCode.getStatus(), errorCode.getMessageId(), result, args);
  }

  private <T> ResponseEntity<MessageResponse<T>> build(
      HttpStatus status, String messageId, T result, Object... args) {
    HttpServletRequest request = currentRequest();
    Locale locale = resolveLocale(request);
    String path = request == null ? DEFAULT_PATH : request.getRequestURI();

    MessageResponse<T> body = MessageResponse.<T>builder()
        .locale(locale.toString())
        .timestamp(OffsetDateTime.now(ZONE_ID))
        .httpStatus(status.value())
        .messageId(messageId)
        .messageTitle(messageService.getTitle(messageId, locale, args))
        .messageContent(messageService.getContent(messageId, locale, args))
        .path(path)
        .result(result)
        .build();

    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
  }

  private HttpServletRequest currentRequest() {
    HttpServletRequest request = null;
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
      request = attrs.getRequest();
    }
    return request;
  }

  private Locale resolveLocale(HttpServletRequest request) {
    Locale locale = DEFAULT_LOCALE;
    if (request != null) {
      locale = localeResolver.resolveLocale(request);
    }
    return locale;
  }

  private HttpStatus resolveStatus(String method) {
    HttpStatus status = HttpStatus.OK;
    if ("POST".equals(method)) {
      status = HttpStatus.CREATED;
    }
    return status;
  }

  private String resolveMessageId(String method) {
    String messageId;
    switch (method) {
      case "GET":
        messageId = MessageCode.SUCCESS_GET;
        break;
      case "POST":
        messageId = MessageCode.SUCCESS_POST;
        break;
      case "PUT":
      case "PATCH":
        messageId = MessageCode.SUCCESS_PUT;
        break;
      case "DELETE":
        messageId = MessageCode.SUCCESS_DELETE;
        break;
      default:
        messageId = MessageCode.SUCCESS_DEFAULT;
        break;
    }
    return messageId;
  }
}
