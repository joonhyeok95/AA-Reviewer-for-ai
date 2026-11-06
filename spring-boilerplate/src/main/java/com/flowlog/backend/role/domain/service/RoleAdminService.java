package com.flowlog.backend.role.domain.service;

import com.flowlog.backend.code.domain.repository.CodeRepository;
import com.flowlog.backend.code.domain.service.LangService;
import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.constant.DateConstants;
import com.flowlog.backend.global.constant.YesNo;
import com.flowlog.backend.global.exception.BusinessException;
import com.flowlog.backend.menu.domain.model.Menu;
import com.flowlog.backend.menu.domain.model.MenuLang;
import com.flowlog.backend.menu.domain.repository.MenuLangRepository;
import com.flowlog.backend.menu.domain.repository.MenuRepository;
import com.flowlog.backend.menu.exception.MenuErrorCode;
import com.flowlog.backend.role.domain.model.BaseRoleGrant;
import com.flowlog.backend.role.domain.model.DeptRole;
import com.flowlog.backend.role.domain.model.Role;
import com.flowlog.backend.role.domain.model.RoleLang;
import com.flowlog.backend.role.domain.model.RoleMenu;
import com.flowlog.backend.role.domain.model.UserRole;
import com.flowlog.backend.role.domain.repository.DeptRoleRepository;
import com.flowlog.backend.role.domain.repository.RoleLangRepository;
import com.flowlog.backend.role.domain.repository.RoleMenuRepository;
import com.flowlog.backend.role.domain.repository.RoleRepository;
import com.flowlog.backend.role.domain.repository.UserRoleRepository;
import com.flowlog.backend.role.domain.service.dto.RoleAdminDtos.RoleAdminResponse;
import com.flowlog.backend.role.domain.service.dto.RoleAdminDtos.RoleGrantResponse;
import com.flowlog.backend.role.domain.service.dto.RoleAdminDtos.RoleGrantSaveRequest;
import com.flowlog.backend.role.domain.service.dto.RoleAdminDtos.RoleGrantsResponse;
import com.flowlog.backend.role.domain.service.dto.RoleAdminDtos.RoleMenuItem;
import com.flowlog.backend.role.domain.service.dto.RoleAdminDtos.RoleMenuNode;
import com.flowlog.backend.role.domain.service.dto.RoleAdminDtos.RoleMenuSaveRequest;
import com.flowlog.backend.role.domain.service.dto.RoleAdminDtos.RoleMenusResponse;
import com.flowlog.backend.role.domain.service.dto.RoleAdminDtos.RoleSaveRequest;
import com.flowlog.backend.role.exception.RoleErrorCode;
import com.flowlog.backend.user.domain.model.Dept;
import com.flowlog.backend.user.domain.model.User;
import com.flowlog.backend.user.domain.model.UserDept;
import com.flowlog.backend.user.domain.model.Workplace;
import com.flowlog.backend.user.domain.repository.DeptRepository;
import com.flowlog.backend.user.domain.repository.UserDeptRepository;
import com.flowlog.backend.user.domain.repository.UserRepository;
import com.flowlog.backend.user.domain.repository.WorkplaceRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 권한 관리 서비스. (권한, 권한별 메뉴 접근, 사용자/부서 부여)
 *
 * <ul>
 *   <li>API 권한 = IdP scope ∩ DB 업무 권한. 여기서 바꾸는 메뉴 접근(LIST/ADD/EDIT/DEL × 메뉴 PERM_CD)과
 *       부여(기간)가 DB 업무 권한이며, 변경 즉시 권한 캐시를 비워 다음 요청부터 반영된다</li>
 *   <li>시스템관리자 권한(레벨 1)의 생성·수정·부여는 시스템관리자만 (권한 상승 방지)</li>
 *   <li>기본 권한(SYSTEM_YN=Y)은 삭제·유형/레벨 변경 불가. 부여 내역이 있는 권한은 삭제 불가</li>
 *   <li>메뉴 접근 저장 시 등록/수정/삭제가 있으면 조회도 허용, 상위 메뉴는 조회 허용 (메뉴 트리에 보이도록)</li>
 *   <li>같은 대상에게 같은 권한을 기간이 겹치게 부여할 수 없다</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-06
 */
