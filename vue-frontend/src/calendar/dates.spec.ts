import { describe, expect, it } from 'vitest'
import {
  addDays, addMonths, groupForList, monthGrid, monthRange, occurrencesByDay, parseYearMonth,
} from './dates'
import type { CalendarOccurrence } from './types'

function occurrence(eventId: number, startDt: string, endDt = startDt): CalendarOccurrence {
  return {
    eventId, eventTypeCd: 'ETC', title: `일정${eventId}`, startDt, endDt, repeatYearlyYn: 'N',
    yearsSince: 0, originStartDt: startDt, memo: null,
  }
}

describe('달력 날짜 계산', () => {
  it('2026년 11월 달력은 일요일(11/1)부터 6주 42일', () => {
    const days = monthGrid({ year: 2026, month: 11 })
    expect(days).toHaveLength(42)
    expect(days[0]).toBe('2026-11-01')
    expect(days[41]).toBe('2026-12-12')
  })

  it('1일이 일요일이 아니면 앞 달 날짜로 채움 (2026년 12월은 11/29 일요일부터)', () => {
    expect(monthGrid({ year: 2026, month: 12 })[0]).toBe('2026-11-29')
  })

  it('월 이동은 해를 넘김, 말일은 월마다 다름 (윤년 2월 29일)', () => {
    expect(addMonths({ year: 2026, month: 12 }, 1)).toEqual({ year: 2027, month: 1 })
    expect(addMonths({ year: 2026, month: 1 }, -1)).toEqual({ year: 2025, month: 12 })
    expect(monthRange({ year: 2028, month: 2 })).toEqual({ from: '2028-02-01', to: '2028-02-29' })
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01')
  })

  it('주소의 연·월(ym)은 YYYY-MM 형식만 허용', () => {
    expect(parseYearMonth('2026-11')).toEqual({ year: 2026, month: 11 })
    expect(parseYearMonth('2026-13')).toBeNull()
    expect(parseYearMonth(['2026-11'])).toBeNull()
  })

  it('여러 날 일정은 걸친 날마다 표시', () => {
    const days = ['2026-09-23', '2026-09-24', '2026-09-25', '2026-09-26', '2026-09-27']
    const byDay = occurrencesByDay([occurrence(1, '2026-09-24', '2026-09-26')], days)
    expect(days.filter((day) => byDay.get(day)?.length)).toEqual(
      ['2026-09-24', '2026-09-25', '2026-09-26'])
  })

  it('목록은 그 달 일정만 날짜별로 묶고, 지난달부터 이어지는 일정은 1일에 표시', () => {
    const groups = groupForList([
      occurrence(1, '2026-10-30', '2026-11-02'),
      occurrence(2, '2026-11-12'),
      occurrence(3, '2026-11-12'),
      occurrence(4, '2026-12-01'),
    ], { year: 2026, month: 11 })
    expect(groups.map((group) => [group.day, group.items.map((item) => item.eventId)])).toEqual([
      ['2026-11-01', [1]],
      ['2026-11-12', [2, 3]],
    ])
  })
})
