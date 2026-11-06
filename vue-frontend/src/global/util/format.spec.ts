import { describe, expect, it } from 'vitest'
import { formatDateTime, formatFileSize, fromDateTimeLocal, toDateTimeLocal } from './format'

describe('format', () => {
  it('서버 LocalDateTime 을 시간대 변환 없이 표시한다', () => {
    expect(formatDateTime('2026-11-05T09:30:12')).toBe('2026-11-05 09:30')
    expect(formatDateTime('2026-11-05')).toBe('2026-11-05')
    expect(formatDateTime(null)).toBe('')
  })

  it('datetime-local 입력값 ↔ 서버 값', () => {
    expect(toDateTimeLocal('2026-11-05T09:30:00')).toBe('2026-11-05T09:30')
    expect(fromDateTimeLocal('2026-11-05T09:30')).toBe('2026-11-05T09:30:00')
    expect(fromDateTimeLocal('')).toBeNull()
  })

  it('파일 크기', () => {
    expect(formatFileSize(512)).toBe('512 B')
    expect(formatFileSize(2048)).toBe('2.0 KB')
    expect(formatFileSize(5 * 1024 * 1024)).toBe('5.0 MB')
  })
})
