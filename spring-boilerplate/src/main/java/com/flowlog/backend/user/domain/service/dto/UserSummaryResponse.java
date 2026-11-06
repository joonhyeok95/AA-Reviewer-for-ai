package com.flowlog.backend.user.domain.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 사용자 검색 결과. (권한 부여 대상 선택)
 *
 * @param userId 사용자ID
 * @param userNm 사용자명
 * @param userTypeCd 사용자유형 (공통코드 USER_TYPE)
 * @param deptNm 주부서명 (요청 언어)
 * @author flowlog
 * @since 2026-11-06
 */
@Schema(description = "사용자 검색 결과")
public record UserSummaryResponse(
    @Schema(description = "사용자ID", example = "emp01") String userId,
    @Schema(description = "사용자명", example = "임직원01") String userNm,
    @Schema(description = "사용자유형", example = "EMPLOYEE") String userTypeCd,
    @Schema(description = "주부서명", example = "본사") String deptNm) {
}
