import DOMPurify from 'dompurify'

/**
 * v-html 로 넣는 HTML 정화. 서버(jsoup)에서 이미 정화되지만 화면에서도 한 번 더 거른다. (심층 방어)
 * 링크는 새 창 + noopener.
 */
export function sanitizeHtml(html: string): string {
  const clean = DOMPurify.sanitize(html, {
    USE_PROFILES: { html: true },
    FORBID_TAGS: ['style', 'form', 'input', 'button'],
    FORBID_ATTR: ['style'],
  })
  const container = document.createElement('div')
  container.innerHTML = clean
  container.querySelectorAll('a[href]').forEach((anchor) => {
    anchor.setAttribute('target', '_blank')
    anchor.setAttribute('rel', 'noopener noreferrer')
  })
  return container.innerHTML
}

/** 본문이 비었는지 (태그만 있는 에디터 기본값 포함) */
export function isBlankHtml(html: string | null | undefined): boolean {
  if (!html) return true
  const container = document.createElement('div')
  container.innerHTML = html
  const hasMedia = container.querySelector('img, table, iframe, video') !== null
  return !hasMedia && (container.textContent ?? '').trim() === ''
}
