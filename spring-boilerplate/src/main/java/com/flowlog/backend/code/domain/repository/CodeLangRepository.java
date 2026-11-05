package com.flowlog.backend.code.domain.repository;

import com.flowlog.backend.code.domain.model.CodeLang;
import com.flowlog.backend.code.domain.model.CodeLangId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 공통코드 다국어 Repository.
 *
 * @author flowlog
 * @since 2026-11-05
 */
public interface CodeLangRepository extends JpaRepository<CodeLang, CodeLangId> {

  /**
   * 그룹 내 코드의 특정 언어 이름을 조회한다.
   *
   * @param groupCd 그룹코드
   * @param langCd 언어코드
   * @return 다국어 목록
   */
  List<CodeLang> findByIdGroupCdAndIdLangCd(String groupCd, String langCd);

  /**
   * 코드의 전체 언어 이름을 조회한다.
   *
   * @param groupCd 그룹코드
   * @param code 코드
   * @return 다국어 목록
   */
  List<CodeLang> findByIdGroupCdAndIdCode(String groupCd, String code);
}
