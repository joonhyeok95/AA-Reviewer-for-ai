package com.flowlog.backend.bulletin.exception;

import com.flowlog.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 주보 도메인 오류 코드. (할당 범위: B8001 ~ B8999)
 *
 * @author flowlog
 * @since 2026-11-08
 */
@Getter
@RequiredArgsConstructor
public enum BulletinErrorCode implements ErrorCode {

  /** 주보 없음 (작성 중 주보를 권한 없이 조회한 경우 포함). */
  BULLETIN_NOT_FOUND(HttpStatus.NOT_FOUND, "B8001"),
  /** 예배일 중복. */
  WORSHIP_DATE_DUPLICATED(HttpStatus.CONFLICT, "B8002"),
  /** 상태 코드 오류. */
  STATUS_INVALID(HttpStatus.BAD_REQUEST, "B8003"),
  /** 교리문답 단위 없음. */
  CATECHISM_UNIT_INVALID(HttpStatus.BAD_REQUEST, "B8004"),
  /** 전례문 없음. */
  LITURGY_TEXT_INVALID(HttpStatus.BAD_REQUEST, "B8005"),
  /** 교회소식 단계 초과 또는 빈 항목. */
  NEWS_INVALID(HttpStatus.BAD_REQUEST, "B8006"),
  /** 섬김위원 같은 역할·날짜 중복. */
  DUTY_DUPLICATED(HttpStatus.BAD_REQUEST, "B8007"),
  /** 게시된 주보 없음 (이번 주 주보). */
  CURRENT_NOT_FOUND(HttpStatus.NOT_FOUND, "B8008"),
  /** 대표사진 형식 오류 (jpg, png, gif, webp 만, 내용으로 판별). */
  PHOTO_INVALID(HttpStatus.BAD_REQUEST, "B8009"),
  /** 대표사진 크기 초과. */
  PHOTO_TOO_LARGE(HttpStatus.BAD_REQUEST, "B8010"),
  /** 대표사진 없음. */
  PHOTO_NOT_FOUND(HttpStatus.NOT_FOUND, "B8011"),
  /** 대표사진 저장·읽기 실패. */
  PHOTO_IO_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "B8012");

  private final HttpStatus status;
  private final String messageId;
}
