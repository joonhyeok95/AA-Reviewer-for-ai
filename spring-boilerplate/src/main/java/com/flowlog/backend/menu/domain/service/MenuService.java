package com.flowlog.backend.menu.domain.service;

import com.flowlog.backend.code.domain.service.LangService;
import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.constant.YesNo;
import com.flowlog.backend.global.exception.BusinessException;
import com.flowlog.backend.menu.domain.model.Menu;
import com.flowlog.backend.menu.domain.model.MenuLang;
import com.flowlog.backend.menu.domain.repository.MenuLangRepository;
import com.flowlog.backend.menu.domain.repository.MenuRepository;
import com.flowlog.backend.menu.domain.service.dto.MenuAdminDtos.MenuContentResponse;
import com.flowlog.backend.menu.domain.service.dto.MenuTreeResponse.MenuPermission;
import com.flowlog.backend.menu.domain.service.dto.MenuTreeResponse;
import com.flowlog.backend.menu.exception.MenuErrorCode;
import com.flowlog.backend.role.domain.model.Role;
import com.flowlog.backend.role.domain.model.RoleMenu;
import com.flowlog.backend.role.domain.repository.RoleMenuRepository;
import com.flowlog.backend.role.domain.repository.RoleRepository;
import com.flowlog.backend.user.domain.model.User;
import com.flowlog.backend.user.domain.repository.UserRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메뉴 서비스.
 *
 * <p>사용자 메뉴 = 오늘 기준 유효 권한(직접 + 부서)의 메뉴 권한({@code TB_ROLE_MENU}, 조회권한 Y) 합산.
 * 시스템관리자는 전체 메뉴/전체 권한. 상위 메뉴 권한이 없으면 하위 메뉴도 노출하지 않는다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Logging
