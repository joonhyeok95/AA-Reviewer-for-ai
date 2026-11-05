package com.flowlog.backend.global.common.domain.service;

import java.util.Locale;

/**
 * 다국어 메시지 조회 서비스.
 *
 * <p>현재는 properties 기반({@link PropertiesMessageService}) 구현을 사용한다.
 * 추후 DB/Redis 기반 메시지 관리가 필요하면 구현체만 추가/교체한다.
 * (구현 교체 가능성이 있으므로 Interface + Impl 구조)
 *
 * @author flowlog
 * @since 2026-11-05
 */
public interface MessageService {

  /**
   * 메시지 제목을 조회한다.
   *
   * @param messageId 메시지 ID
   * @param locale 로케일
   * @param args 치환 인자 ({0}, {1} ...)
   * @return 메시지 제목 (미정의 시 메시지 ID)
   */
  String getTitle(String messageId, Locale locale, Object... args);

  /**
   * 메시지 내용을 조회한다.
   *
   * @param messageId 메시지 ID
   * @param locale 로케일
   * @param args 치환 인자 ({0}, {1} ...)
   * @return 메시지 내용 (미정의 시 메시지 ID)
   */
  String getContent(String messageId, Locale locale, Object... args);
}
