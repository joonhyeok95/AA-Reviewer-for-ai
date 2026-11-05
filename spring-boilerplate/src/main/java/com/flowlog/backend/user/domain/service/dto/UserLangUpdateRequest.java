package com.flowlog.backend.user.domain.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 사용자 설정 언어 변경 요청.
 *
 * @param langCd 언어코드 (공통코드 LANG)
 * @author flowlog
 * @since 2026-11-05
 */
@Schema(description = "사용자 설정 언어 변경 요청")
public record UserLangUpdateRequest(
    @Schema(description = "언어코드", example = "en") @NotBlank @Size(max = 10) String langCd) {
}
