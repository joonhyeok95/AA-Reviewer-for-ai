package com.flowlog.backend.calendar.domain.model;

import com.flowlog.backend.global.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 캘린더 일정 Entity. (TB_CAL_EVENT)
 *
 * <p>매년 반복 일정은 원본 1건만 저장하고, 조회 시 기간 안의 해마다 펼친다.
 * ({@link com.flowlog.backend.calendar.domain.service.CalendarOccurrences})
 *
 * @author flowlog
 * @since 2026-11-07
 */
@Getter
@Entity
@Table(name = "TB_CAL_EVENT")
@SequenceGenerator(name = "SQ_CAL_EVENT_GEN", sequenceName = "SQ_CAL_EVENT", allocationSize = 1)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CalendarEvent extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SQ_CAL_EVENT_GEN")
  @Column(name = "EVENT_ID")
  private Long id; // 일정 ID

  @Column(name = "EVENT_TYPE_CD", length = 30, nullable = false)
  private String eventTypeCd; // 일정 유형 (공통코드 CAL_EVENT_TYPE)

  @Column(name = "TITLE", length = 200, nullable = false)
  private String title; // 제목

  @Column(name = "START_DT", nullable = false)
  private LocalDate startDt; // 시작일 (매년 반복이면 최초 일자)

  @Column(name = "END_DT", nullable = false)
  private LocalDate endDt; // 종료일

  @Column(name = "REPEAT_YEARLY_YN", length = 1, nullable = false)
  private String repeatYearlyYn; // 매년 반복 여부 (Y/N)

  @Column(name = "MEMO", length = 1000)
  private String memo; // 메모

  @Builder
  private CalendarEvent(String eventTypeCd, String title, LocalDate startDt, LocalDate endDt,
      String repeatYearlyYn, String memo) {
    this.eventTypeCd = eventTypeCd;
    this.title = title;
    this.startDt = startDt;
    this.endDt = endDt;
    this.repeatYearlyYn = repeatYearlyYn;
    this.memo = memo;
  }

  /**
   * 일정을 수정한다.
   *
   * @param eventTypeCd 일정 유형
   * @param title 제목
   * @param startDt 시작일
   * @param endDt 종료일
   * @param repeatYearlyYn 매년 반복 여부
   * @param memo 메모
   */
  public void update(String eventTypeCd, String title, LocalDate startDt, LocalDate endDt,
      String repeatYearlyYn, String memo) {
    this.eventTypeCd = eventTypeCd;
    this.title = title;
    this.startDt = startDt;
    this.endDt = endDt;
    this.repeatYearlyYn = repeatYearlyYn;
    this.memo = memo;
  }
}
