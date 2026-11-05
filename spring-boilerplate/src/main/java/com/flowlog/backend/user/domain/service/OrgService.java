package com.flowlog.backend.user.domain.service;

import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.constant.YesNo;
import com.flowlog.backend.user.domain.model.Dept;
import com.flowlog.backend.user.domain.model.User;
import com.flowlog.backend.user.domain.model.Workplace;
import com.flowlog.backend.user.domain.repository.DeptRepository;
import com.flowlog.backend.user.domain.repository.UserRepository;
import com.flowlog.backend.user.domain.repository.WorkplaceRepository;
import com.flowlog.backend.user.domain.service.dto.OrgTreeResponse;
import com.flowlog.backend.user.domain.service.dto.OrgTreeResponse.DeptNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인사 마스터 조직 서비스. (사업장 / 부서 조직도 / 소속 사용자)
 *
 * <ul>
 *   <li>업무 테이블에는 사업장/부서 코드만 저장하고, 명칭은 이 서비스로 조회 시점에 가져온다.</li>
 *   <li>명칭 언어 : ko = 국문, 그 외 = 영문명(없으면 국문)</li>
 *   <li>수신자 추출은 호출 시점의 인사 마스터 기준 (예약 발송 사이의 인사이동 반영)</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Logging
@Service("orgService")
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrgService {

  /** 메일 대상 유형 : 사업장. */
  public static final String TARGET_WORKPLACE = "WORKPLACE";

  /** 메일 대상 유형 : 부서. */
  public static final String TARGET_DEPT = "DEPT";

  private final WorkplaceRepository workplaceRepository;
  private final DeptRepository deptRepository;
  private final UserRepository userRepository;

  /**
   * 조직도 트리. (사업장 → 부서 트리)
   *
   * <p>상위 부서가 다른 사업장이면 해당 사업장의 최상위로 표시한다.
   *
   * @param langCd 명칭 언어
   * @return 사업장별 부서 트리
   */
  public List<OrgTreeResponse> getOrgTree(String langCd) {
    List<Dept> depts = activeDepts();
    Map<String, Dept> byCode = depts.stream()
        .collect(Collectors.toMap(Dept::getDeptCd, Function.identity()));
    return workplaceRepository.findByUseYnOrderBySortOrderAsc(YesNo.Y).stream()
        .map(workplace -> new OrgTreeResponse(workplace.getWorkplaceCd(),
            workplace.nameOf(langCd),
            depts.stream()
                .filter(dept -> workplace.getWorkplaceCd().equals(dept.getWorkplaceCd()))
                .filter(dept -> isRootIn(dept, byCode))
                .map(dept -> toNode(dept, depts, langCd))
                .toList()))
        .toList();
  }

  /**
   * 사업장 존재(사용 중) 여부.
   *
   * @param workplaceCd 사업장코드
   * @return 존재하면 true
   */
  public boolean existsWorkplace(String workplaceCd) {
    return workplaceRepository.findById(workplaceCd)
        .filter(workplace -> YesNo.isYes(workplace.getUseYn())).isPresent();
  }

  /**
   * 부서 존재(사용 중) 여부.
   *
   * @param deptCd 부서코드
   * @return 존재하면 true
   */
  public boolean existsDept(String deptCd) {
    return deptRepository.findById(deptCd).filter(dept -> YesNo.isYes(dept.getUseYn())).isPresent();
  }

  /**
   * 사업장명 맵. (코드 → 언어별 명칭)
   *
   * @param langCd 명칭 언어
   * @return 사업장코드 → 명칭
   */
  public Map<String, String> workplaceNames(String langCd) {
    return workplaceRepository.findAll().stream()
        .collect(Collectors.toMap(Workplace::getWorkplaceCd, w -> w.nameOf(langCd)));
  }

  /**
   * 부서명 맵. (코드 → 언어별 명칭)
   *
   * @param langCd 명칭 언어
   * @return 부서코드 → 명칭
   */
  public Map<String, String> deptNames(String langCd) {
    return deptRepository.findAll().stream()
        .collect(Collectors.toMap(Dept::getDeptCd, dept -> dept.nameOf(langCd)));
  }

  /**
   * 대상 부서 코드 확장. (사용 중인 부서만)
   *
   * <ul>
   *   <li>WORKPLACE : 해당 사업장 소속 전체 부서</li>
   *   <li>DEPT : 해당 부서 (+ includeSub 이면 하위 부서 전체)</li>
   * </ul>
   *
   * @param targetType 대상 유형
   * @param targetCd 대상 코드
   * @param includeSub 하위 부서 포함 (DEPT)
   * @return 부서코드 집합
   */
  public Set<String> expandDepts(String targetType, String targetCd, boolean includeSub) {
    List<Dept> depts = activeDepts();
    Set<String> result = new LinkedHashSet<>();
    if (TARGET_WORKPLACE.equals(targetType)) {
      depts.stream().filter(dept -> targetCd.equals(dept.getWorkplaceCd()))
          .forEach(dept -> result.add(dept.getDeptCd()));
    } else if (TARGET_DEPT.equals(targetType)
        && depts.stream().anyMatch(dept -> dept.getDeptCd().equals(targetCd))) {
      result.add(targetCd);
      if (includeSub) {
        Deque<String> queue = new ArrayDeque<>(List.of(targetCd));
        while (!queue.isEmpty()) {
          String parent = queue.poll();
          depts.stream().filter(dept -> parent.equals(dept.getParentDeptCd()))
              .filter(dept -> result.add(dept.getDeptCd()))
              .forEach(dept -> queue.add(dept.getDeptCd()));
        }
      }
    }
    return result;
  }

  /**
   * 부서 소속 사용자 (현재 인사 마스터 기준, 겸직 포함, 사용 중인 사용자).
   *
   * @param deptCds 부서코드
   * @return 사용자 목록
   */
  public List<User> usersInDepts(Set<String> deptCds) {
    return deptCds.isEmpty() ? List.of() : userRepository.findActiveUsersInDepts(deptCds);
  }

  private List<Dept> activeDepts() {
    return deptRepository.findByUseYnOrderBySortOrderAscDeptCdAsc(YesNo.Y);
  }

  private static boolean isRootIn(Dept dept, Map<String, Dept> byCode) {
    Dept parent = dept.getParentDeptCd() == null ? null : byCode.get(dept.getParentDeptCd());
    return parent == null || !Objects.equals(parent.getWorkplaceCd(), dept.getWorkplaceCd());
  }

  private static DeptNode toNode(Dept dept, List<Dept> depts, String langCd) {
    List<DeptNode> children = new ArrayList<>();
    for (Dept child : depts) {
      if (dept.getDeptCd().equals(child.getParentDeptCd())
          && Objects.equals(dept.getWorkplaceCd(), child.getWorkplaceCd())) {
        children.add(toNode(child, depts, langCd));
      }
    }
    return new DeptNode(dept.getDeptCd(), dept.nameOf(langCd), dept.getWorkplaceCd(), children);
  }
}
