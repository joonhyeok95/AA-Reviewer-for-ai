package com.flowlog.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowlog.backend.code.domain.model.Code;
import com.flowlog.backend.code.domain.model.CodeGroup;
import com.flowlog.backend.code.domain.model.CodeGroupLang;
import com.flowlog.backend.code.domain.model.CodeId;
import com.flowlog.backend.code.domain.model.CodeLang;
import com.flowlog.backend.code.domain.repository.CodeGroupLangRepository;
import com.flowlog.backend.code.domain.repository.CodeGroupRepository;
import com.flowlog.backend.code.domain.repository.CodeLangRepository;
import com.flowlog.backend.code.domain.repository.CodeRepository;
import com.flowlog.backend.global.constant.DateConstants;
import com.flowlog.backend.global.constant.YesNo;
import com.flowlog.backend.menu.domain.model.Menu;
import com.flowlog.backend.menu.domain.model.MenuLang;
import com.flowlog.backend.menu.domain.model.MenuLangId;
import com.flowlog.backend.menu.domain.repository.MenuLangRepository;
import com.flowlog.backend.menu.domain.repository.MenuRepository;
import com.flowlog.backend.role.domain.model.DeptRole;
import com.flowlog.backend.role.domain.model.Role;
import com.flowlog.backend.role.domain.model.RoleMenu;
import com.flowlog.backend.role.domain.model.UserRole;
import com.flowlog.backend.role.domain.repository.DeptRoleRepository;
import com.flowlog.backend.role.domain.repository.RoleLangRepository;
import com.flowlog.backend.role.domain.repository.RoleMenuRepository;
import com.flowlog.backend.role.domain.repository.RoleRepository;
import com.flowlog.backend.role.domain.repository.UserRoleRepository;
import com.flowlog.backend.user.domain.model.UserDept;
import com.flowlog.backend.user.domain.repository.DeptRepository;
import com.flowlog.backend.user.domain.repository.UserDeptRepository;
import com.flowlog.backend.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공통코드 / 사용자·부서 / 권한 / 메뉴 Entity-Repository 통합 테스트.
 *
 * <p>DDL(db/ddl) 과 Entity 매핑 일치, 초기 데이터(db/data/01_auth_menu_code.sql), 핵심 조회를 검증한다.
 * 기준일은 결과가 날짜에 따라 바뀌지 않도록 2026-11-05 로 고정한다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@SpringBootTest
@Transactional
class AuthMenuCodeRepositoryTest {

  private static final LocalDate BASE_DATE = LocalDate.of(2026, 11, 5);

  @Autowired private EntityManager em;
  @Autowired private CodeGroupRepository codeGroupRepository;
  @Autowired private CodeGroupLangRepository codeGroupLangRepository;
  @Autowired private CodeRepository codeRepository;
  @Autowired private CodeLangRepository codeLangRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private DeptRepository deptRepository;
  @Autowired private UserDeptRepository userDeptRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private RoleLangRepository roleLangRepository;
  @Autowired private UserRoleRepository userRoleRepository;
  @Autowired private DeptRoleRepository deptRoleRepository;
  @Autowired private RoleMenuRepository roleMenuRepository;
  @Autowired private MenuRepository menuRepository;
  @Autowired private MenuLangRepository menuLangRepository;

  private List<String> effectiveRoleCodes(String userId, LocalDate baseDate) {
    return roleRepository.findEffectiveRoles(userId, baseDate).stream()
        .map(Role::getRoleCd)
        .toList();
  }

  @Nested
  @DisplayName("공통코드")
  class CodeTest {

    @Test
    @DisplayName("초기 코드그룹 10개(공지 포함, WORKPLACE 그룹 없음)와 MODULE 코드(TX, WS)를 정렬순서대로 조회")
    void findCodes() {
      assertThat(codeGroupRepository.findByUseYnOrderBySortOrderAsc(YesNo.Y))
          .extracting(CodeGroup::getGroupCd)
          .containsExactly("LANG", "MODULE", "ROLE_TYPE", "USER_TYPE", "MENU_VIEW_TYPE",
              "NOTICE_CATEGORY", "NOTICE_TAG", "MAIL_SEND_TYPE", "MAIL_TARGET_TYPE", "MAIL_STATUS");

      assertThat(codeRepository.findByIdGroupCdAndUseYnOrderBySortOrderAsc("MODULE", YesNo.Y))
          .extracting(code -> code.getId().getCode())
          .containsExactly("TX", "WS");
    }

