package com.flowlog.backend.calendar.domain.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 일정 기간 조회 조건. (from ~ to, 양 끝 포함, 최대 {@link #MAX_DAYS}일)
 *
 * @param from 조회 시작일
 * @param to 조회 종료일
 * @author flowlog
 * @since 2026-11-07
 */
@Schema(description = "일정 기간 조회 조건")
public record CalendarRangeRequest(
    @Schema(description = "조회 시작일", example = "2026-11-01",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate from,

    @Schema(description = "조회 종료일 (포함)", example = "2026-11-30",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate to) {

  /** 한 번에 조회할 수 있는 최대 일수. (달력 6주 = 42일, 목록은 한 달) */
  public static final int MAX_DAYS = 400;
}
