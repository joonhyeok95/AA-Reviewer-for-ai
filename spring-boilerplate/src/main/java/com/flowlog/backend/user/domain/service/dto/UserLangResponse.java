package com.flowlog.backend.user.domain.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 사용자 설정 언어.
 *
 * @param userId 사용자ID
 * @param langCd 언어코드
 * @author flowlog
 * @since 2026-11-05
 */
@Schema(description = "사용자 설정 언어")
public record UserLangResponse(
    @Schema(description = "사용자ID", example = "tx.viewer") String userId,
    @Schema(description = "언어코드", example = "en") String langCd) {
}
