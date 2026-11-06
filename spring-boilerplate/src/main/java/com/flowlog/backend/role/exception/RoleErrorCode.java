package com.flowlog.backend.role.exception;

import com.flowlog.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 권한 관리 오류 코드. (B6001 ~ B6999)
 *
 * @author flowlog
 * @since 2026-11-06
 */
@Getter
@RequiredArgsConstructor
public enum RoleErrorCode implements ErrorCode {

  /** 권한 없음. */
  ROLE_NOT_FOUND(HttpStatus.NOT_FOUND, "B6001"),
  /** 권한코드 중복. */
  ROLE_DUPLICATED(HttpStatus.CONFLICT, "B6002"),
  /** 기본 권한 보호 (삭제/유형·레벨 변경 불가). */
  SYSTEM_ROLE_PROTECTED(HttpStatus.CONFLICT, "B6003"),
  /** 시스템관리자 권한은 시스템관리자만 변경. */
  SYS_ADMIN_ONLY(HttpStatus.FORBIDDEN, "B6004"),
  /** 부여 내역이 있어 삭제 불가. */
  ROLE_IN_USE(HttpStatus.CONFLICT, "B6005"),
  /** 공통코드 값 오류. */
  INVALID_CODE(HttpStatus.BAD_REQUEST, "B6006"),
  /** 부여 대상(사용자/부서) 없음. */
  GRANT_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "B6007"),
  /** 부여 내역 없음. */
  GRANT_NOT_FOUND(HttpStatus.NOT_FOUND, "B6008"),
  /** 부여 기간 오류. */
  GRANT_PERIOD_INVALID(HttpStatus.BAD_REQUEST, "B6009"),
  /** 같은 대상의 기간 중복. */
  GRANT_OVERLAP(HttpStatus.CONFLICT, "B6010");

  private final HttpStatus status;
  private final String messageId;
}
