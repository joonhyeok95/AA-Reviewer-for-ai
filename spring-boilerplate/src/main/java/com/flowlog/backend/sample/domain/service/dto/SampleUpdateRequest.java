package com.flowlog.backend.sample.domain.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 샘플 수정 요청.
 *
 * @param name 샘플명
 * @param description 설명
 * @param useYn 사용여부
 * @author flowlog
 * @since 2026-11-05
 */
@Schema(description = "샘플 수정 요청")
public record SampleUpdateRequest(
    @Schema(description = "샘플명", example = "수정 샘플", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(max = 100)
    String name,

    @Schema(description = "설명", example = "수정된 설명")
    @Size(max = 500)
    String description,

    @Schema(description = "사용여부(Y/N)", example = "N", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Pattern(regexp = "^[YN]$")
    String useYn) {
}
