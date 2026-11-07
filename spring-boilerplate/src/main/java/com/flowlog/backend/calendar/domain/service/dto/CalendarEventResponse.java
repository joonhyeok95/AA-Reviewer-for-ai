package com.flowlog.backend.calendar.domain.service.dto;

import com.flowlog.backend.calendar.domain.model.CalendarEvent;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 일정 응답. (원본 - 수정 화면용)
 *
 * @param id 일정 ID
 * @param eventTypeCd 일정 유형
 * @param title 제목
 * @param startDt 시작일
 * @param endDt 종료일
 * @param repeatYearlyYn 매년 반복 여부
 * @param memo 메모
 * @param createdBy 생성자
 * @param createdAt 생성일시
 * @param updatedBy 수정자
 * @param updatedAt 수정일시
 * @author flowlog
 * @since 2026-11-07
 */
@Schema(description = "일정 응답")
public record CalendarEventResponse(
    @Schema(description = "일정 ID", example = "1") Long id,
    @Schema(description = "일정 유형", example = "BIRTHDAY") String eventTypeCd,
    @Schema(description = "제목", example = "홍길동 생일") String title,
    @Schema(description = "시작일", example = "1990-11-12") LocalDate startDt,
    @Schema(description = "종료일", example = "1990-11-12") LocalDate endDt,
    @Schema(description = "매년 반복 여부(Y/N)", example = "Y") String repeatYearlyYn,
    @Schema(description = "메모") String memo,
    @Schema(description = "생성자", example = "admin") String createdBy,
    @Schema(description = "생성일시") LocalDateTime createdAt,
    @Schema(description = "수정자", example = "admin") String updatedBy,
    @Schema(description = "수정일시") LocalDateTime updatedAt) {

  /**
   * Entity 를 응답으로 변환한다.
   *
   * @param event 일정
   * @return 응답
   */
  public static CalendarEventResponse from(CalendarEvent event) {
    return new CalendarEventResponse(event.getId(), event.getEventTypeCd(), event.getTitle(),
        event.getStartDt(), event.getEndDt(), event.getRepeatYearlyYn(), event.getMemo(),
        event.getCreatedBy(), event.getCreatedAt(), event.getUpdatedBy(), event.getUpdatedAt());
  }
}
