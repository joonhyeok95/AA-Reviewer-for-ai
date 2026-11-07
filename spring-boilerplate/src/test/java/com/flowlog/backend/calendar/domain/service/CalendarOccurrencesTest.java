package com.flowlog.backend.calendar.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowlog.backend.calendar.domain.service.CalendarOccurrences.Occurrence;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 일정 발생일 계산 (매년 반복 펼치기) 단위 테스트.
 *
 * @author flowlog
 * @since 2026-11-07
 */
class CalendarOccurrencesTest {

  private static final LocalDate NOV_FROM = LocalDate.of(2026, 11, 1);
  private static final LocalDate NOV_TO = LocalDate.of(2026, 11, 30);

  @Test
  @DisplayName("반복 안 함 - 기간과 겹치면 원래 기간 그대로, 안 겹치면 없음")
  void once() {
    // given
    LocalDate start = LocalDate.of(2026, 10, 30);
    LocalDate end = LocalDate.of(2026, 11, 2);

    // when
    List<Occurrence> overlapping = CalendarOccurrences.expand(start, end, false, NOV_FROM, NOV_TO);
    List<Occurrence> outside = CalendarOccurrences.expand(start, LocalDate.of(2026, 10, 31), false,
        NOV_FROM, NOV_TO);

    // then
    assertThat(overlapping).containsExactly(new Occurrence(start, end, 0));
    assertThat(outside).isEmpty();
  }

  @Test
  @DisplayName("매년 반복 - 생일은 해당 연도 같은 월·일, 지난 햇수 = 나이")
  void yearly_birthday() {
    // when
    List<Occurrence> result = CalendarOccurrences.expand(LocalDate.of(1990, 11, 12),
        LocalDate.of(1990, 11, 12), true, NOV_FROM, NOV_TO);

    // then
    assertThat(result).containsExactly(
        new Occurrence(LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 12), 36));
  }

  @Test
  @DisplayName("매년 반복 - 시작한 해 이전에는 발생하지 않음")
  void yearly_notBeforeStart() {
    // when
    List<Occurrence> result = CalendarOccurrences.expand(LocalDate.of(2027, 11, 12),
        LocalDate.of(2027, 11, 12), true, NOV_FROM, NOV_TO);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("매년 반복 - 2월 29일은 평년에 2월 28일, 윤년에 2월 29일")
  void yearly_leapDay() {
    // given
    LocalDate leapDay = LocalDate.of(1996, 2, 29);

    // when
    List<Occurrence> common = CalendarOccurrences.expand(leapDay, leapDay, true,
        LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
    List<Occurrence> leap = CalendarOccurrences.expand(leapDay, leapDay, true,
        LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29));

    // then
    assertThat(common).extracting(Occurrence::startDt).containsExactly(LocalDate.of(2026, 2, 28));
    assertThat(leap).extracting(Occurrence::startDt).containsExactly(LocalDate.of(2028, 2, 29));
  }

  @Test
  @DisplayName("매년 반복 - 해를 넘기는 일정(12/31~1/1)은 1월 조회에도 전년도 발생분이 걸림")
  void yearly_crossYear() {
    // when
    List<Occurrence> result = CalendarOccurrences.expand(LocalDate.of(2020, 12, 31),
        LocalDate.of(2021, 1, 1), true, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31));

    // then
    assertThat(result).containsExactly(
        new Occurrence(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1), 6));
  }

  @Test
  @DisplayName("매년 반복 - 여러 해에 걸친 조회 기간은 해마다 1건")
  void yearly_multiYearRange() {
    // when
    List<Occurrence> result = CalendarOccurrences.expand(LocalDate.of(2000, 5, 5),
        LocalDate.of(2000, 5, 5), true, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 12, 31));

    // then
    assertThat(result).extracting(Occurrence::startDt)
        .containsExactly(LocalDate.of(2026, 5, 5), LocalDate.of(2027, 5, 5));
  }
}
