package com.flowlog.backend.notice.exception;

import com.flowlog.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 공지사항 오류 코드. (B2001 ~ B2999)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@RequiredArgsConstructor
public enum NoticeErrorCode implements ErrorCode {

  /** 공지 없음 (삭제/게시 전 포함). */
  NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "B2001"),
  /** 최소 1개 언어의 제목과 본문 필수 (NT-61). */
  NOTICE_CONTENT_REQUIRED(HttpStatus.BAD_REQUEST, "B2002"),
  /** 언어 버전의 제목/본문 중 하나만 입력. */
  NOTICE_LANG_INCOMPLETE(HttpStatus.BAD_REQUEST, "B2003"),
  /** 사용하지 않는 언어. */
  NOTICE_LANG_INVALID(HttpStatus.BAD_REQUEST, "B2004"),
  /** 같은 언어 중복 입력. */
  NOTICE_LANG_DUPLICATED(HttpStatus.BAD_REQUEST, "B2005"),
  /** 대표 언어가 입력한 언어가 아님. */
  NOTICE_BASE_LANG_INVALID(HttpStatus.BAD_REQUEST, "B2006"),
  /** 공통코드 값 오류 (분류/태그/메일 발송 유형). */
  NOTICE_CODE_INVALID(HttpStatus.BAD_REQUEST, "B2007"),
  /** 팝업 기간 오류. */
  NOTICE_POPUP_PERIOD_INVALID(HttpStatus.BAD_REQUEST, "B2008"),
  /** 대상 지정 발송인데 메일 대상 없음. */
  NOTICE_MAIL_TARGET_REQUIRED(HttpStatus.BAD_REQUEST, "B2009"),
  /** 인사 마스터에 없는 사업장/부서. */
  NOTICE_MAIL_TARGET_INVALID(HttpStatus.BAD_REQUEST, "B2010"),
  /** 첨부파일 없음. */
  NOTICE_FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "B2011"),
  /** 허용하지 않는 확장자. */
  NOTICE_FILE_EXT_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "B2012"),
  /** 첨부 개수 초과. */
  NOTICE_FILE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "B2013"),
  /** 빈 파일. */
  NOTICE_FILE_EMPTY(HttpStatus.BAD_REQUEST, "B2014"),
  /** 파일 저장/조회 실패. */
  NOTICE_FILE_IO_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "B2015");

  private final HttpStatus status;
  private final String messageId;
}
