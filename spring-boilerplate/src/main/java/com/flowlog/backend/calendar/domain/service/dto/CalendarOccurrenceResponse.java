package com.flowlog.backend.calendar.domain.service.dto;

import com.flowlog.backend.calendar.domain.model.CalendarEvent;
import com.flowlog.backend.calendar.domain.service.CalendarOccurrences.Occurrence;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 기간 안의 일정 발생 1건. (달력·목록 표시용, 매년 반복은 해마다 1건)
 *
 * @param eventId 일정 ID (수정·삭제 대상)
 * @param eventTypeCd 일정 유형
 * @param title 제목
 * @param startDt 이번 발생 시작일
 * @param endDt 이번 발생 종료일
 * @param repeatYearlyYn 매년 반복 여부
 * @param yearsSince 최초 일자부터 지난 햇수 (생일 = 나이, 결혼기념일 = 주년, 반복 안 함 0)
 * @param originStartDt 최초 시작일 (원본)
 * @param memo 메모
 * @author flowlog
 * @since 2026-11-07
 */
@Schema(description = "일정 발생")
public record CalendarOccurrenceResponse(
    @Schema(description = "일정 ID", example = "1") Long eventId,
    @Schema(description = "일정 유형", example = "BIRTHDAY") String eventTypeCd,
    @Schema(description = "제목", example = "홍길동 생일") String title,
    @Schema(description = "이번 발생 시작일", example = "2026-11-12") LocalDate startDt,
    @Schema(description = "이번 발생 종료일", example = "2026-11-12") LocalDate endDt,
    @Schema(description = "매년 반복 여부(Y/N)", example = "Y") String repeatYearlyYn,
    @Schema(description = "최초 일자부터 지난 햇수", example = "36") int yearsSince,
    @Schema(description = "최초 시작일", example = "1990-11-12") LocalDate originStartDt,
    @Schema(description = "메모") String memo) {

  /**
   * 일정과 발생으로 응답을 만든다.
   *
   * @param event 일정
   * @param occurrence 발생
   * @return 응답
   */
  public static CalendarOccurrenceResponse of(CalendarEvent event, Occurrence occurrence) {
    return new CalendarOccurrenceResponse(event.getId(), event.getEventTypeCd(), event.getTitle(),
        occurrence.startDt(), occurrence.endDt(), event.getRepeatYearlyYn(),
        occurrence.yearsSince(), event.getStartDt(), event.getMemo());
  }
}
