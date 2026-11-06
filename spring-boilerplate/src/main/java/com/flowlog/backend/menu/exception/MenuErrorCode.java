package com.flowlog.backend.menu.exception;

import com.flowlog.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 메뉴 관리 오류 코드. (B5001 ~ B5999)
 *
 * @author flowlog
 * @since 2026-11-06
 */
@Getter
@RequiredArgsConstructor
public enum MenuErrorCode implements ErrorCode {

  /** 메뉴 없음. */
  MENU_NOT_FOUND(HttpStatus.NOT_FOUND, "B5001"),
  /** 하위 메뉴가 있어 삭제 불가. */
  MENU_HAS_CHILDREN(HttpStatus.CONFLICT, "B5002"),
  /** 상위 메뉴 지정 오류 (자기 자신/하위 메뉴). */
  INVALID_PARENT(HttpStatus.BAD_REQUEST, "B5003"),
  /** 메뉴 유형과 맞지 않는 URL. */
  INVALID_URL(HttpStatus.BAD_REQUEST, "B5004"),
  /** 공통코드 값 오류. */
  INVALID_CODE(HttpStatus.BAD_REQUEST, "B5005"),
  /** 순서 변경 대상 오류 (다른 상위 메뉴). */
  INVALID_ORDER(HttpStatus.BAD_REQUEST, "B5006"),
  /** 본문 메뉴 없음 또는 권한 없음. */
  CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "B5007");

  private final HttpStatus status;
  private final String messageId;
}
