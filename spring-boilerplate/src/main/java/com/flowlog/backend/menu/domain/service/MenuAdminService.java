package com.flowlog.backend.menu.domain.service;

import com.flowlog.backend.code.domain.repository.CodeRepository;
import com.flowlog.backend.code.domain.service.LangService;
import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.constant.YesNo;
import com.flowlog.backend.global.exception.BusinessException;
import com.flowlog.backend.global.util.HtmlSanitizer;
import com.flowlog.backend.menu.domain.model.Menu;
import com.flowlog.backend.menu.domain.model.MenuLang;
import com.flowlog.backend.menu.domain.repository.MenuLangRepository;
import com.flowlog.backend.menu.domain.repository.MenuRepository;
import com.flowlog.backend.menu.domain.service.dto.MenuAdminDtos.MenuAdminNode;
import com.flowlog.backend.menu.domain.service.dto.MenuAdminDtos.MenuDetailResponse;
import com.flowlog.backend.menu.domain.service.dto.MenuAdminDtos.MenuI18n;
import com.flowlog.backend.menu.domain.service.dto.MenuAdminDtos.MenuOrderRequest;
import com.flowlog.backend.menu.domain.service.dto.MenuAdminDtos.MenuSaveRequest;
import com.flowlog.backend.menu.exception.MenuErrorCode;
import com.flowlog.backend.role.domain.repository.RoleMenuRepository;
import com.flowlog.backend.role.domain.service.UserAuthorityService;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메뉴 관리 서비스. (트리 조회, 등록/수정/삭제, 순서 변경, 언어별 메뉴명·본문)
 *
 * <ul>
 *   <li>URL 은 유형별로 검증: MENU = / 로 시작하는 경로(묶음 메뉴는 비워도 됨), LINK·IFRAME = http(s),
 *       CONTENT = 없음 (본문은 언어별 HTML, 저장 시 정화)</li>
 *   <li>상위 메뉴는 자기 자신이나 하위 메뉴로 지정할 수 없다 (순환 방지)</li>
 *   <li>하위 메뉴가 있으면 삭제 불가. 삭제 시 권한별 메뉴 접근(TB_ROLE_MENU)도 함께 삭제</li>
 *   <li>메뉴의 권한코드·사용여부는 API 권한 계산에 쓰이므로 변경 시 권한 캐시 전체 삭제</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-06
 */
