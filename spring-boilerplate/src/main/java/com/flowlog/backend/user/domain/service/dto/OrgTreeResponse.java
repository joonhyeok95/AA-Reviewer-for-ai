package com.flowlog.backend.user.domain.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 조직도 (인사 마스터) - 사업장 노드.
 *
 * @param workplaceCd 사업장코드
 * @param workplaceNm 사업장명 (요청 언어)
 * @param depts 부서 트리
 * @author flowlog
 * @since 2026-11-05
 */
@Schema(description = "조직도 - 사업장")
public record OrgTreeResponse(
    @Schema(description = "사업장코드", example = "SEOUL") String workplaceCd,
    @Schema(description = "사업장명", example = "서울 본사") String workplaceNm,
    @Schema(description = "부서 트리") List<DeptNode> depts) {

  /**
   * 부서 노드.
   *
   * @param deptCd 부서코드
   * @param deptNm 부서명 (요청 언어)
   * @param workplaceCd 사업장코드
   * @param children 하위 부서
   */
  @Schema(description = "조직도 - 부서")
  public record DeptNode(
      @Schema(description = "부서코드", example = "TX_TEAM") String deptCd,
      @Schema(description = "부서명", example = "TX팀") String deptNm,
      @Schema(description = "사업장코드", example = "SEOUL") String workplaceCd,
      @Schema(description = "하위 부서") List<DeptNode> children) {
  }
}
