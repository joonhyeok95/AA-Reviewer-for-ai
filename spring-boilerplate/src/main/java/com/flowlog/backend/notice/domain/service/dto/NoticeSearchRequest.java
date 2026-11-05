package com.flowlog.backend.notice.domain.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 공지 목록 검색 조건.
 *
 * @param keyword 검색어 (사용자 언어로 표시되는 버전의 제목/본문)
 * @param categoryCd 분류
 * @param includeScheduled 예약(게시 전) 공지 포함 - 공지 관리 권한자만 적용
 * @author flowlog
 * @since 2026-11-05
 */
@Schema(description = "공지 목록 검색 조건")
public record NoticeSearchRequest(
    @Schema(description = "검색어", example = "점검") @Size(max = 100) String keyword,
    @Schema(description = "분류", example = "GENERAL") @Size(max = 50) String categoryCd,
    @Schema(description = "예약 공지 포함 (관리자)", example = "false") Boolean includeScheduled) {
}
