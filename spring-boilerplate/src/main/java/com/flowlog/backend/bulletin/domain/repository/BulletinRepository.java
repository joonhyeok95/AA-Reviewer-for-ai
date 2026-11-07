package com.flowlog.backend.bulletin.domain.repository;

import com.flowlog.backend.bulletin.domain.model.Bulletin;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 주보 Repository.
 *
 * @author flowlog
 * @since 2026-11-08
 */
public interface BulletinRepository extends JpaRepository<Bulletin, Long> {

  /**
   * 주보 목록. (상태 조건이 null 이면 전체)
   *
   * @param statusCd 상태 (null = 전체)
   * @param pageable 페이징 (정렬은 예배일 역순 고정)
   * @return 주보
   */
  @Query("""
      select b from Bulletin b
       where (:statusCd is null or b.statusCd = :statusCd)
       order by b.worshipDt desc
      """)
  Page<Bulletin> search(@Param("statusCd") String statusCd, Pageable pageable);

  /**
   * 예배일 중복 여부.
   *
   * @param worshipDt 예배일
   * @return 존재 여부
   */
  boolean existsByWorshipDt(LocalDate worshipDt);

  /**
   * 다른 주보에 같은 예배일이 있는지. (수정 시)
   *
   * @param worshipDt 예배일
   * @param id 제외할 주보 ID
   * @return 존재 여부
   */
  boolean existsByWorshipDtAndIdNot(LocalDate worshipDt, Long id);

  /**
   * 기간 안의 가장 이른 주보 (이번 주 주보).
   *
   * @param statusCd 상태
   * @param from 시작일
   * @param to 종료일
   * @return 주보
   */
  Optional<Bulletin> findFirstByStatusCdAndWorshipDtBetweenOrderByWorshipDtAsc(
      String statusCd, LocalDate from, LocalDate to);

  /**
   * 기준일 이전 가장 최근 주보.
   *
   * @param statusCd 상태
   * @param date 기준일
   * @return 주보
   */
  Optional<Bulletin> findFirstByStatusCdAndWorshipDtLessThanEqualOrderByWorshipDtDesc(
      String statusCd, LocalDate date);
}
