import { deleteResult, getResult, postResult, putResult } from '@/global/api/http'
import type { MessageResponse } from '@/global/api/types'
import type { CalendarEvent, CalendarEventSaveRequest, CalendarOccurrence } from './types'

const BASE = '/api/v1/calendar/events'

/** 기간(from ~ to, 양 끝 포함) 안의 일정 발생 */
export function fetchOccurrences(from: string, to: string): Promise<CalendarOccurrence[]> {
  return getResult<CalendarOccurrence[]>(BASE, { params: { from, to } })
}

export function fetchEvent(id: number): Promise<CalendarEvent> {
  return getResult<CalendarEvent>(`${BASE}/${id}`)
}

export function createEvent(body: CalendarEventSaveRequest): Promise<MessageResponse<CalendarEvent>> {
  return postResult<CalendarEvent>(BASE, body)
}

export function updateEvent(id: number, body: CalendarEventSaveRequest)
  : Promise<MessageResponse<CalendarEvent>> {
  return putResult<CalendarEvent>(`${BASE}/${id}`, body)
}

export function deleteEvent(id: number): Promise<MessageResponse<null>> {
  return deleteResult(`${BASE}/${id}`)
}
