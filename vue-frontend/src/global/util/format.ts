/**
 * 서버 날짜 값(LocalDateTime "2026-11-05T09:00:00", LocalDate "2026-11-05", 서버 시간대 Asia/Seoul)
 * 을 화면 표기로 바꾼다. 시간대 변환 없이 값 그대로 쓴다.
 */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return ''
  const [date, time = ''] = value.split('T')
  return time ? `${date} ${time.slice(0, 5)}` : date ?? ''
}

export function formatDate(value: string | null | undefined): string {
  return value ? value.slice(0, 10) : ''
}

/** <input type="datetime-local"> 값 ↔ 서버 LocalDateTime */
export function toDateTimeLocal(value: string | null | undefined): string {
  return value ? value.slice(0, 16) : ''
}

export function fromDateTimeLocal(value: string): string | null {
  if (!value) return null
  return value.length === 16 ? `${value}:00` : value
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

export function formatNumber(value: number, locale: string): string {
  return new Intl.NumberFormat(locale).format(value)
}

/** 오늘 날짜 (YYYY-MM-DD, 브라우저 시간대) */
export function today(): string {
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}
