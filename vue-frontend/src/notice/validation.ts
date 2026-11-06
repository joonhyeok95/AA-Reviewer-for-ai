import { i18n } from '@/global/i18n'
import { isBlankHtml } from '@/global/util/html'

/**
 * 공지 입력 검증 (서버 규칙과 동일하게 화면에서 먼저 안내. 최종 검증은 서버 B2002~B2009)
 *  - 최소 1개 언어의 제목 + 본문
 *  - 제목/본문 중 하나만 입력한 언어는 오류
 *  - 팝업 사용 시 시작일 ≤ 종료일
 *  - 대상 지정 메일은 대상 1개 이상
 */
export interface LangContent {
  title: string
  contentHtml: string
}

export type LangState = 'filled' | 'partial' | 'empty'

export interface NoticeFormErrors {
  summary?: string
  langs?: Record<string, string>
  categoryCd?: string
  popup?: string
  mailTargets?: string
}

export interface NoticeFormInput {
  langCds: string[]
  contents: Record<string, LangContent | undefined>
  categoryCd: string
  popupYn: 'Y' | 'N'
  popupStartDt: string
  popupEndDt: string
  mailSendTypeCd: string
  mailTargetCount: number
}

export function langState(content: LangContent | undefined): LangState {
  const hasTitle = !!content?.title.trim()
  const hasBody = !isBlankHtml(content?.contentHtml)
  if (hasTitle && hasBody) return 'filled'
  return hasTitle || hasBody ? 'partial' : 'empty'
}

export function validateNotice(input: NoticeFormInput): NoticeFormErrors {
  const t = i18n.global.t
  const errors: NoticeFormErrors = {}
  const langErrors: Record<string, string> = {}
  let filled = 0

  for (const langCd of input.langCds) {
    const state = langState(input.contents[langCd])
    if (state === 'filled') filled += 1
    if (state === 'partial') langErrors[langCd] = t('notice.form.errPartial')
  }
  if (Object.keys(langErrors).length) {
    errors.langs = langErrors
  }
  if (filled === 0 && !errors.langs) {
    errors.summary = t('notice.form.errNoLang')
  }
  if (!input.categoryCd) {
    errors.categoryCd = t('notice.form.errCategory')
  }
  if (input.popupYn === 'Y') {
    if (!input.popupStartDt || !input.popupEndDt) {
      errors.popup = t('notice.form.errPopupRequired')
    } else if (input.popupStartDt > input.popupEndDt) {
      errors.popup = t('notice.form.errPopupRange')
    }
  }
  if (input.mailSendTypeCd === 'TARGET' && input.mailTargetCount === 0) {
    errors.mailTargets = t('notice.form.errMailTargets')
  }
  if (!errors.summary && Object.keys(errors).length) {
    errors.summary = [
      errors.langs && t('notice.form.errLangSummary', { langs: Object.keys(errors.langs).map((l) => l.toUpperCase()).join(', ') }),
      errors.categoryCd, errors.popup, errors.mailTargets,
    ].filter(Boolean).join('\n')
  }
  return errors
}
