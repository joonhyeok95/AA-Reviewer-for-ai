/**
 * 표준 응답 형식 (spring-boilerplate MessageResponse / Gateway GatewayResponse 공통).
 * 성공/실패 모든 응답이 같은 형식이며 result 만 다르다.
 */
export interface MessageResponse<T> {
  locale: string
  timestamp: string
  httpStatus: number
  messageId: string
  messageTitle: string
  messageContent: string
  path: string
  result: T
}

/** 목록 조회 result (PageResponse). page 는 0부터. */
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

/** 검증 오류 result 항목 (FieldErrorDetail). */
export interface FieldErrorDetail {
  field: string
  rejectedValue: unknown
  reason: string
}

/** 페이지 요청 파라미터. */
export interface PageQuery {
  page?: number
  size?: number
  sort?: string
}
