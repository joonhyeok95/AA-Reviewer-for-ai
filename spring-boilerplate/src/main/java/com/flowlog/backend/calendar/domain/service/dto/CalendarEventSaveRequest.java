package com.flowlog.backend.calendar.domain.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 일정 등록·수정 요청.
 *
 * @param eventTypeCd 일정 유형 (공통코드 CAL_EVENT_TYPE)
 * @param title 제목
 * @param startDt 시작일 (매년 반복이면 최초 일자 - 생일은 출생일)
 * @param endDt 종료일 (생략하면 시작일과 같음)
 * @param repeatYearlyYn 매년 반복 여부 (Y/N)
 * @param memo 메모
 * @author flowlog
 * @since 2026-11-07
 */
@Schema(description = "일정 등록·수정 요청")
public record CalendarEventSaveRequest(
    @Schema(description = "일정 유형 (공통코드 CAL_EVENT_TYPE)", example = "BIRTHDAY",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(max = 30)
    String eventTypeCd,

    @Schema(description = "제목", example = "홍길동 생일", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(max = 200)
    String title,

    @Schema(description = "시작일", example = "1990-11-12", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    LocalDate startDt,

    @Schema(description = "종료일 (생략 시 시작일)", example = "1990-11-12")
    LocalDate endDt,

    @Schema(description = "매년 반복 여부(Y/N)", example = "Y",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Pattern(regexp = "^[YN]$")
    String repeatYearlyYn,

    @Schema(description = "메모", example = "케이크 준비")
    @Size(max = 1000)
    String memo) {

  /**
   * 종료일 (생략 시 시작일).
   *
   * @return 종료일
   */
  public LocalDate endOrStart() {
    return endDt == null ? startDt : endDt;
  }
}
