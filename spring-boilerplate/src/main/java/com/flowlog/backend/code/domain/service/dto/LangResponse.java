package com.flowlog.backend.code.domain.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 지원 언어. (공통코드 LANG 기반 - 언어 탭/언어 선택/에디터 언어팩)
 *
 * @param langCd 언어코드 (ISO 639-1)
 * @param langNm 언어명 (요청 사용자 언어)
 * @param editorLocale 에디터 언어팩 (LANG.ATTR1)
 * @param badge 배지 표시 (대문자)
 * @param defaultLang 기본 언어 여부
 * @author flowlog
 * @since 2026-11-05
 */
@Schema(description = "지원 언어")
public record LangResponse(
    @Schema(description = "언어코드", example = "ko") String langCd,
    @Schema(description = "언어명", example = "한국어") String langNm,
    @Schema(description = "에디터 언어팩", example = "ko-KR") String editorLocale,
    @Schema(description = "배지", example = "KO") String badge,
    @Schema(description = "기본 언어 여부") boolean defaultLang) {
}
