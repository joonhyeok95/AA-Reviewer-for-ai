import { describe, expect, it } from 'vitest'
import { ApiError } from './ApiError'

describe('ApiError', () => {
  it('표준 응답이면 messageId / 제목 / 내용 / 필드 오류를 그대로 담는다', () => {
    const error = ApiError.fromResponse(400, {
      locale: 'ko_KR', timestamp: '', httpStatus: 400, messageId: 'C0002',
      messageTitle: '입력값 오류', messageContent: '입력값을 확인해 주세요.', path: '/api/v1/samples',
      result: [{ field: 'name', rejectedValue: '', reason: '필수 입력입니다.' }],
    })

    expect(error.status).toBe(400)
    expect(error.messageId).toBe('C0002')
    expect(error.title).toBe('입력값 오류')
    expect(error.fieldError('name')).toBe('필수 입력입니다.')
    expect(error.fieldError('useYn')).toBeUndefined()
  })

  it('Gateway 표준 응답(429 G0005)도 같은 형식으로 처리한다', () => {
    const error = ApiError.fromResponse(429, {
      httpStatus: 429, messageId: 'G0005', messageTitle: '요청 한도 초과', messageContent: '잠시 후',
      result: null,
    })

    expect(error.messageId).toBe('G0005')
    expect(error.fieldErrors).toEqual([])
  })

  it('표준 응답이 아니면 HTTP 상태만 담는다', () => {
    const error = ApiError.fromResponse(502, '<html>Bad Gateway</html>')

    expect(error.messageId).toBe('HTTP_502')
    expect(error.title).toBe('')
  })

  it('네트워크 오류는 status 0', () => {
    expect(ApiError.network().status).toBe(0)
  })
})
