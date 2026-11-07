import type { CalendarOccurrence } from './types'

/**
 * 달력 날짜 계산. 날짜는 시간대 영향을 받지 않도록 'YYYY-MM-DD' 문자열로 다룬다.
 * (문자열 비교 = 날짜 비교)
 */

/** 연·월 (month 는 1~12) */
export interface YearMonth {
  year: number
  month: number
}

const pad = (n: number): string => String(n).padStart(2, '0')

export function toIso(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function parseIso(iso: string): Date {
  const [year, month, day] = iso.split('-').map(Number)
  return new Date(year, month - 1, day)
}

export function addDays(iso: string, days: number): string {
  const date = parseIso(iso)
  date.setDate(date.getDate() + days)
  return toIso(date)
}

export function addMonths(ym: YearMonth, delta: number): YearMonth {
  const index = ym.year * 12 + (ym.month - 1) + delta
  return { year: Math.floor(index / 12), month: (index % 12) + 1 }
}

export function yearMonthOf(iso: string): YearMonth {
  const [year, month] = iso.split('-').map(Number)
  return { year, month }
}

/** '2026-11' ↔ YearMonth (주소 쿼리용). 형식이 틀리면 null */
export function parseYearMonth(value: unknown): YearMonth | null {
  if (typeof value !== 'string' || !/^[0-9]{4}-(0[1-9]|1[0-2])$/.test(value)) return null
  const [year, month] = value.split('-').map(Number)
  return { year, month }
}

export function formatYearMonth(ym: YearMonth): string {
  return `${ym.year}-${pad(ym.month)}`
}

/** 그 달의 첫날 ~ 말일 */
export function monthRange(ym: YearMonth): { from: string; to: string } {
  const last = new Date(ym.year, ym.month, 0).getDate()
  return { from: `${ym.year}-${pad(ym.month)}-01`, to: `${ym.year}-${pad(ym.month)}-${pad(last)}` }
}

/** 월 달력 6주(42일). 일요일 시작, 앞뒤 달 날짜 포함 */
export function monthGrid(ym: YearMonth): string[] {
  const first = new Date(ym.year, ym.month - 1, 1)
  const start = toIso(new Date(ym.year, ym.month - 1, 1 - first.getDay()))
  return Array.from({ length: 42 }, (_, i) => addDays(start, i))
}

/** 날짜별 일정 (여러 날 일정은 걸친 날마다). 날짜 안에서는 응답 순서 유지 */
export function occurrencesByDay(occurrences: CalendarOccurrence[], days: string[])
  : Map<string, CalendarOccurrence[]> {
  const result = new Map<string, CalendarOccurrence[]>(days.map((day) => [day, []]))
  for (const occurrence of occurrences) {
    for (const day of days) {
      if (day >= occurrence.startDt && day <= occurrence.endDt) {
        result.get(day)?.push(occurrence)
      }
    }
  }
  return result
}

/** 목록 보기 : 그 달에 걸친 일정을 표시일(시작일, 지난달부터 이어지면 1일)별로 묶음 */
export function groupForList(occurrences: CalendarOccurrence[], ym: YearMonth)
  : { day: string; items: CalendarOccurrence[] }[] {
  const { from, to } = monthRange(ym)
  const groups = new Map<string, CalendarOccurrence[]>()
  for (const occurrence of occurrences) {
    if (occurrence.endDt < from || occurrence.startDt > to) continue
    const day = occurrence.startDt < from ? from : occurrence.startDt
    groups.set(day, [...(groups.get(day) ?? []), occurrence])
  }
  return [...groups.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([day, items]) => ({ day, items }))
}
