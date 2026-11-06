package com.flowlog.backend.sample.domain.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 샘플 목록 검색 조건. (Query String 바인딩)
 *
 * @param name 샘플명 (부분 일치)
 * @param useYn 사용여부
 * @author flowlog
 * @since 2026-11-05
 */
@Schema(description = "샘플 검색 조건")
public record SampleSearchRequest(
    @Schema(description = "샘플명(부분 일치)", example = "샘플")
    @Size(max = 100)
    String name,

    @Schema(description = "사용여부(Y/N)", example = "Y")
    @Pattern(regexp = "^[YN]$")
    String useYn) {
}
