package com.flowlog.backend.role.domain.service;

import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.constant.YesNo;
import com.flowlog.backend.menu.domain.model.Menu;
import com.flowlog.backend.menu.domain.repository.MenuRepository;
import com.flowlog.backend.role.domain.model.Role;
import com.flowlog.backend.role.domain.model.RoleMenu;
import com.flowlog.backend.role.domain.repository.RoleMenuRepository;
import com.flowlog.backend.role.domain.repository.RoleRepository;
import com.flowlog.backend.role.domain.service.dto.UserAuthority;
import com.flowlog.backend.user.domain.model.User;
import com.flowlog.backend.user.domain.repository.UserRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 DB 권한 계산 서비스. (인증 방식과 무관한 권한 원천)
 *
 * <ul>
 *   <li>사용자 식별 : IdP 고유ID({@code TB_USER.IDP_SUBJECT} = Keycloak user id = 토큰 sub).
 *       변경 가능한 사용자명({@code preferred_username}, NameID)으로 권한을 매핑하지 않는다.</li>
 *   <li>유효 권한 : 직접 부여 + 소속 부서 부여, 오늘 기준 기간 내 ({@link RoleRepository#findEffectiveRoles})</li>
 *   <li>API 권한 : 유효 권한의 메뉴 권한({@code TB_ROLE_MENU} LIST/ADD/EDIT/DEL) 중
 *       API 리소스가 연결된 메뉴({@code TB_MENU.PERM_CD})를 {@code {PERM_CD}.{list|add|edit|del}} 로 합산.
 *       → 커스텀 권한(예: 조회 전용 매니저)이 API 인가에 그대로 반영된다.</li>
 * </ul>
 *
 * <p>요청마다 호출되므로 결과를 캐시한다. ({@code app.security.authority-cache-ttl})
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Logging
@Service("userAuthorityService")
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserAuthorityService {

  /** 캐시 이름. */
  public static final String CACHE_NAME = "userAuthority";

  private static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final RoleMenuRepository roleMenuRepository;
  private final MenuRepository menuRepository;

  /**
   * IdP 고유ID(토큰 sub)로 사용자 DB 권한을 계산한다.
   *
   * @param subject IdP 사용자 고유ID
   * @return 사용자 권한 (미등록/미사용 사용자면 {@link UserAuthority#NONE})
   */
  @Cacheable(cacheNames = CACHE_NAME, key = "#subject", condition = "#subject != null")
  public UserAuthority loadBySubject(String subject) {
    Optional<User> user = StringUtils.isBlank(subject) ? Optional.empty()
        : userRepository.findByIdpSubjectAndUseYn(subject, YesNo.Y);
    return user.map(this::calculate).orElse(UserAuthority.NONE);
  }

  private UserAuthority calculate(User user) {
    List<Role> roles = roleRepository.findEffectiveRoles(user.getUserId(), LocalDate.now(ZONE_ID));
    List<Long> roleIds = roles.stream().map(Role::getId).toList();
    return new UserAuthority(
        user.getUserId(),
        roles.stream().map(Role::getRoleCd).toList(),
        roleIds.isEmpty() ? Set.of() : permissionsOf(roleIds));
  }

  private Set<String> permissionsOf(List<Long> roleIds) {
    List<RoleMenu> roleMenus = roleMenuRepository.findByIdRoleIdIn(roleIds);
    Map<Long, String> permCds = menuRepository.findAllById(
            roleMenus.stream().map(roleMenu -> roleMenu.getId().getMenuId()).distinct().toList())
        .stream()
        .filter(menu -> YesNo.isYes(menu.getUseYn()) && StringUtils.isNotBlank(menu.getPermCd()))
        .collect(Collectors.toMap(Menu::getId, Menu::getPermCd, (a, b) -> a));

    Set<String> permissions = new TreeSet<>();
    for (RoleMenu roleMenu : roleMenus) {
      String permCd = permCds.get(roleMenu.getId().getMenuId());
      if (permCd != null) {
        addIf(permissions, roleMenu.getListYn(), permCd + ".list");
        addIf(permissions, roleMenu.getAddYn(), permCd + ".add");
        addIf(permissions, roleMenu.getEditYn(), permCd + ".edit");
        addIf(permissions, roleMenu.getDelYn(), permCd + ".del");
      }
    }
    return Collections.unmodifiableSet(permissions);
  }

  private static void addIf(Set<String> permissions, String yn, String permission) {
    if (YesNo.isYes(yn)) {
      permissions.add(permission);
    }
  }
}