@Logging
@Service("menuAdminService")
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MenuAdminService {

  static final String VIEW_TYPE_GROUP = "MENU_VIEW_TYPE";
  static final String MODULE_GROUP = "MODULE";
  static final String VIEW_MENU = "MENU";
  static final String VIEW_CONTENT = "CONTENT";
  private static final Set<String> EXTERNAL_TYPES = Set.of("LINK", "IFRAME");
  private static final Pattern MENU_PATH = Pattern.compile("^/(?!/)[A-Za-z0-9/_.-]{0,999}$");

  private final MenuRepository menuRepository;
  private final MenuLangRepository menuLangRepository;
  private final RoleMenuRepository roleMenuRepository;
  private final CodeRepository codeRepository;
  private final LangService langService;

  /**
   * 전체 메뉴 트리 (미사용·미노출 포함).
   *
   * @param langCd 표시 언어
   * @return 메뉴 트리
   */
  public List<MenuAdminNode> getTree(String langCd) {
    List<Menu> menus = menuRepository.findAllByOrderBySortOrderAscIdAsc();
    Map<Long, Map<String, String>> names = allNames();
    Map<Long, List<Menu>> byParent = new HashMap<>();
    menus.forEach(menu -> byParent.computeIfAbsent(menu.getParentMenuId(), key -> new ArrayList<>())
        .add(menu));
    return buildTree(byParent, names, langCd, null);
  }

  /**
   * 메뉴 상세. (언어별 메뉴명·본문 포함)
   *
   * @param menuId 메뉴ID
   * @return 상세
   */
  public MenuDetailResponse getMenu(Long menuId) {
    return toDetail(findMenu(menuId));
  }

  /**
   * 메뉴 등록.
   *
   * @param request 요청
   * @return 등록된 메뉴
   */
  @Transactional
  @CacheEvict(cacheNames = UserAuthorityService.CACHE_NAME, allEntries = true)
  public MenuDetailResponse createMenu(MenuSaveRequest request) {
    validate(request, null);
    Menu menu = menuRepository.save(Menu.builder()
        .parentMenuId(request.parentMenuId())
        .moduleCd(StringUtils.trimToNull(request.moduleCd()))
        .viewTypeCd(request.viewTypeCd())
        .menuUrl(normalizeUrl(request))
        .permCd(StringUtils.trimToNull(request.permCd()))
        .newWindowYn(request.newWindowYn())
        .sortOrder(request.sortOrder())
        .adminMenuYn(request.adminMenuYn())
        .sideMenuYn(request.sideMenuYn())
        .displayYn(request.displayYn())
        .useYn(request.useYn())
        .build());
    saveLangs(menu.getId(), request);
    return toDetail(menu);
  }

  /**
   * 메뉴 수정. (상위 메뉴 이동 포함)
   *
   * @param menuId 메뉴ID
   * @param request 요청
   * @return 수정된 메뉴
   */
  @Transactional
  @CacheEvict(cacheNames = UserAuthorityService.CACHE_NAME, allEntries = true)
  public MenuDetailResponse updateMenu(Long menuId, MenuSaveRequest request) {
    Menu menu = findMenu(menuId);
    validate(request, menuId);
    menu.update(StringUtils.trimToNull(request.moduleCd()), request.viewTypeCd(),
        normalizeUrl(request), StringUtils.trimToNull(request.permCd()), request.newWindowYn(),
        request.adminMenuYn(), request.sideMenuYn(), request.displayYn(), request.useYn());
    menu.move(request.parentMenuId(), request.sortOrder());
    saveLangs(menuId, request);
    return toDetail(menu);
  }

  /**
   * 메뉴 삭제. (하위 메뉴가 있으면 불가, 권한별 메뉴 접근도 삭제)
   *
   * @param menuId 메뉴ID
   */
  @Transactional
  @CacheEvict(cacheNames = UserAuthorityService.CACHE_NAME, allEntries = true)
  public void deleteMenu(Long menuId) {
    Menu menu = findMenu(menuId);
    if (menuRepository.existsByParentMenuId(menuId)) {
      throw new BusinessException(MenuErrorCode.MENU_HAS_CHILDREN, menuId);
    }
    roleMenuRepository.deleteAll(roleMenuRepository.findByIdMenuId(menuId));
    menuLangRepository.deleteAll(menuLangRepository.findByIdMenuId(menuId));
    menuRepository.delete(menu);
  }

  /**
   * 같은 상위 메뉴 안의 순서 변경. (목록 순서대로 1, 2, 3 …)
   *
   * @param request 요청
   */
  @Transactional
  public void reorder(MenuOrderRequest request) {
    List<Menu> menus = menuRepository.findAllById(request.menuIds());
    Map<Long, Menu> byId = menus.stream().collect(Collectors.toMap(Menu::getId, menu -> menu));
    if (byId.size() != request.menuIds().size()
        || menus.stream().anyMatch(m -> !Objects.equals(m.getParentMenuId(),
            request.parentMenuId()))) {
      throw new BusinessException(MenuErrorCode.INVALID_ORDER);
    }
    for (int i = 0; i < request.menuIds().size(); i++) {
      Menu menu = byId.get(request.menuIds().get(i));
      menu.move(menu.getParentMenuId(), i + 1);
    }
  }

  private void validate(MenuSaveRequest request, Long menuId) {
    requireCode(VIEW_TYPE_GROUP, request.viewTypeCd());
    if (StringUtils.isNotBlank(request.moduleCd())) {
      requireCode(MODULE_GROUP, request.moduleCd().trim());
    }
    validateParent(request.parentMenuId(), menuId);
    validateUrl(request);
  }

  private void requireCode(String groupCd, String code) {
    if (!codeRepository.existsByIdGroupCdAndIdCodeAndUseYn(groupCd, code, YesNo.Y)) {
      throw new BusinessException(MenuErrorCode.INVALID_CODE, groupCd, code);
    }
  }

  /** 상위 메뉴 : 존재해야 하고, 자기 자신이나 자기 하위 메뉴면 안 된다. */
  private void validateParent(Long parentMenuId, Long menuId) {
    if (parentMenuId == null) {
      return;
    }
    Menu parent = findMenu(parentMenuId);
    Long cursor = parent.getId();
    Map<Long, Long> parents = menuRepository.findAll().stream()
        .filter(menu -> menu.getParentMenuId() != null)
        .collect(Collectors.toMap(Menu::getId, Menu::getParentMenuId));
    int guard = 0;
    while (cursor != null && guard++ < 1000) {
      if (cursor.equals(menuId)) {
        throw new BusinessException(MenuErrorCode.INVALID_PARENT, parentMenuId);
      }
      cursor = parents.get(cursor);
    }
  }

  private void validateUrl(MenuSaveRequest request) {
    String url = StringUtils.trimToNull(request.menuUrl());
    String type = request.viewTypeCd();
    boolean valid;
    if (VIEW_MENU.equals(type)) {
      valid = url == null || MENU_PATH.matcher(url).matches();
    } else if (EXTERNAL_TYPES.contains(type)) {
      valid = isHttpUrl(url);
    } else {
      valid = !VIEW_CONTENT.equals(type) || url == null;
    }
    if (!valid) {
      throw new BusinessException(MenuErrorCode.INVALID_URL, type);
    }
  }

  private static boolean isHttpUrl(String url) {
    if (url == null) {
      return false;
    }
    try {
      URI uri = new URI(url);
      return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
          && StringUtils.isNotBlank(uri.getHost());
    } catch (URISyntaxException e) {
      return false;
    }
  }

  private static String normalizeUrl(MenuSaveRequest request) {
    return VIEW_CONTENT.equals(request.viewTypeCd()) ? null
        : StringUtils.trimToNull(request.menuUrl());
  }

  /** 언어별 메뉴명·본문 저장 - 기존 행은 수정, 빠진 언어는 삭제. 본문은 CONTENT 유형만, 정화 후 저장. */
  private void saveLangs(Long menuId, MenuSaveRequest request) {
    Map<String, String> source = new LinkedHashMap<>();
    Map<String, String> contents = new HashMap<>();
    for (MenuI18n item : request.i18n()) {
      source.put(item.langCd(), item.menuNm());
      if (VIEW_CONTENT.equals(request.viewTypeCd()) && StringUtils.isNotBlank(item.menuContent())) {
        contents.put(item.langCd(), HtmlSanitizer.sanitize(item.menuContent()));
      }
    }
    Map<String, String> names = langService.normalizeNames(source);
    Map<String, MenuLang> existing = menuLangRepository.findByIdMenuId(menuId).stream()
        .collect(Collectors.toMap(lang -> lang.getId().getLangCd(), lang -> lang));
    names.forEach((langCd, name) -> {
      MenuLang lang = existing.remove(langCd);
      if (lang == null) {
        menuLangRepository.save(new MenuLang(menuId, langCd, name, contents.get(langCd)));
      } else {
        lang.update(name, contents.get(langCd));
      }
    });
    menuLangRepository.deleteAll(existing.entrySet().stream()
        .filter(entry -> langService.isActive(entry.getKey())).map(Map.Entry::getValue).toList());
  }

  private Map<Long, Map<String, String>> allNames() {
    Map<Long, Map<String, String>> names = new HashMap<>();
    for (MenuLang lang : menuLangRepository.findAll()) {
      names.computeIfAbsent(lang.getId().getMenuId(), key -> new LinkedHashMap<>())
          .put(lang.getId().getLangCd(), lang.getMenuNm());
    }
    return names;
  }

  private List<MenuAdminNode> buildTree(
      Map<Long, List<Menu>> byParent, Map<Long, Map<String, String>> names, String langCd,
      Long parentId) {
    List<MenuAdminNode> nodes = new ArrayList<>();
    for (Menu menu : byParent.getOrDefault(parentId, List.of())) {
      nodes.add(new MenuAdminNode(menu.getId(), menu.getParentMenuId(),
          langService.nameOf(names.getOrDefault(menu.getId(), Map.of()), langCd),
          menu.getModuleCd(), menu.getViewTypeCd(), menu.getMenuUrl(), menu.getPermCd(),
          menu.getSortOrder(), menu.getNewWindowYn(), menu.getAdminMenuYn(),
          menu.getSideMenuYn(), menu.getDisplayYn(), menu.getUseYn(),
          buildTree(byParent, names, langCd, menu.getId())));
    }
    return nodes;
  }

  private MenuDetailResponse toDetail(Menu menu) {
    List<MenuI18n> i18n = menuLangRepository.findByIdMenuId(menu.getId()).stream()
        .map(lang -> new MenuI18n(lang.getId().getLangCd(), lang.getMenuNm(),
            lang.getMenuContent()))
        .toList();
    return new MenuDetailResponse(menu.getId(), menu.getParentMenuId(), menu.getModuleCd(),
        menu.getViewTypeCd(), menu.getMenuUrl(), menu.getPermCd(), menu.getSortOrder(),
        menu.getNewWindowYn(), menu.getAdminMenuYn(), menu.getSideMenuYn(), menu.getDisplayYn(),
        menu.getUseYn(), i18n, roleMenuRepository.findByIdMenuId(menu.getId()).size(),
        menuRepository.findByParentMenuIdOrderBySortOrderAsc(menu.getId()).size());
  }

  private Menu findMenu(Long menuId) {
    return menuRepository.findById(menuId)
        .orElseThrow(() -> new BusinessException(MenuErrorCode.MENU_NOT_FOUND, menuId));
  }
}
