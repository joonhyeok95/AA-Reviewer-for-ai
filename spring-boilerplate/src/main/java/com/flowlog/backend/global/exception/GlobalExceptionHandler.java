package com.flowlog.backend.global.exception;

import com.flowlog.backend.global.common.model.FieldErrorDetail;
import com.flowlog.backend.global.common.model.MessageResponse;
import com.flowlog.backend.global.component.MessageHttpResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 전역 예외 처리기. 모든 오류는 표준 응답({@link MessageResponse}) 형식으로 반환한다.
 *
 * <p>업무 도메인 전용 처리가 필요하면 별도 {@code @RestControllerAdvice} 를 만들고
 * 이 클래스보다 높은 우선순위({@code @Order})를 지정한다.
 *
 * <p>로그 정책: 4xx 는 WARN (스택트레이스 없음), 5xx 는 ERROR (스택트레이스 포함)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

  private final MessageHttpResponse messageHttpResponse;

  /** 비즈니스 예외. */
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<MessageResponse<Void>> handleBusinessException(BusinessException e) {
    log.warn("[BusinessException] {}", e.getMessage());
    return messageHttpResponse.error(e.getErrorCode(), null, e.getArgs());
  }

  /** {@code @RequestBody @Valid} 검증 실패, {@code @ModelAttribute} 바인딩/검증 실패. */
  @ExceptionHandler(BindException.class)
  public ResponseEntity<MessageResponse<List<FieldErrorDetail>>> handleBindException(
      BindException e) {
    List<FieldErrorDetail> details = e.getBindingResult().getFieldErrors().stream()
        .map(this::toDetail)
        .toList();
    log.warn("[BindException] {}", details);
    return messageHttpResponse.error(CommonErrorCode.INVALID_INPUT, details);
  }

  /** {@code @PathVariable}, {@code @RequestParam} 등 메서드 파라미터 제약조건 위반. */
  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<MessageResponse<List<FieldErrorDetail>>> handleMethodValidation(
      HandlerMethodValidationException e) {
    List<FieldErrorDetail> details = e.getParameterValidationResults().stream()
        .flatMap(result -> result.getResolvableErrors().stream()
            .map(error -> new FieldErrorDetail(
                result.getMethodParameter().getParameterName(),
                result.getArgument(),
                messageOf(error))))
        .toList();
    log.warn("[HandlerMethodValidationException] {}", details);
    return messageHttpResponse.error(CommonErrorCode.INVALID_INPUT, details);
  }

  /** Service 계층 {@code @Validated} 메서드 검증 실패. */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<MessageResponse<List<FieldErrorDetail>>> handleConstraintViolation(
      ConstraintViolationException e) {
    List<FieldErrorDetail> details = e.getConstraintViolations().stream()
        .map(v -> new FieldErrorDetail(
            v.getPropertyPath().toString(), v.getInvalidValue(), v.getMessage()))
        .toList();
    log.warn("[ConstraintViolationException] {}", details);
    return messageHttpResponse.error(CommonErrorCode.INVALID_INPUT, details);
  }

  /** 파라미터 타입 불일치 (예: 숫자 자리에 문자). */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<MessageResponse<Void>> handleTypeMismatch(
      MethodArgumentTypeMismatchException e) {
    log.warn("[TypeMismatch] {}={}", e.getName(), e.getValue());
    return messageHttpResponse.error(CommonErrorCode.INVALID_TYPE, null, e.getName());
  }

  /** 필수 요청 파라미터 누락. */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<MessageResponse<Void>> handleMissingParameter(
      MissingServletRequestParameterException e) {
    log.warn("[MissingParameter] {}", e.getParameterName());
    return messageHttpResponse.error(
        CommonErrorCode.MISSING_PARAMETER, null, e.getParameterName());
  }

  /** 요청 본문 파싱 실패 (JSON 형식 오류 등). */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<MessageResponse<Void>> handleNotReadable(
      HttpMessageNotReadableException e) {
    log.warn("[NotReadable] {}", e.getMostSpecificCause().getMessage());
    return messageHttpResponse.error(CommonErrorCode.UNREADABLE_BODY, null);
  }

  /** 지원하지 않는 HTTP Method. */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<MessageResponse<Void>> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException e) {
    log.warn("[MethodNotSupported] {}", e.getMethod());
    return messageHttpResponse.error(CommonErrorCode.METHOD_NOT_ALLOWED, null);
  }

  /** 지원하지 않는 Content-Type. */
  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<MessageResponse<Void>> handleMediaTypeNotSupported(
      HttpMediaTypeNotSupportedException e) {
    log.warn("[MediaTypeNotSupported] {}", e.getContentType());
    return messageHttpResponse.error(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE, null);
  }

  /** 존재하지 않는 경로. */
  @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
  public ResponseEntity<MessageResponse<Void>> handleNotFound(Exception e) {
    log.warn("[NotFound] {}", e.getMessage());
    return messageHttpResponse.error(CommonErrorCode.PATH_NOT_FOUND, null);
  }

  /** Entity 불변식 위반 등 잘못된 인자 (예: 권한 시작일 &gt; 종료일). */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<MessageResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
    log.warn("[IllegalArgument] {}", e.getMessage());
    return messageHttpResponse.error(CommonErrorCode.BAD_REQUEST, null);
  }

  /** DB 제약조건 위반 (Unique, FK, Not Null 등). */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<MessageResponse<Void>> handleDataIntegrity(
      DataIntegrityViolationException e) {
    log.warn("[DataIntegrityViolation] {}", e.getMostSpecificCause().getMessage());
    return messageHttpResponse.error(CommonErrorCode.DATA_INTEGRITY_VIOLATION, null);
  }

  /** 그 외 모든 예외. 내부 정보는 응답에 노출하지 않는다. */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<MessageResponse<Void>> handleException(Exception e) {
    log.error("[UnhandledException] {}", e.getMessage(), e);
    return messageHttpResponse.error(CommonErrorCode.INTERNAL_SERVER_ERROR, null);
  }

  private FieldErrorDetail toDetail(FieldError error) {
    return new FieldErrorDetail(error.getField(), error.getRejectedValue(), messageOf(error));
  }

  private String messageOf(MessageSourceResolvable error) {
    return error.getDefaultMessage();
  }
}
