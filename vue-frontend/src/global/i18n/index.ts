import { createI18n } from 'vue-i18n'
import en from './messages/en'
import ko from './messages/ko'

/**
 * 화면 문구 다국어.
 *
 *  - 언어 목록은 서버 공통코드 LANG 기준 (GET /api/v1/langs). 여기의 메시지 파일은 "번역본" 일 뿐이다.
 *  - LANG 에 새 언어가 추가됐는데 번역본이 없으면 기본 언어 문구로 표시된다. (fallbackLocale)
 *    번역본을 추가하려면 messages/{langCd}.ts 를 만들고 아래 messages 에 등록한다.
 *  - 서버 응답 메시지(messageTitle / messageContent)와 데이터 이름(메뉴/코드/조직)은 서버가 번역해서 준다.
 */
export const i18n = createI18n({
  legacy: false,
  locale: 'ko',
  fallbackLocale: 'ko',
  missingWarn: false,
  fallbackWarn: false,
  messages: { ko, en },
})

type UiLocale = keyof typeof i18n.global.messages.value

/** 번역본이 있는 언어인지 */
export function hasMessages(langCd: string): langCd is UiLocale {
  return (i18n.global.availableLocales as string[]).includes(langCd)
}

/**
 * 화면 문구 언어 설정. 번역본이 없으면 fallback(기본 언어 → ko) 문구를 쓴다.
 *
 * @param langCd 표시 언어 (LANG 코드)
 * @param fallbackLangCd 기본 언어 (LANG 정렬 첫 번째)
 */
export function setUiLocale(langCd: string, fallbackLangCd = 'ko'): void {
  const fallback: UiLocale = hasMessages(fallbackLangCd) ? fallbackLangCd : 'ko'
  i18n.global.fallbackLocale.value = fallback
  i18n.global.locale.value = hasMessages(langCd) ? langCd : fallback
}
