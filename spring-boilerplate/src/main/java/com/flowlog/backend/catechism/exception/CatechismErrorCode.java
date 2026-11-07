package com.flowlog.backend.catechism.exception;

import com.flowlog.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 교리문답 도메인 오류 코드. (할당 범위: B9001 ~ B9500)
 *
 * @author flowlog
 * @since 2026-11-08
 */
@Getter
@RequiredArgsConstructor
public enum CatechismErrorCode implements ErrorCode {

  /** 교리문답 없음. */
  CATECHISM_NOT_FOUND(HttpStatus.NOT_FOUND, "B9001"),
  /** 교리문답 코드 중복. */
  CATECHISM_CD_DUPLICATED(HttpStatus.CONFLICT, "B9002"),
  /** 단위 없음. */
  UNIT_NOT_FOUND(HttpStatus.NOT_FOUND, "B9003"),
  /** 코드값 오류. */
  CODE_INVALID(HttpStatus.BAD_REQUEST, "B9004"),
  /** 문 번호 중복. */
  QUESTION_NO_DUPLICATED(HttpStatus.CONFLICT, "B9005"),
  /** 상위 단위 오류 (다른 교리문답, 자기 자신·하위). */
  PARENT_INVALID(HttpStatus.BAD_REQUEST, "B9006"),
  /** 하위 단위가 있어 삭제 불가. */
  UNIT_HAS_CHILDREN(HttpStatus.CONFLICT, "B9007"),
  /** 주보에서 사용 중. */
  UNIT_IN_USE(HttpStatus.CONFLICT, "B9008"),
  /** 단위가 있어 교리문답 삭제 불가. */
  CATECHISM_HAS_UNITS(HttpStatus.CONFLICT, "B9009");

  private final HttpStatus status;
  private final String messageId;
}
