package com.flowlog.backend.calendar.domain.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 일정 → 조회 기간 안의 발생일 계산. (매년 반복 펼치기)
 *
 * <ul>
 *   <li>반복 안 함 : 기간과 겹치면 원래 기간 그대로 1건</li>
 *   <li>매년 반복 : 시작한 해부터 해마다 같은 월·일. 2월 29일은 평년에 2월 28일.
 *       여러 날 일정(예: 12/31~1/1)은 길이를 유지하므로 전년도 발생분도 함께 확인한다.</li>
 *   <li>{@code yearsSince} : 시작한 해부터 몇 년째인지 (생일 = 나이, 결혼기념일 = 주년). 반복 안 함은 0</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-07
 */
public final class CalendarOccurrences {

  private CalendarOccurrences() {
  }

  /**
   * 발생일 계산.
   *
   * @param start 일정 시작일
   * @param end 일정 종료일
   * @param repeatYearly 매년 반복 여부
   * @param from 조회 시작일
   * @param to 조회 종료일
   * @return 기간과 겹치는 발생 (시작일 순)
   */
  public static List<Occurrence> expand(LocalDate start, LocalDate end, boolean repeatYearly,
      LocalDate from, LocalDate to) {
    List<Occurrence> result = new ArrayList<>();
    if (!repeatYearly) {
      if (!start.isAfter(to) && !end.isBefore(from)) {
        result.add(new Occurrence(start, end, 0));
      }
      return result;
    }
    long length = ChronoUnit.DAYS.between(start, end);
    for (int year = Math.max(start.getYear(), from.getYear() - 1); year <= to.getYear(); year++) {
      LocalDate occurrenceStart = sameDayIn(start, year);
      LocalDate occurrenceEnd = occurrenceStart.plusDays(length);
      if (!occurrenceStart.isAfter(to) && !occurrenceEnd.isBefore(from)) {
        result.add(new Occurrence(occurrenceStart, occurrenceEnd, year - start.getYear()));
      }
    }
    return result;
  }

  /** 같은 월·일 (해당 월에 없는 날짜 = 2월 29일 → 말일). */
  private static LocalDate sameDayIn(LocalDate date, int year) {
    LocalDate firstOfMonth = LocalDate.of(year, date.getMonth(), 1);
    return firstOfMonth.withDayOfMonth(
        Math.min(date.getDayOfMonth(), firstOfMonth.lengthOfMonth()));
  }

  /**
   * 발생 1건.
   *
   * @param startDt 발생 시작일
   * @param endDt 발생 종료일
   * @param yearsSince 시작한 해부터 지난 햇수 (반복 안 함은 0)
   */
  public record Occurrence(LocalDate startDt, LocalDate endDt, int yearsSince) {
  }
}
