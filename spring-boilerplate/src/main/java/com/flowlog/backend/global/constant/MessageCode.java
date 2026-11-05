package com.flowlog.backend.global.constant;

/**
 * 표준 응답 메시지 ID 상수.
 *
 * <p>메시지 본문은 {@code resources/messages/messages_*.properties} 에
 * {@code {messageId}.title}, {@code {messageId}.content} 키로 정의한다.
 *
 * <ul>
 *   <li>S : 성공</li>
 *   <li>C : 공통 요청 오류 (클라이언트)</li>
 *   <li>B : 비즈니스 오류</li>
 *   <li>E : 시스템 오류</li>
 * </ul>
 *
 * <p>오류 메시지 ID 는 {@link com.flowlog.backend.global.exception.ErrorCode} 에서 관리한다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
public final class MessageCode {

  /** 기본 성공 (OPTIONS, HEAD 등). */
  public static final String SUCCESS_DEFAULT = "S0000";

  /** 단건 조회 성공 (GET). */
  public static final String SUCCESS_GET = "S0001";

  /** 목록 조회 성공 (GET 목록). 인자 {0}: 조회 건수. */
  public static final String SUCCESS_GET_LIST = "S0002";

  /** 등록 성공 (POST). */
  public static final String SUCCESS_POST = "S0003";

  /** 수정 성공 (PUT, PATCH). */
  public static final String SUCCESS_PUT = "S0004";

  /** 삭제 성공 (DELETE). */
  public static final String SUCCESS_DELETE = "S0005";

  /** 메시지 제목 Key 접미사. */
  public static final String TITLE_SUFFIX = ".title";

  /** 메시지 내용 Key 접미사. */
  public static final String CONTENT_SUFFIX = ".content";

  private MessageCode() {
    throw new UnsupportedOperationException("상수 클래스는 인스턴스화할 수 없습니다.");
  }
}
