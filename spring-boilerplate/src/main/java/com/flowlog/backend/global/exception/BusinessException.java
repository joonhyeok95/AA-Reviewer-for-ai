package com.flowlog.backend.global.exception;

import java.util.Arrays;
import lombok.Getter;

/**
 * 비즈니스 예외. 도메인 정책 위반, 데이터 미존재 등 의도된 오류 상황에서 발생시킨다.
 *
 * <pre>
 * throw new BusinessException(SampleErrorCode.SAMPLE_NOT_FOUND);
 * throw new BusinessException(SampleErrorCode.SAMPLE_NAME_DUPLICATED, name);
 * </pre>
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
public class BusinessException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient ErrorCode errorCode; // 오류 코드
  private final transient Object[] args; // 메시지 치환 인자

  /**
   * 비즈니스 예외를 생성한다.
   *
   * @param errorCode 오류 코드
   * @param args 메시지 치환 인자 ({0}, {1} ...)
   */
  public BusinessException(ErrorCode errorCode, Object... args) {
    super(errorCode.getMessageId() + " " + Arrays.toString(args));
    this.errorCode = errorCode;
    this.args = args;
  }
}
