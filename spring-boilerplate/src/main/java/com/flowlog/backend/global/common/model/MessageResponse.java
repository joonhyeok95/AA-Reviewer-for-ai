package com.flowlog.backend.global.common.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import lombok.Builder;

/**
 * 표준 응답 객체. 성공/실패 모든 API 응답은 이 형식으로 반환한다.
 *
 * <pre>
 * {
 *   "locale": "ko_KR",
 *   "timestamp": "2026-11-05T10:31:44.830+09:00",
 *   "httpStatus": 200,
 *   "messageId": "S0001",
 *   "messageTitle": "조회 완료",
 *   "messageContent": "조회되었습니다.",
 *   "path": "/api/v1/samples/1",
 *   "result": { ... }
 * }
 * </pre>
 *
 * @param locale 응답 로케일 (예: ko_KR)
 * @param timestamp 응답 시각 (ISO-8601, Asia/Seoul)
 * @param httpStatus HTTP 상태 코드
 * @param messageId 메시지 ID
 * @param messageTitle 다국어 처리된 메시지 제목
 * @param messageContent 다국어 처리된 메시지 내용
 * @param path 요청 URI
 * @param result 결과 데이터 (없으면 null)
 * @param <T> 결과 데이터 타입
 * @author flowlog
 * @since 2026-11-05
 */
@Builder
@Schema(description = "표준 응답")
public record MessageResponse<T>(
    @Schema(description = "로케일", example = "ko_KR") String locale,
    @Schema(description = "응답 시각") OffsetDateTime timestamp,
    @Schema(description = "HTTP 상태 코드", example = "200") int httpStatus,
    @Schema(description = "메시지 ID", example = "S0001") String messageId,
    @Schema(description = "메시지 제목", example = "조회 완료") String messageTitle,
    @Schema(description = "메시지 내용", example = "조회되었습니다.") String messageContent,
    @Schema(description = "요청 경로", example = "/api/v1/samples/1") String path,
    @Schema(description = "결과 데이터") T result) {
}
