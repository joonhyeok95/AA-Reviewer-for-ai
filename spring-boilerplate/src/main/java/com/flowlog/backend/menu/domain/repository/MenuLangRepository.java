package com.flowlog.backend.menu.domain.repository;

import com.flowlog.backend.menu.domain.model.MenuLang;
import com.flowlog.backend.menu.domain.model.MenuLangId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 메뉴 다국어 Repository.
 *
 * @author flowlog
 * @since 2026-11-05
 */
public interface MenuLangRepository extends JpaRepository<MenuLang, MenuLangId> {

  /**
   * 특정 언어의 전체 메뉴명을 조회한다. (트리 구성용)
   *
   * @param langCd 언어코드
   * @return 다국어 목록
   */
  List<MenuLang> findByIdLangCd(String langCd);

  /**
   * 메뉴의 전체 언어 정보를 조회한다.
   *
   * @param menuId 메뉴ID
   * @return 다국어 목록
   */
  List<MenuLang> findByIdMenuId(Long menuId);
}
