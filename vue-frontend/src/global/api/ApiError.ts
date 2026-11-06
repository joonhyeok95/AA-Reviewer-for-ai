import type { FieldErrorDetail, MessageResponse } from './types'

/**
 * API 오류. 서버 표준 응답(messageId / messageTitle / messageContent)을 그대로 담는다.
 *  - status 0 : 네트워크 오류 (서버 미기동, 연결 끊김)
 */
export class ApiError extends Error {
  readonly status: number
  readonly messageId: string
  readonly title: string
  readonly content: string
  readonly fieldErrors: FieldErrorDetail[]

  constructor(status: number, messageId: string, title: string, content: string,
    fieldErrors: FieldErrorDetail[] = []) {
    super(content || title || messageId)
    this.name = 'ApiError'
    this.status = status
    this.messageId = messageId
    this.title = title
    this.content = content
    this.fieldErrors = fieldErrors
  }

  /** 응답 본문(표준 응답 또는 임의 값)으로 오류를 만든다. */
  static fromResponse(status: number, body: unknown): ApiError {
    if (isMessageResponse(body)) {
      const fieldErrors = Array.isArray(body.result) ? (body.result as FieldErrorDetail[]) : []
      return new ApiError(status, body.messageId, body.messageTitle, body.messageContent,
        fieldErrors)
    }
    return new ApiError(status, `HTTP_${status}`, '', '')
  }

  static network(): ApiError {
    return new ApiError(0, 'NETWORK', '', '')
  }

  /** 필드별 오류 사유. (같은 필드는 첫 번째) */
  fieldError(field: string): string | undefined {
    return this.fieldErrors.find((error) => error.field === field)?.reason
  }
}

export function isMessageResponse(body: unknown): body is MessageResponse<unknown> {
  return typeof body === 'object' && body !== null && 'messageId' in body && 'httpStatus' in body
}
