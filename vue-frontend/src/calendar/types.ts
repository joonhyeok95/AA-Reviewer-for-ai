export type YesNo = 'Y' | 'N'

/** 화면 보기 방식 : 월 달력 / 목록 */
export type CalendarViewMode = 'month' | 'list'

/** GET /api/v1/calendar/events?from&to - 기간 안의 일정 발생 (매년 반복은 해마다 1건) */
export interface CalendarOccurrence {
  eventId: number
  /** 공통코드 CAL_EVENT_TYPE (ATTR1 = 표시 색) */
  eventTypeCd: string
  title: string
  /** 이번 발생 시작일 (YYYY-MM-DD) */
  startDt: string
  endDt: string
  repeatYearlyYn: YesNo
  /** 최초 일자부터 지난 햇수 (생일 = 나이, 결혼기념일 = 주년, 반복 안 함·연도 모름 0) */
  yearsSince: number
  originStartDt: string
  memo: string | null
}

/** GET /api/v1/calendar/events/{id} - 일정 원본 */
export interface CalendarEvent {
  id: number
  eventTypeCd: string
  title: string
  startDt: string
  endDt: string
  repeatYearlyYn: YesNo
  /** 시작 연도를 아는지 (N = 출생·결혼 연도 모름 → 나이·주년 표시 안 함) */
  yearKnownYn: YesNo
  memo: string | null
  createdBy: string
  createdAt: string
  updatedBy: string
  updatedAt: string
}

/** 등록·수정 요청 (종료일 생략 = 시작일) */
export interface CalendarEventSaveRequest {
  eventTypeCd: string
  title: string
  startDt: string
  endDt: string | null
  repeatYearlyYn: YesNo
  yearKnownYn: YesNo
  memo: string
}
