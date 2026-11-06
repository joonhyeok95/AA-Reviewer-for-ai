package com.flowlog.backend.auth.domain.service.dto;

import com.flowlog.backend.role.domain.service.dto.EffectiveRoleResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 현재 로그인 사용자 정보.
 *
 * @param authType 인증 방식 (OAUTH2_SESSION / OAUTH2_BEARER / SAML2)
 * @param userId 사용자ID (OAuth2: preferred_username, SAML2: NameID)
 * @param userNm 사용자명 (TB_USER 미등록이면 null)
 * @param registered TB_USER 등록 여부
 * @param serviceAccount Client Credentials 서비스 계정 여부
 * @param clientId 토큰 발급 client (OAuth2 의 azp, SAML2 는 null)
 *     <p>토큰 값(Access/Refresh/ID Token)은 응답에 포함하지 않는다.
 * @param roles DB 유효 권한
 * @param authorities Spring Security 권한 (SCOPE_*, ROLE_*)
 * @param langCd 화면/데이터 표시 언어 (사용자 설정 → Accept-Language → 기본 언어)
 * @param userLangCd 사용자 설정 언어 (TB_USER.LANG_CD, 미설정이면 null)
 * @author flowlog
 * @since 2026-11-05
 */
@Schema(description = "현재 로그인 사용자")
public record AuthUserResponse(
    @Schema(description = "인증 방식", example = "OAUTH2_SESSION") String authType,
    @Schema(description = "사용자ID", example = "tx.viewer") String userId,
    @Schema(description = "사용자명", example = "TX조회매니저") String userNm,
    @Schema(description = "TB_USER 등록 여부") boolean registered,
    @Schema(description = "서비스 계정 여부 (Client Credentials)") boolean serviceAccount,
    @Schema(description = "토큰 발급 client", example = "flowlog-bff") String clientId,
    @Schema(description = "DB 유효 권한") List<EffectiveRoleResponse> roles,
    @Schema(description = "Spring Security 권한") List<String> authorities,
    @Schema(description = "표시 언어 (사용자 설정 → Accept-Language → 기본 언어)", example = "ko")
    String langCd,
    @Schema(description = "사용자 설정 언어 (미설정 null)", example = "en") String userLangCd) {
}