    @Test
    @DisplayName("코드명 다국어 - USER_TYPE 의 en 이름")
    void findCodeLang() {
      Map<String, String> names = codeLangRepository
          .findByIdGroupCdAndIdLangCd("USER_TYPE", "en").stream()
          .collect(Collectors.toMap(l -> l.getId().getCode(), CodeLang::getCodeNm));

      assertThat(names).hasSize(5).containsEntry("PARTNER", "Partner");
    }

    @Test
    @DisplayName("자연키 Entity 신규 저장 - SELECT 없이 INSERT, Auditing 값 세팅")
    void saveNewNaturalKeyEntity() {
      // given
      CodeGroup group = CodeGroup.builder()
          .groupCd("NEW_GROUP").sortOrder(99).systemYn(YesNo.N).useYn(YesNo.Y).build();

      // when
      codeGroupRepository.save(group);
      codeGroupLangRepository.save(new CodeGroupLang("NEW_GROUP", "ko", "신규그룹"));
      codeRepository.save(Code.builder()
          .groupCd("NEW_GROUP").code("A").sortOrder(1).useYn(YesNo.Y).build());
      em.flush();
      em.clear();

      // then
      CodeGroup saved = codeGroupRepository.findById("NEW_GROUP").orElseThrow();
      assertThat(saved.getCreatedBy()).isEqualTo("system");
      assertThat(saved.getCreatedAt()).isNotNull();
      assertThat(codeRepository.existsByIdGroupCdAndIdCodeAndUseYn("NEW_GROUP", "A", YesNo.Y))
          .isTrue();
    }

