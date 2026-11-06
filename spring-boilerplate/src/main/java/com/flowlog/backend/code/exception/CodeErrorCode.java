package com.flowlog.backend.code.exception;

import com.flowlog.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 공통코드 관리 오류 코드. (B4001 ~ B4999)
 *
 * @author flowlog
 * @since 2026-11-06
 */
@Getter
@RequiredArgsConstructor
public enum CodeErrorCode implements ErrorCode {

  /** 지원하지 않는 언어. */
  INVALID_LANG(HttpStatus.BAD_REQUEST, "B4001"),
  /** 기본 언어 이름 누락. */
  DEFAULT_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "B4002"),
  /** 그룹 없음. */
  GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "B4003"),
  /** 그룹코드 중복. */
  GROUP_DUPLICATED(HttpStatus.CONFLICT, "B4004"),
  /** 코드 없음. */
  CODE_NOT_FOUND(HttpStatus.NOT_FOUND, "B4005"),
  /** 코드 중복. */
  CODE_DUPLICATED(HttpStatus.CONFLICT, "B4006"),
  /** 시스템 그룹 삭제 불가. */
  SYSTEM_GROUP_PROTECTED(HttpStatus.CONFLICT, "B4007"),
  /** 시스템 그룹의 코드 삭제 불가. */
  SYSTEM_CODE_PROTECTED(HttpStatus.CONFLICT, "B4008"),
  /** 사용 중인 언어 최소 1개 필요. */
  LAST_LANG_REQUIRED(HttpStatus.CONFLICT, "B4009");

  private final HttpStatus status;
  private final String messageId;
}
