package com.flowlog.backend.calendar.domain.repository;

import com.flowlog.backend.calendar.domain.model.CalendarEvent;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 캘린더 일정 Repository.
 *
 * @author flowlog
 * @since 2026-11-07
 */
public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

  /**
   * 기간 조회 대상 일정.
   *
   * <ul>
   *   <li>반복 안 함 : 기간과 겹치는 일정 (시작일 &lt;= to, 종료일 &gt;= from)</li>
   *   <li>매년 반복 : 기간 종료일 이전에 시작한 일정 전체 (해마다 펼치는 것은 서비스에서)</li>
   * </ul>
   *
   * @param from 조회 시작일
   * @param to 조회 종료일
   * @return 일정 (시작일, ID 순)
   */
  @Query("""
      select e from CalendarEvent e
       where e.startDt <= :to
         and (e.repeatYearlyYn = 'Y' or e.endDt >= :from)
       order by e.startDt, e.id
      """)
  List<CalendarEvent> findCandidates(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