    @Test
    @DisplayName("FK 제약 - 존재하지 않는 그룹의 코드는 저장 불가")
    void codeWithoutGroup_violatesForeignKey() {
      codeRepository.save(Code.builder()
          .groupCd("NO_GROUP").code("X").sortOrder(1).useYn(YesNo.Y).build());

      assertThatThrownBy(() -> codeRepository.flush())
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  @Nested
  @DisplayName("사용자 / 부서")
  class UserDeptTest {

    @Test
    @DisplayName("사용자는 N개 부서에 소속 - tx.viewer: WS_TEAM(주), TF_TEAM")
    void userHasMultipleDepts() {
      List<UserDept> depts = userDeptRepository.findByIdUserId("tx.viewer");

      assertThat(depts).extracting(d -> d.getId().getDeptCd())
          .containsExactlyInAnyOrder("WS_TEAM", "TF_TEAM");
      assertThat(depts).filteredOn(d -> YesNo.isYes(d.getMainDeptYn()))
          .extracting(d -> d.getId().getDeptCd())
          .containsExactly("WS_TEAM");
    }

    @Test
    @DisplayName("부서 계층 - HQ 하위 3개 부서")
    void deptHierarchy() {
      assertThat(deptRepository.findByParentDeptCdOrderBySortOrderAsc("HQ"))
          .hasSize(3);
      assertThat(userRepository.findById("partner01").orElseThrow().getCompanyNm())
          .isEqualTo("협력사A");
    }
  }

  @Nested
  @DisplayName("권한")
  class RoleTest {

    @Test
    @DisplayName("레벨 모델 - 시스템관리자 1, 모듈마스터/매니저는 모듈별, 일반사용자 5종 + 게시판 작성자·열람자")
    void roleLevels() {
      assertThat(roleRepository.findByRoleCd("SYS_ADMIN").orElseThrow().isSystemAdmin()).isTrue();
      assertThat(roleRepository.findByModuleCdOrderByRoleLevelAscSortOrderAsc("TX"))
          .extracting(Role::getRoleCd)
          .containsExactly("TX_MASTER", "TX_MANAGER", "TX_MANAGER_READ");
      assertThat(roleRepository.findAll())
          .filteredOn(r -> r.getRoleLevel() == Role.LEVEL_GENERAL_USER)
          .hasSize(7);
    }

    @Test
    @DisplayName("유효 권한 - 직접 부여 + 부서 부여 합산 (tx.viewer: TF 부서의 조회매니저 + 임직원)")
    void effectiveRoles_userAndDept() {
      assertThat(effectiveRoleCodes("tx.viewer", BASE_DATE))
          .containsExactly("TX_MANAGER_READ", "USER_EMP");
      assertThat(effectiveRoleCodes("admin", BASE_DATE))
          .containsExactly("SYS_ADMIN", "USER_EMP");
    }

    @Test
    @DisplayName("유효 권한 - 만료(2025년)/시작 전(2099년) 권한은 제외")
    void effectiveRoles_excludesExpiredAndFuture() {
      assertThat(userRoleRepository.findByUserIdOrderByStartDtDesc("emp01")).hasSize(2);
      assertThat(effectiveRoleCodes("emp01", BASE_DATE)).containsExactly("USER_EMP");
    }

    @Test
    @DisplayName("유효 권한 - 기준일이 바뀌면 기간 만료된 권한이 빠짐 (2027-01-01)")
    void effectiveRoles_afterPeriodEnds() {
      LocalDate nextYear = LocalDate.of(2027, 1, 1);

      assertThat(effectiveRoleCodes("tx.viewer", nextYear)).containsExactly("USER_EMP");
      assertThat(effectiveRoleCodes("partner01", nextYear)).isEmpty();
    }

    @Test
    @DisplayName("권한 부여 저장 - 종료일 미지정 시 무기한(9999-12-31), 시퀀스는 고정 ID(1~16) 이후 101부터")
    void grantRole_defaultEndDate() {
      // given
      Long roleId = roleRepository.findByRoleCd("WS_MANAGER").orElseThrow().getId();

      // when
      UserRole saved = userRoleRepository.saveAndFlush(UserRole.builder()
          .userId("emp01").roleId(roleId).startDt(BASE_DATE).build());
      DeptRole deptSaved = deptRoleRepository.saveAndFlush(DeptRole.builder()
          .deptCd("TX_TEAM").roleId(roleId).startDt(BASE_DATE).endDt(BASE_DATE).build());

      // then
      assertThat(saved.getId()).isGreaterThan(100L); // 시퀀스는 롤백되지 않으므로 범위로 검증
      assertThat(saved.getEndDt()).isEqualTo(DateConstants.MAX_END_DATE);
      assertThat(deptSaved.isValidOn(BASE_DATE)).isTrue();
      assertThat(deptSaved.isValidOn(BASE_DATE.plusDays(1))).isFalse();
      assertThat(effectiveRoleCodes("emp01", BASE_DATE)).contains("WS_MANAGER");
    }

    @Test
    @DisplayName("권한 부여 - 시작일이 종료일보다 늦으면 생성 불가")
    void grantRole_invalidPeriod() {
      assertThatThrownBy(() -> UserRole.builder()
          .userId("emp01").roleId(1L)
          .startDt(BASE_DATE).endDt(BASE_DATE.minusDays(1)).build())
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("권한명 다국어")
    void roleLang() {
      Long roleId = roleRepository.findByRoleCd("TX_MANAGER_READ").orElseThrow().getId();

      assertThat(roleLangRepository.findByIdRoleIdInAndIdLangCd(List.of(roleId), "en"))
          .singleElement()
          .satisfies(l -> assertThat(l.getRoleNm()).isEqualTo("TX Manager (Read-only)"));
    }
  }

  @Nested
  @DisplayName("메뉴 / 메뉴권한")
  class MenuTest {

    @Test
    @DisplayName("메뉴 트리 - 전체 조회 후 메모리 구성, 4뎁스 (TX > 거래관리 > 거래목록 > 거래상세)")
    void buildMenuTree() {
      // given
      List<Menu> menus = menuRepository.findByUseYnOrderBySortOrderAscIdAsc(YesNo.Y);
      Map<Long, String> names = menuLangRepository.findByIdLangCd("ko").stream()
          .collect(Collectors.toMap(l -> l.getId().getMenuId(), MenuLang::getMenuNm));
      Map<Long, Menu> byId = menus.stream()
          .collect(Collectors.toMap(Menu::getId, Function.identity()));

      // when - 최하위(거래상세, id=4)에서 루트까지 경로
      List<String> path = new ArrayList<>();
      Menu current = byId.get(4L);
      while (current != null) {
        path.add(0, names.get(current.getId()));
        current = current.isRoot() ? null : byId.get(current.getParentMenuId());
      }

      // then
      assertThat(menus).hasSize(17);
      assertThat(path).containsExactly("TX", "거래관리", "거래목록", "거래상세");
      assertThat(menuRepository.findByParentMenuIdOrderBySortOrderAsc(2L))
          .extracting(Menu::getMenuUrl)
          .containsExactly("/tx/trades/list", "/tx/trades/admin");
    }

    @Test
    @DisplayName("메뉴 속성 - 관리자메뉴, 사이드메뉴, 미노출, 조회유형")
    void menuAttributes() {
      Map<Long, Menu> byId = new HashMap<>();
      menuRepository.findAll().forEach(m -> byId.put(m.getId(), m));

      assertThat(byId.get(5L).isAdminMenu()).isTrue();
      assertThat(byId.get(4L).getDisplayYn()).isEqualTo(YesNo.N);
      assertThat(byId.values()).filteredOn(m -> YesNo.isYes(m.getSideMenuYn()))
          .extracting(Menu::getViewTypeCd)
          .containsExactlyInAnyOrder("MENU", "LINK", "MENU");
      assertThat(byId.get(10L).getViewTypeCd()).isEqualTo("IFRAME");
    }

    @Test
    @DisplayName("본문(CONTENT) 메뉴 - 언어별 CLOB 본문 조회")
    void contentMenu() {
      MenuLang en = menuLangRepository.findById(new MenuLangId(6L, "en")).orElseThrow();

      assertThat(en.getMenuNm()).isEqualTo("TX Guide");
      assertThat(en.getMenuContent()).contains("How to use the TX module");
    }

    @Test
    @DisplayName("신규 메뉴 저장 - 시퀀스 101부터, 하위메뉴 존재 여부")
    void saveMenu() {
      Menu saved = menuRepository.saveAndFlush(Menu.builder()
          .parentMenuId(9L).moduleCd("WS").viewTypeCd("MENU").menuUrl("/ws/status/list/sub")
          .newWindowYn(YesNo.N).sortOrder(1).adminMenuYn(YesNo.N).sideMenuYn(YesNo.N)
          .displayYn(YesNo.Y).useYn(YesNo.Y).build());
      menuLangRepository.saveAndFlush(new MenuLang(saved.getId(), "ko", "4뎁스 메뉴", null));

      assertThat(saved.getId()).isGreaterThan(100L); // 시퀀스는 롤백되지 않으므로 범위로 검증
      assertThat(menuRepository.existsByParentMenuId(9L)).isTrue();
    }

    @Test
    @DisplayName("메뉴권한 - 조회매니저는 TX 메뉴 조회만, 매니저는 삭제 제외")
    void roleMenuAuthority() {
      Long readRoleId = roleRepository.findByRoleCd("TX_MANAGER_READ").orElseThrow().getId();
      Long managerRoleId = roleRepository.findByRoleCd("TX_MANAGER").orElseThrow().getId();

      List<RoleMenu> readMenus = roleMenuRepository.findByIdRoleIdIn(List.of(readRoleId));
      assertThat(readMenus).hasSize(5).allSatisfy(rm -> {
        assertThat(rm.getListYn()).isEqualTo(YesNo.Y);
        assertThat(List.of(rm.getAddYn(), rm.getEditYn(), rm.getDelYn()))
            .containsOnly(YesNo.N);
      });

      assertThat(roleMenuRepository.findByIdRoleIdIn(List.of(managerRoleId)))
          .allSatisfy(rm -> assertThat(rm.getDelYn()).isEqualTo(YesNo.N));
    }

    @Test
    @DisplayName("메뉴권한 합산 - 사용자 유효 권한들의 메뉴권한을 OR 로 합산")
    void mergeRoleMenus() {
      // given - tx.viewer 의 유효 권한: TX_MANAGER_READ + USER_EMP
      List<Long> roleIds = roleRepository.findEffectiveRoles("tx.viewer", BASE_DATE).stream()
          .map(Role::getId)
          .toList();

      // when - 메뉴별 조회권한 OR 합산
      Map<Long, Boolean> listable = roleMenuRepository.findByIdRoleIdIn(roleIds).stream()
          .collect(Collectors.toMap(
              rm -> rm.getId().getMenuId(),
              rm -> YesNo.isYes(rm.getListYn()),
              Boolean::logicalOr));

      // then - TX 메뉴(조회매니저) + WS/사이드 메뉴(임직원), 관리자메뉴(5)와 시스템관리(11~14)는 없음
      assertThat(listable.keySet())
          .contains(1L, 2L, 3L, 4L, 6L, 7L, 8L, 9L, 10L, 15L, 16L)
          .doesNotContain(5L, 11L, 12L, 13L, 14L);
    }
  }

  @Test
  @DisplayName("FK 제약 - 존재하지 않는 사용자에게 권한 부여 불가")
  void grantToUnknownUser_violatesForeignKey() {
    userRoleRepository.save(UserRole.builder()
        .userId("unknown").roleId(1L).startDt(BASE_DATE).build());

    assertThatThrownBy(() -> userRoleRepository.flush())
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("CodeId equals/hashCode - 복합키 비교")
  void compositeKeyEquality() {
    assertThat(new CodeId("MODULE", "TX")).isEqualTo(new CodeId("MODULE", "TX"));
    assertThat(codeRepository.findById(new CodeId("MODULE", "TX"))).isPresent();
  }
}
