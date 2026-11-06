package com.flowlog.backend.global.common.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 입력값 검증 실패 상세. 검증 오류 응답의 {@code result} 에 목록으로 담긴다.
 *
 * @param field 오류 필드명
 * @param rejectedValue 입력된 값
 * @param reason 오류 사유 (다국어 처리됨)
 * @author flowlog
 * @since 2026-11-05
 */
@Schema(description = "입력값 검증 오류 상세")
public record FieldErrorDetail(
    @Schema(description = "필드명", example = "name") String field,
    @Schema(description = "입력값", example = "") Object rejectedValue,
    @Schema(description = "사유", example = "공백일 수 없습니다") String reason) {
}
