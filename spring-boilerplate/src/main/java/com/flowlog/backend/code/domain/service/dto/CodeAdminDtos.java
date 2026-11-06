package com.flowlog.backend.code.domain.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * 공통코드 관리 DTO.
 *
 * <p>이름은 언어별 맵(언어코드 → 이름)으로 주고받는다. 기본 언어 이름은 필수, 그 외 언어는 비우면 기본 언어로 표시.
 *
 * @author flowlog
 * @since 2026-11-06
 */
public final class CodeAdminDtos {

  private CodeAdminDtos() {
  }

  /**
   * 코드 그룹 (관리 목록).
   *
   * @param groupCd 그룹코드
   * @param groupNm 그룹명 (요청 언어, 없으면 기본 언어)
   * @param names 언어별 그룹명
   * @param groupDesc 설명
   * @param sortOrder 정렬순서
   * @param systemYn 시스템 그룹 여부 (삭제 불가, 코드 삭제 불가)
   * @param useYn 사용여부
   * @param codeCnt 코드 수 (미사용 포함)
   */
  @Schema(description = "공통코드 그룹 (관리)")
  public record CodeGroupAdminResponse(
      String groupCd, String groupNm, Map<String, String> names, String groupDesc,
      Integer sortOrder, String systemYn, String useYn, long codeCnt) {
  }

  /**
   * 코드 그룹 등록/수정 요청. (수정 시 groupCd 는 경로 값 사용)
   *
   * @param groupCd 그룹코드 (영문 대문자·숫자·_)
   * @param names 언어별 그룹명
   * @param groupDesc 설명
   * @param sortOrder 정렬순서
   * @param useYn 사용여부
   */
  @Schema(description = "공통코드 그룹 등록/수정 요청")
  public record CodeGroupSaveRequest(
      @Schema(description = "그룹코드", example = "NOTICE_TYPE")
      @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,49}$") String groupCd,
      @Schema(description = "언어별 그룹명", example = "{\"ko\": \"공지 유형\", \"en\": \"Notice type\"}")
      @NotEmpty Map<@NotBlank @Size(max = 10) String, @Size(max = 200) String> names,
      @Schema(description = "설명") @Size(max = 1000) String groupDesc,
      @Schema(description = "정렬순서", example = "10") @NotNull @Min(0) @Max(99999) Integer sortOrder,
      @Schema(description = "사용여부", example = "Y") @NotBlank @Pattern(regexp = "^[YN]$")
      String useYn) {
  }

  /**
   * 코드 (관리 목록).
   *
   * @param groupCd 그룹코드
   * @param code 코드
   * @param codeNm 코드명 (요청 언어, 없으면 기본 언어)
   * @param names 언어별 코드명
   * @param codeDesc 설명
   * @param sortOrder 정렬순서
   * @param attr1 확장속성1
   * @param attr2 확장속성2
   * @param attr3 확장속성3
   * @param useYn 사용여부
   */
  @Schema(description = "공통코드 (관리)")
  public record CodeAdminResponse(
      String groupCd, String code, String codeNm, Map<String, String> names, String codeDesc,
      Integer sortOrder, String attr1, String attr2, String attr3, String useYn) {
  }

  /**
   * 코드 등록/수정 요청. (수정 시 code 는 경로 값 사용)
   *
   * @param code 코드 (영문·숫자·_ . -)
   * @param names 언어별 코드명
   * @param codeDesc 설명
   * @param sortOrder 정렬순서
   * @param attr1 확장속성1 (예: LANG 의 에디터 언어팩)
   * @param attr2 확장속성2
   * @param attr3 확장속성3
   * @param useYn 사용여부
   */
  @Schema(description = "공통코드 등록/수정 요청")
  public record CodeSaveRequest(
      @Schema(description = "코드", example = "URGENT")
      @NotBlank @Pattern(regexp = "^[A-Za-z0-9_.-]{1,50}$") String code,
      @Schema(description = "언어별 코드명", example = "{\"ko\": \"긴급\", \"en\": \"Urgent\"}")
      @NotEmpty Map<@NotBlank @Size(max = 10) String, @Size(max = 200) String> names,
      @Schema(description = "설명") @Size(max = 1000) String codeDesc,
      @Schema(description = "정렬순서", example = "1") @NotNull @Min(0) @Max(99999) Integer sortOrder,
      @Schema(description = "확장속성1") @Size(max = 200) String attr1,
      @Schema(description = "확장속성2") @Size(max = 200) String attr2,
      @Schema(description = "확장속성3") @Size(max = 200) String attr3,
      @Schema(description = "사용여부", example = "Y") @NotBlank @Pattern(regexp = "^[YN]$")
      String useYn) {
  }
}