@Logging
@Service("roleAdminService")
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RoleAdminService {

  /** 부여 대상 유형: 사용자. */
  public static final String TARGET_USER = "USER";

  /** 부여 대상 유형: 부서. */
  public static final String TARGET_DEPT = "DEPT";

  private static final String ROLE_TYPE_GROUP = "ROLE_TYPE";
  private static final String MODULE_GROUP = "MODULE";
  private static final String SYSTEM_ADMIN_TYPE = "SYSTEM_ADMIN";

  private final RoleRepository roleRepository;
  private final RoleLangRepository roleLangRepository;
  private final RoleMenuRepository roleMenuRepository;
  private final UserRoleRepository userRoleRepository;
  private final DeptRoleRepository deptRoleRepository;
  private final MenuRepository menuRepository;
  private final MenuLangRepository menuLangRepository;
  private final CodeRepository codeRepository;
  private final UserRepository userRepository;
  private final UserDeptRepository userDeptRepository;
  private final DeptRepository deptRepository;
  private final WorkplaceRepository workplaceRepository;
  private final LangService langService;
  private final Clock clock;

  // ------------------------------------------------------------------ 권한

  /**
   * 전체 권한 (미사용 포함).
   *
   * @param langCd 표시 언어
   * @return 권한 목록
   */
  public List<RoleAdminResponse> getRoles(String langCd) {
    Map<Long, Map<String, String>> names = new HashMap<>();
    for (RoleLang lang : roleLangRepository.findAll()) {
      names.computeIfAbsent(lang.getId().getRoleId(), key -> new LinkedHashMap<>())
          .put(lang.getId().getLangCd(), lang.getRoleNm());
    }
    Map<Long, Long> users = counts(userRoleRepository.countByRole());
    Map<Long, Long> depts = counts(deptRoleRepository.countByRole());
    Map<Long, Long> menus = counts(roleMenuRepository.countByRole());
    return roleRepository.findAllByOrderByRoleLevelAscSortOrderAscIdAsc().stream()
        .map(role -> toResponse(role, names.getOrDefault(role.getId(), Map.of()), langCd,
            users.getOrDefault(role.getId(), 0L), depts.getOrDefault(role.getId(), 0L),
            menus.getOrDefault(role.getId(), 0L)))
        .toList();
  }

  /**
   * 권한 등록.
   *
   * @param request 요청
   * @param sysAdmin 요청자가 시스템관리자인지
   * @param langCd 표시 언어
   * @return 등록된 권한
   */
  @Transactional
  @CacheEvict(cacheNames = UserAuthorityService.CACHE_NAME, allEntries = true)
  public RoleAdminResponse createRole(RoleSaveRequest request, boolean sysAdmin, String langCd) {
    if (roleRepository.existsByRoleCd(request.roleCd())) {
      throw new BusinessException(RoleErrorCode.ROLE_DUPLICATED, request.roleCd());
    }
    validateRole(request, sysAdmin);
    Map<String, String> names = langService.normalizeNames(request.names());
    Role role = roleRepository.save(Role.builder()
        .roleCd(request.roleCd())
        .roleTypeCd(request.roleTypeCd())
        .roleLevel(request.roleLevel())
        .moduleCd(StringUtils.trimToNull(request.moduleCd()))
        .roleDesc(StringUtils.trimToNull(request.roleDesc()))
        .sortOrder(request.sortOrder())
        .systemYn(YesNo.N)
        .useYn(request.useYn())
        .build());
    saveNames(role.getId(), names);
    return toResponse(role, names, langCd, 0, 0, 0);
  }

  /**
   * 권한 수정. (권한코드는 바꾸지 않음)
   *
   * @param roleId 권한ID
   * @param request 요청
   * @param sysAdmin 요청자가 시스템관리자인지
   * @param langCd 표시 언어
   * @return 수정된 권한
   */
  @Transactional
  @CacheEvict(cacheNames = UserAuthorityService.CACHE_NAME, allEntries = true)
  public RoleAdminResponse updateRole(
      Long roleId, RoleSaveRequest request, boolean sysAdmin, String langCd) {
    Role role = findRole(roleId);
    requireSysAdminFor(role, sysAdmin);
    validateRole(request, sysAdmin);
    if (YesNo.isYes(role.getSystemYn())
        && (!role.getRoleTypeCd().equals(request.roleTypeCd())
        || !role.getRoleLevel().equals(request.roleLevel()))) {
      throw new BusinessException(RoleErrorCode.SYSTEM_ROLE_PROTECTED, role.getRoleCd());
    }
    Map<String, String> names = langService.normalizeNames(request.names());
    role.update(request.roleTypeCd(), request.roleLevel(),
        StringUtils.trimToNull(request.moduleCd()), StringUtils.trimToNull(request.roleDesc()),
        request.sortOrder(), request.useYn());
    saveNames(roleId, names);
    return toResponse(role, names, langCd, userRoleRepository.findByRoleId(roleId).size(),
        deptRoleRepository.findByRoleId(roleId).size(),
        roleMenuRepository.findByIdRoleId(roleId).size());
  }

  /**
   * 권한 삭제. (기본 권한·부여 내역이 있는 권한 불가, 메뉴 접근·언어별 이름 함께 삭제)
   *
   * @param roleId 권한ID
   * @param sysAdmin 요청자가 시스템관리자인지
   */
  @Transactional
  @CacheEvict(cacheNames = UserAuthorityService.CACHE_NAME, allEntries = true)
  public void deleteRole(Long roleId, boolean sysAdmin) {
    Role role = findRole(roleId);
    requireSysAdminFor(role, sysAdmin);
    if (YesNo.isYes(role.getSystemYn())) {
      throw new BusinessException(RoleErrorCode.SYSTEM_ROLE_PROTECTED, role.getRoleCd());
    }
    int grants = userRoleRepository.findByRoleId(roleId).size()
        + deptRoleRepository.findByRoleId(roleId).size();
    if (grants > 0) {
      throw new BusinessException(RoleErrorCode.ROLE_IN_USE, String.valueOf(grants));
    }
    roleMenuRepository.deleteAll(roleMenuRepository.findByIdRoleId(roleId));
    roleLangRepository.deleteAll(roleLangRepository.findByIdRoleId(roleId));
    roleRepository.delete(role);
  }

  // ------------------------------------------------------------------ 메뉴 접근

  /**
   * 권한의 메뉴 접근. (전체 메뉴 트리 + 이 권한의 LIST/ADD/EDIT/DEL)
   *
   * @param roleId 권한ID
   * @param langCd 표시 언어
   * @return 메뉴 접근
   */
  public RoleMenusResponse getRoleMenus(Long roleId, String langCd) {
    Role role = findRole(roleId);
    Map<Long, RoleMenu> granted = roleMenuRepository.findByIdRoleId(roleId).stream()
        .collect(Collectors.toMap(roleMenu -> roleMenu.getId().getMenuId(), Function.identity()));
    Map<Long, Map<String, String>> names = new HashMap<>();
    for (MenuLang lang : menuLangRepository.findAll()) {
      names.computeIfAbsent(lang.getId().getMenuId(), key -> new LinkedHashMap<>())
          .put(lang.getId().getLangCd(), lang.getMenuNm());
    }
    Map<Long, List<Menu>> byParent = new HashMap<>();
    menuRepository.findAllByOrderBySortOrderAscIdAsc().forEach(menu ->
        byParent.computeIfAbsent(menu.getParentMenuId(), key -> new ArrayList<>()).add(menu));
    return new RoleMenusResponse(roleId, role.getRoleCd(), role.isSystemAdmin(),
        menuTree(byParent, granted, names, langCd, null));
  }

  /**
   * 권한의 메뉴 접근 저장. (전체 교체)
   *
   * @param roleId 권한ID
   * @param request 요청
   * @param sysAdmin 요청자가 시스템관리자인지
   * @param langCd 표시 언어
   * @return 저장 결과
   */
  @Transactional
  @CacheEvict(cacheNames = UserAuthorityService.CACHE_NAME, allEntries = true)
  public RoleMenusResponse saveRoleMenus(
      Long roleId, RoleMenuSaveRequest request, boolean sysAdmin, String langCd) {
    Role role = findRole(roleId);
    requireSysAdminFor(role, sysAdmin);
    Map<Long, Menu> menus = menuRepository.findAll().stream()
        .collect(Collectors.toMap(Menu::getId, Function.identity()));
    Map<Long, String[]> wanted = new LinkedHashMap<>();
    for (RoleMenuItem item : request.items()) {
      if (!menus.containsKey(item.menuId())) {
        throw new BusinessException(MenuErrorCode.MENU_NOT_FOUND, item.menuId());
      }
      boolean any = YesNo.isYes(item.addYn()) || YesNo.isYes(item.editYn())
          || YesNo.isYes(item.delYn());
      String list = any ? YesNo.Y : item.listYn();
      if (YesNo.isYes(list)) {
        wanted.put(item.menuId(), new String[] {list, item.addYn(), item.editYn(), item.delYn()});
      }
    }
    // 상위 메뉴는 조회 허용 (상위가 없으면 사용자 메뉴 트리에 나타나지 않음)
    for (Long menuId : List.copyOf(wanted.keySet())) {
      Long parentId = menus.get(menuId).getParentMenuId();
      int guard = 0;
      while (parentId != null && guard++ < 1000) {
        wanted.putIfAbsent(parentId, new String[] {YesNo.Y, YesNo.N, YesNo.N, YesNo.N});
        parentId = menus.get(parentId).getParentMenuId();
      }
    }
    Map<Long, RoleMenu> existing = roleMenuRepository.findByIdRoleId(roleId).stream()
        .collect(Collectors.toMap(roleMenu -> roleMenu.getId().getMenuId(), Function.identity()));
    wanted.forEach((menuId, flags) -> {
      RoleMenu roleMenu = existing.remove(menuId);
      if (roleMenu == null) {
        roleMenuRepository.save(RoleMenu.builder().roleId(roleId).menuId(menuId)
            .listYn(flags[0]).addYn(flags[1]).editYn(flags[2]).delYn(flags[3]).build());
      } else {
        roleMenu.changeAuthority(flags[0], flags[1], flags[2], flags[3]);
      }
    });
    roleMenuRepository.deleteAll(existing.values());
    roleMenuRepository.flush();
    return getRoleMenus(roleId, langCd);
  }

  // ------------------------------------------------------------------ 부여

  /**
   * 권한의 사용자/부서 부여 내역.
   *
   * @param roleId 권한ID
   * @param langCd 표시 언어 (부서·사업장명)
   * @return 부여 내역
   */
  public RoleGrantsResponse getGrants(Long roleId, String langCd) {
    findRole(roleId);
    LocalDate today = LocalDate.now(clock);
    List<UserRole> userRoles = userRoleRepository.findByRoleId(roleId);
    List<DeptRole> deptRoles = deptRoleRepository.findByRoleId(roleId);

    List<String> userIds = userRoles.stream().map(UserRole::getUserId).distinct().toList();
    Map<String, User> users = userRepository.findAllById(userIds).stream()
        .collect(Collectors.toMap(User::getUserId, Function.identity()));
    Map<String, String> mainDepts = userDeptRepository.findByIdUserIdIn(userIds).stream()
        .filter(userDept -> YesNo.isYes(userDept.getMainDeptYn()))
        .collect(Collectors.toMap(userDept -> userDept.getId().getUserId(),
            userDept -> userDept.getId().getDeptCd(), (a, b) -> a));
    Map<String, Dept> depts = deptRepository.findAll().stream()
        .collect(Collectors.toMap(Dept::getDeptCd, Function.identity()));
    Map<String, Workplace> workplaces = workplaceRepository.findAll().stream()
        .collect(Collectors.toMap(Workplace::getWorkplaceCd, Function.identity()));

    List<RoleGrantResponse> userGrants = userRoles.stream()
        .map(grant -> {
          User user = users.get(grant.getUserId());
          Dept dept = depts.get(mainDepts.get(grant.getUserId()));
          return toGrant(grant, grant.getId(), TARGET_USER, grant.getUserId(),
              user == null ? null : user.getUserNm(), dept == null ? null : dept.nameOf(langCd),
              today);
        })
        .sorted(grantOrder())
        .toList();
    List<RoleGrantResponse> deptGrants = deptRoles.stream()
        .map(grant -> {
          Dept dept = depts.get(grant.getDeptCd());
          Workplace workplace = dept == null ? null : workplaces.get(dept.getWorkplaceCd());
          return toGrant(grant, grant.getId(), TARGET_DEPT, grant.getDeptCd(),
              dept == null ? null : dept.nameOf(langCd),
              workplace == null ? null : workplace.nameOf(langCd), today);
        })
        .sorted(grantOrder())
        .toList();
    return new RoleGrantsResponse(roleId, userGrants, deptGrants);
  }

  /**
   * 권한 부여. (사용자 또는 부서, 기간)
   *
   * @param roleId 권한ID
   * @param request 요청
   * @param sysAdmin 요청자가 시스템관리자인지
   * @param langCd 표시 언어
   * @return 부여 내역
   */
  @Transactional
  @CacheEvict(cacheNames = UserAuthorityService.CACHE_NAME, allEntries = true)
  public RoleGrantsResponse createGrant(
      Long roleId, RoleGrantSaveRequest request, boolean sysAdmin, String langCd) {
    Role role = findRole(roleId);
    requireSysAdminFor(role, sysAdmin);
    LocalDate endDt = endOf(request);
    String reason = StringUtils.trimToNull(request.grantReason());
    if (TARGET_USER.equals(request.targetType())) {
      if (!userRepository.existsById(request.targetId())) {
        throw new BusinessException(RoleErrorCode.GRANT_TARGET_NOT_FOUND, TARGET_USER,
            request.targetId());
      }
      requireNoOverlap(userRoleRepository.findByRoleId(roleId).stream()
          .filter(grant -> grant.getUserId().equals(request.targetId())).toList(),
          null, request, endDt);
      userRoleRepository.save(UserRole.builder().userId(request.targetId()).roleId(roleId)
          .startDt(request.startDt()).endDt(endDt).grantReason(reason).build());
    } else {
      if (!deptRepository.existsById(request.targetId())) {
        throw new BusinessException(RoleErrorCode.GRANT_TARGET_NOT_FOUND, TARGET_DEPT,
            request.targetId());
      }
      requireNoOverlap(deptRoleRepository.findByRoleId(roleId).stream()
          .filter(grant -> grant.getDeptCd().equals(request.targetId())).toList(),
          null, request, endDt);
      deptRoleRepository.save(DeptRole.builder().deptCd(request.targetId()).roleId(roleId)
          .startDt(request.startDt()).endDt(endDt).grantReason(reason).build());
    }
    return getGrants(roleId, langCd);
  }

  /**
   * 부여 기간·사유 변경. (대상은 바꾸지 않음)
   *
   * @param roleId 권한ID
   * @param targetType USER / DEPT
   * @param grantId 부여ID
   * @param request 요청
   * @param sysAdmin 요청자가 시스템관리자인지
   * @param langCd 표시 언어
   * @return 부여 내역
   */
  @Transactional
  @CacheEvict(cacheNames = UserAuthorityService.CACHE_NAME, allEntries = true)
  public RoleGrantsResponse updateGrant(
      Long roleId, String targetType, Long grantId, RoleGrantSaveRequest request,
      boolean sysAdmin, String langCd) {
    requireSysAdminFor(findRole(roleId), sysAdmin);
    LocalDate endDt = endOf(request);
    BaseRoleGrant grant;
    List<? extends BaseRoleGrant> sameTarget;
    if (TARGET_USER.equals(targetType)) {
      UserRole userRole = userRoleRepository.findById(grantId)
          .filter(found -> found.getRoleId().equals(roleId))
          .orElseThrow(() -> new BusinessException(RoleErrorCode.GRANT_NOT_FOUND, grantId));
      grant = userRole;
      sameTarget = userRoleRepository.findByRoleId(roleId).stream()
          .filter(other -> other.getUserId().equals(userRole.getUserId())).toList();
    } else {
      DeptRole deptRole = deptRoleRepository.findById(grantId)
          .filter(found -> found.getRoleId().equals(roleId))
          .orElseThrow(() -> new BusinessException(RoleErrorCode.GRANT_NOT_FOUND, grantId));
      grant = deptRole;
      sameTarget = deptRoleRepository.findByRoleId(roleId).stream()
          .filter(other -> other.getDeptCd().equals(deptRole.getDeptCd())).toList();
    }
    requireNoOverlap(sameTarget, grant, request, endDt);
    grant.changePeriod(request.startDt(), endDt);
    grant.changeReason(StringUtils.trimToNull(request.grantReason()));
    return getGrants(roleId, langCd);
  }

  /**
   * 부여 회수. (즉시 삭제 - 기간을 줄여 종료하려면 수정 사용)
   *
   * @param roleId 권한ID
   * @param targetType USER / DEPT
   * @param grantId 부여ID
   * @param sysAdmin 요청자가 시스템관리자인지
   */
  @Transactional
  @CacheEvict(cacheNames = UserAuthorityService.CACHE_NAME, allEntries = true)
  public void deleteGrant(Long roleId, String targetType, Long grantId, boolean sysAdmin) {
    requireSysAdminFor(findRole(roleId), sysAdmin);
    if (TARGET_USER.equals(targetType)) {
      userRoleRepository.delete(userRoleRepository.findById(grantId)
          .filter(found -> found.getRoleId().equals(roleId))
          .orElseThrow(() -> new BusinessException(RoleErrorCode.GRANT_NOT_FOUND, grantId)));
    } else {
      deptRoleRepository.delete(deptRoleRepository.findById(grantId)
          .filter(found -> found.getRoleId().equals(roleId))
          .orElseThrow(() -> new BusinessException(RoleErrorCode.GRANT_NOT_FOUND, grantId)));
    }
  }

  // ------------------------------------------------------------------ 내부

  private void validateRole(RoleSaveRequest request, boolean sysAdmin) {
    requireCode(ROLE_TYPE_GROUP, request.roleTypeCd());
    if (StringUtils.isNotBlank(request.moduleCd())) {
      requireCode(MODULE_GROUP, request.moduleCd().trim());
    }
    boolean adminLevel = request.roleLevel() == Role.LEVEL_SYSTEM_ADMIN
        || SYSTEM_ADMIN_TYPE.equals(request.roleTypeCd());
    if (adminLevel && !sysAdmin) {
      throw new BusinessException(RoleErrorCode.SYS_ADMIN_ONLY);
    }
  }

  private void requireCode(String groupCd, String code) {
    if (!codeRepository.existsByIdGroupCdAndIdCodeAndUseYn(groupCd, code, YesNo.Y)) {
      throw new BusinessException(RoleErrorCode.INVALID_CODE, groupCd, code);
    }
  }

  /** 시스템관리자 권한(레벨 1 / 유형 SYSTEM_ADMIN)은 시스템관리자만 변경·부여. */
  private static void requireSysAdminFor(Role role, boolean sysAdmin) {
    if ((role.isSystemAdmin() || SYSTEM_ADMIN_TYPE.equals(role.getRoleTypeCd())) && !sysAdmin) {
      throw new BusinessException(RoleErrorCode.SYS_ADMIN_ONLY);
    }
  }

  private static LocalDate endOf(RoleGrantSaveRequest request) {
    LocalDate endDt = request.endDt() == null ? DateConstants.MAX_END_DATE : request.endDt();
    if (request.startDt().isAfter(endDt)) {
      throw new BusinessException(RoleErrorCode.GRANT_PERIOD_INVALID);
    }
    return endDt;
  }

  private static void requireNoOverlap(
      List<? extends BaseRoleGrant> sameTarget, BaseRoleGrant self, RoleGrantSaveRequest request,
      LocalDate endDt) {
    boolean overlap = sameTarget.stream()
        .filter(grant -> grant != self)
        .anyMatch(grant -> grant.overlaps(request.startDt(), endDt));
    if (overlap) {
      throw new BusinessException(RoleErrorCode.GRANT_OVERLAP, request.targetType(),
          request.targetId());
    }
  }

  private void saveNames(Long roleId, Map<String, String> names) {
    Map<String, RoleLang> existing = roleLangRepository.findByIdRoleId(roleId).stream()
        .collect(Collectors.toMap(lang -> lang.getId().getLangCd(), Function.identity()));
    names.forEach((langCd, name) -> {
      RoleLang lang = existing.remove(langCd);
      if (lang == null) {
        roleLangRepository.save(new RoleLang(roleId, langCd, name));
      } else {
        lang.changeName(name);
      }
    });
    roleLangRepository.deleteAll(existing.entrySet().stream()
        .filter(entry -> langService.isActive(entry.getKey())).map(Map.Entry::getValue).toList());
  }

  private List<RoleMenuNode> menuTree(
      Map<Long, List<Menu>> byParent, Map<Long, RoleMenu> granted,
      Map<Long, Map<String, String>> names, String langCd, Long parentId) {
    List<RoleMenuNode> nodes = new ArrayList<>();
    for (Menu menu : byParent.getOrDefault(parentId, List.of())) {
      RoleMenu roleMenu = granted.get(menu.getId());
      nodes.add(new RoleMenuNode(menu.getId(),
          langService.nameOf(names.getOrDefault(menu.getId(), Map.of()), langCd),
          menu.getViewTypeCd(), menu.getPermCd(), menu.getUseYn(), menu.getDisplayYn(),
          roleMenu == null ? YesNo.N : roleMenu.getListYn(),
          roleMenu == null ? YesNo.N : roleMenu.getAddYn(),
          roleMenu == null ? YesNo.N : roleMenu.getEditYn(),
          roleMenu == null ? YesNo.N : roleMenu.getDelYn(),
          menuTree(byParent, granted, names, langCd, menu.getId())));
    }
    return nodes;
  }

  private static RoleGrantResponse toGrant(
      BaseRoleGrant grant, Long grantId, String targetType, String targetId, String targetNm,
      String targetSubNm, LocalDate today) {
    String status = grant.isValidOn(today) ? "ACTIVE"
        : today.isBefore(grant.getStartDt()) ? "SCHEDULED" : "EXPIRED";
    return new RoleGrantResponse(grantId, targetType, targetId, targetNm, targetSubNm,
        grant.getStartDt(), grant.getEndDt(), grant.getGrantReason(), status,
        grant.getUpdatedBy(), grant.getUpdatedAt());
  }

  /** 유효 → 시작 전 → 만료, 같은 상태는 대상ID. */
  private static Comparator<RoleGrantResponse> grantOrder() {
    Map<String, Integer> rank = Map.of("ACTIVE", 0, "SCHEDULED", 1, "EXPIRED", 2);
    return Comparator.<RoleGrantResponse>comparingInt(grant -> rank.get(grant.status()))
        .thenComparing(RoleGrantResponse::targetId)
        .thenComparing(RoleGrantResponse::startDt);
  }

  private static Map<Long, Long> counts(List<Object[]> rows) {
    return rows.stream().collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1],
        (a, b) -> a, HashMap::new));
  }

  private RoleAdminResponse toResponse(
      Role role, Map<String, String> names, String langCd, long users, long depts, long menus) {
    return new RoleAdminResponse(role.getId(), role.getRoleCd(), langService.nameOf(names, langCd),
        names, role.getRoleTypeCd(), role.getRoleLevel(), role.getModuleCd(), role.getRoleDesc(),
        role.getSortOrder(), role.getSystemYn(), role.getUseYn(), users, depts, menus);
  }

  private Role findRole(Long roleId) {
    return roleRepository.findById(roleId)
        .orElseThrow(() -> new BusinessException(RoleErrorCode.ROLE_NOT_FOUND, roleId));
  }
}
