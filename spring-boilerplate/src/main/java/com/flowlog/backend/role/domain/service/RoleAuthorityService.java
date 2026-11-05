package com.flowlog.backend.role.domain.service;

import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.role.domain.repository.RoleRepository;
import com.flowlog.backend.role.domain.service.dto.EffectiveRoleResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 유효 권한 조회 서비스. (오늘 기준, 직접 부여 + 소속 부서 부여)
 *
 * <p>인증 권한 계산은 {@code UserAuthorityService} / {@code AuthorityResolver} 가 담당한다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Logging
@Service("roleAuthorityService")
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RoleAuthorityService {

  private static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");

  private final RoleRepository roleRepository;

  /**
   * 사용자의 오늘 기준 유효 권한을 조회한다.
   *
   * @param userId 사용자ID (미등록/공백이면 빈 목록)
   * @return 유효 권한 목록 (상위 레벨 순)
   */
  public List<EffectiveRoleResponse> findEffectiveRoles(String userId) {
    List<EffectiveRoleResponse> roles = List.of();
    if (StringUtils.isNotBlank(userId)) {
      roles = roleRepository.findEffectiveRoles(userId, LocalDate.now(ZONE_ID)).stream()
          .map(EffectiveRoleResponse::from)
          .toList();
    }
    return roles;
  }
}
