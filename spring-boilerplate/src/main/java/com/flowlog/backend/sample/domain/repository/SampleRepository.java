package com.flowlog.backend.sample.domain.repository;

import com.flowlog.backend.sample.domain.model.Sample;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 샘플 Repository.
 *
 * @author flowlog
 * @since 2026-11-05
 */
public interface SampleRepository extends JpaRepository<Sample, Long> {

  /**
   * 샘플명 존재 여부.
   *
   * @param name 샘플명
   * @return 존재 여부
   */
  boolean existsByName(String name);

  /**
   * 자기 자신을 제외한 샘플명 존재 여부. (수정 시 중복 체크)
   *
   * @param name 샘플명
   * @param id 제외할 샘플 ID
   * @return 존재 여부
   */
  boolean existsByNameAndIdNot(String name, Long id);

  /**
   * 조건 검색. 조건값이 null 이면 해당 조건은 무시한다.
   *
   * @param name 샘플명 (부분 일치)
   * @param useYn 사용여부
   * @param pageable 페이징/정렬
   * @return 검색 결과
   */
  @Query("""
      select s from Sample s
       where (:name is null or s.name like concat('%', :name, '%'))
         and (:useYn is null or s.useYn = :useYn)
      """)
  Page<Sample> search(
      @Param("name") String name, @Param("useYn") String useYn, Pageable pageable);
}
