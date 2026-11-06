package com.flowlog.gateway.exception;

import java.time.OffsetDateTime;

/**
 * Gateway 오류 응답. spring-boilerplate 의 표준 응답(MessageResponse)과 동일한 형식이다.
 *
 * @param locale 응답 로케일
 * @param timestamp 응답 시각
 * @param httpStatus HTTP 상태 코드
 * @param messageId 메시지 ID
 * @param messageTitle 메시지 제목
 * @param messageContent 메시지 내용
 * @param path 요청 경로 (Gateway 기준, 예: /cm/api/v1/...)
 * @param result 결과 데이터 (오류는 null)
 * @author flowlog
 * @since 2026-11-05
 */
public record GatewayResponse(
    String locale,
    OffsetDateTime timestamp,
    int httpStatus,
    String messageId,
    String messageTitle,
    String messageContent,
    String path,
    Object result) {
}
