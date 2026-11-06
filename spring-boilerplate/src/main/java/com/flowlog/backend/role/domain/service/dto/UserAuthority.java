package com.flowlog.backend.role.domain.service.dto;

import java.util.List;
import java.util.Set;

/**
 * 사용자 DB 권한 (권한 계산 결과, 캐시 대상).
 *
 * @param userId 사용자ID (미등록이면 null)
 * @param roleCds 오늘 기준 유효 권한코드 (상위 레벨 순)
 * @param permissions API 권한 (예: sample.list) - 유효 권한의 메뉴 권한(TB_ROLE_MENU)에서 계산
 * @author flowlog
 * @since 2026-11-05
 */
public record UserAuthority(String userId, List<String> roleCds, Set<String> permissions) {

  /** 미등록 사용자 (권한 없음). */
  public static final UserAuthority NONE = new UserAuthority(null, List.of(), Set.of());

  /**
   * 등록 사용자 여부.
   *
   * @return TB_USER 에 연결된 사용자면 true
   */
  public boolean registered() {
    return userId != null;
  }
}
