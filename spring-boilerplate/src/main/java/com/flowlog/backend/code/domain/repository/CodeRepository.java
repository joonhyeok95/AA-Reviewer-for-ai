package com.flowlog.backend.code.domain.repository;

import com.flowlog.backend.code.domain.model.Code;
import com.flowlog.backend.code.domain.model.CodeId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 공통코드 Repository.
 *
 * @author flowlog
 * @since 2026-11-05
 */
public interface CodeRepository extends JpaRepository<Code, CodeId> {

  /**
   * 그룹의 코드 목록을 정렬순서대로 조회한다.
   *
   * @param groupCd 그룹코드
   * @param useYn 사용여부
   * @return 코드 목록
   */
  List<Code> findByIdGroupCdAndUseYnOrderBySortOrderAsc(String groupCd, String useYn);

  /**
   * 코드 존재 여부. (공통코드 참조 컬럼 검증용)
   *
   * @param groupCd 그룹코드
   * @param code 코드
   * @param useYn 사용여부
   * @return 존재 여부
   */
  boolean existsByIdGroupCdAndIdCodeAndUseYn(String groupCd, String code, String useYn);
}