@Service("menuService")
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MenuService {

  /** 시스템관리자 권한코드. */
  public static final String SYS_ADMIN = "SYS_ADMIN";

  private static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final RoleMenuRepository roleMenuRepository;
  private final MenuRepository menuRepository;
  private final MenuLangRepository menuLangRepository;
  private final LangService langService;

  /**
   * 로그인 사용자 메뉴 트리.
   *
   * @param subject IdP 사용자 고유ID (미등록이면 빈 목록)
   * @param langCd 사용자 언어 (LangService.resolveUserLang)
   * @return 최상위 메뉴 목록 (정렬순서)
   */
  public List<MenuTreeResponse> getMyMenus(String subject, String langCd) {
    Optional<User> user = StringUtils.isBlank(subject) ? Optional.empty()
        : userRepository.findByIdpSubjectAndUseYn(subject, YesNo.Y);
    List<MenuTreeResponse> tree = List.of();
    if (user.isPresent()) {
      List<Role> roles =
          roleRepository.findEffectiveRoles(user.get().getUserId(), LocalDate.now(ZONE_ID));
      List<Menu> menus = menuRepository.findByUseYnOrderBySortOrderAscIdAsc(YesNo.Y).stream()
          .filter(menu -> YesNo.isYes(menu.getDisplayYn()))
          .toList();
      tree = buildTree(menus, permissionsOf(roles, menus), menuNames(langCd), null);
    }
    return tree;
  }

  /**
   * 본문형(CONTENT) 메뉴의 본문. 내 메뉴에 있는(조회 권한이 있는) 메뉴만.
   *
   * <p>표시 언어 본문이 없으면 기본 언어 본문 (fallback=true).
   *
   * @param subject IdP 사용자 고유ID
   * @param menuId 메뉴ID
   * @param langCd 표시 언어
   * @return 본문
   */
  public MenuContentResponse getContent(String subject, Long menuId, String langCd) {
    Optional<User> user = StringUtils.isBlank(subject) ? Optional.empty()
        : userRepository.findByIdpSubjectAndUseYn(subject, YesNo.Y);
    Menu menu = menuRepository.findById(menuId)
        .filter(found -> YesNo.isYes(found.getUseYn())
            && MenuAdminService.VIEW_CONTENT.equals(found.getViewTypeCd()))
        .orElseThrow(() -> new BusinessException(MenuErrorCode.CONTENT_NOT_FOUND, menuId));
    List<Role> roles = user.map(found ->
        roleRepository.findEffectiveRoles(found.getUserId(), LocalDate.now(ZONE_ID)))
        .orElse(List.of());
    if (!permissionsOf(roles, List.of(menu)).containsKey(menuId)) {
      throw new BusinessException(MenuErrorCode.CONTENT_NOT_FOUND, menuId);
    }
    Map<String, MenuLang> langs = menuLangRepository.findByIdMenuId(menuId).stream()
        .collect(Collectors.toMap(lang -> lang.getId().getLangCd(), lang -> lang));
    String defaultLang = langService.defaultLang();
    MenuLang requested = langs.get(langCd);
    MenuLang base = langs.get(defaultLang);
    boolean fallback = requested == null || StringUtils.isBlank(requested.getMenuContent());
    MenuLang shown = fallback && base != null ? base : requested;
    String menuNm = requested != null ? requested.getMenuNm()
        : base != null ? base.getMenuNm() : null;
    return new MenuContentResponse(menuId, menuNm,
        shown == null ? langCd : shown.getId().getLangCd(),
        fallback && shown != null && !shown.getId().getLangCd().equals(langCd),
        shown == null ? null : shown.getMenuContent());
  }

  private Map<Long, MenuPermission> permissionsOf(List<Role> roles, List<Menu> menus) {
    Map<Long, MenuPermission> permissions = new HashMap<>();
    if (roles.stream().anyMatch(role -> SYS_ADMIN.equals(role.getRoleCd()))) {
      menus.forEach(menu -> permissions.put(menu.getId(), MenuPermission.ALL));
    } else if (!roles.isEmpty()) {
      List<RoleMenu> roleMenus =
          roleMenuRepository.findByIdRoleIdIn(roles.stream().map(Role::getId).toList());
      for (RoleMenu roleMenu : roleMenus) {
        if (YesNo.isYes(roleMenu.getListYn())) {
          MenuPermission permission = new MenuPermission(true, YesNo.isYes(roleMenu.getAddYn()),
              YesNo.isYes(roleMenu.getEditYn()), YesNo.isYes(roleMenu.getDelYn()));
          permissions.merge(roleMenu.getId().getMenuId(), permission, MenuPermission::merge);
        }
      }
    }
    return permissions;
  }

  /** 메뉴명 : 사용자 언어 → 없으면 기본 언어. */
  private Map<Long, String> menuNames(String langCd) {
    String defaultLang = langService.defaultLang();
    Map<Long, String> names = menuLangRepository.findByIdLangCd(defaultLang).stream()
        .collect(Collectors.toMap(lang -> lang.getId().getMenuId(), MenuLang::getMenuNm,
            (a, b) -> a, HashMap::new));
    if (!defaultLang.equals(langCd)) {
      menuLangRepository.findByIdLangCd(langCd)
          .forEach(lang -> names.put(lang.getId().getMenuId(), lang.getMenuNm()));
    }
    return names;
  }

  private List<MenuTreeResponse> buildTree(
      List<Menu> menus, Map<Long, MenuPermission> permissions, Map<Long, String> names,
      Long parentId) {
    List<MenuTreeResponse> nodes = new ArrayList<>();
    for (Menu menu : menus) {
      boolean child = parentId == null ? menu.isRoot() : parentId.equals(menu.getParentMenuId());
      MenuPermission permission = permissions.get(menu.getId());
      if (child && permission != null) {
        nodes.add(new MenuTreeResponse(
            menu.getId(), names.get(menu.getId()), menu.getModuleCd(), menu.getViewTypeCd(),
            menu.getMenuUrl(), YesNo.isYes(menu.getNewWindowYn()), menu.isAdminMenu(),
            YesNo.isYes(menu.getSideMenuYn()), permission,
            buildTree(menus, permissions, names, menu.getId())));
      }
    }
    return nodes;
  }
}
