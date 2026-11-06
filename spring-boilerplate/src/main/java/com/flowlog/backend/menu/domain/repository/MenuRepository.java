package com.flowlog.backend.menu.domain.repository;

import com.flowlog.backend.menu.domain.model.Menu;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 메뉴 Repository.
 *
 * <p>메뉴는 변경이 적고 조회가 잦으므로 전체를 한 번 읽어 메모리에서 트리를 구성하는 방식을 기본으로 한다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
public interface MenuRepository extends JpaRepository<Menu, Long> {

  /**
   * 사용여부로 전체 메뉴를 조회한다. (트리 구성용)
   *
   * @param useYn 사용여부
   * @return 메뉴 목록 (정렬순서 순)
   */
  List<Menu> findByUseYnOrderBySortOrderAscIdAsc(String useYn);

  /**
   * 하위 메뉴 목록을 조회한다.
   *
   * @param parentMenuId 상위메뉴ID
   * @return 하위 메뉴 목록 (정렬순서 순)
   */
  List<Menu> findByParentMenuIdOrderBySortOrderAsc(Long parentMenuId);

  /**
   * 하위 메뉴 존재 여부. (삭제 전 체크)
   *
   * @param parentMenuId 상위메뉴ID
   * @return 존재 여부
   */
  boolean existsByParentMenuId(Long parentMenuId);

  /**
   * 전체 메뉴 (미사용 포함, 관리 화면).
   *
   * @return 메뉴 목록 (정렬순서, ID)
   */
  List<Menu> findAllByOrderBySortOrderAscIdAsc();
}
