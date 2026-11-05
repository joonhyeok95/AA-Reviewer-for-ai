package com.flowlog.backend.code.domain.repository;

import com.flowlog.backend.code.domain.model.CodeGroupLang;
import com.flowlog.backend.code.domain.model.CodeGroupLangId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 공통코드 그룹 다국어 Repository.
 *
 * @author flowlog
 * @since 2026-11-05
 */
public interface CodeGroupLangRepository extends JpaRepository<CodeGroupLang, CodeGroupLangId> {

  /**
   * 그룹의 전체 언어 이름을 조회한다.
   *
   * @param groupCd 그룹코드
   * @return 다국어 목록
   */
  List<CodeGroupLang> findByIdGroupCd(String groupCd);

  /**
   * 특정 언어의 전체 그룹명을 조회한다.
   *
   * @param langCd 언어코드
   * @return 다국어 목록
   */
  List<CodeGroupLang> findByIdLangCd(String langCd);
}
