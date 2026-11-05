package com.flowlog.backend.role.domain.service.dto;

import com.flowlog.backend.role.domain.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 사용자의 유효 권한.
 *
 * @param roleCd 권한코드
 * @param roleTypeCd 권한유형
 * @param roleLevel 권한레벨 (1 시스템관리자 ~ 4 일반사용자)
 * @param moduleCd 모듈코드 (모듈 무관이면 null)
 * @author flowlog
 * @since 2026-11-05
 */
@Schema(description = "유효 권한")
public record EffectiveRoleResponse(
    @Schema(description = "권한코드", example = "TX_MANAGER_READ") String roleCd,
    @Schema(description = "권한유형", example = "MODULE_MANAGER") String roleTypeCd,
    @Schema(description = "권한레벨", example = "3") Integer roleLevel,
    @Schema(description = "모듈코드", example = "TX") String moduleCd) {

  /**
   * Entity 를 응답 DTO 로 변환한다.
   *
   * @param role 권한 Entity
   * @return 유효 권한
   */
  public static EffectiveRoleResponse from(Role role) {
    return new EffectiveRoleResponse(
        role.getRoleCd(), role.getRoleTypeCd(), role.getRoleLevel(), role.getModuleCd());
  }
}
