package com.flowlog.backend.code.domain.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 공통코드 (선택 목록용).
 *
 * @param code 코드
 * @param codeNm 코드명 (사용자 언어, 없으면 기본 언어)
 * @param sortOrder 정렬순서
 * @param attr1 확장속성1
 * @author flowlog
 * @since 2026-11-05
 */
@Schema(description = "공통코드")
public record CodeResponse(
    @Schema(description = "코드", example = "GENERAL") String code,
    @Schema(description = "코드명", example = "일반") String codeNm,
    @Schema(description = "정렬순서", example = "1") Integer sortOrder,
    @Schema(description = "확장속성1") String attr1) {
}
