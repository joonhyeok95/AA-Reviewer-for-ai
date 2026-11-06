import { useAuthStore } from '@/auth/store'
import { useCodeStore } from '@/code/store'
import type { Lang } from '@/code/types'
import { setAcceptLanguage } from '@/global/api/http'
import { setUiLocale } from '@/global/i18n'
import { useMenuStore } from '@/menu/store'
import { updateMyLang } from '@/user/api'

/**
 * 로그인 세션 준비.
 *  1) GET /auth/me (로그인 여부, 권한, 표시 언어)
 *  2) 지원 언어(LANG) + 내 메뉴
 *  3) 화면 언어 = 서버가 정한 표시 언어 (사용자 설정 → Accept-Language → 기본 언어)
 */
let bootstrapped = false

export async function bootstrapSession(): Promise<boolean> {
  const auth = useAuthStore()
  const user = await auth.ensureLoaded()
  if (!user) {
    return false
  }
  if (!bootstrapped) {
    const code = useCodeStore()
    await code.loadLangs()
    applyLang(user.langCd)
    await useMenuStore().load()
    bootstrapped = true
  }
  return true
}

export function resetSession(): void {
  bootstrapped = false
  useAuthStore().clear()
  useMenuStore().clear()
  useCodeStore().clearCodes()
}

/** 화면 언어 적용 : 문구, Accept-Language(LANG.ATTR1), <html lang> */
export function applyLang(langCd: string): void {
  const code = useCodeStore()
  const lang: Lang | undefined = code.langOf(langCd) ?? code.defaultLang
  if (!lang) {
    return
  }
  setUiLocale(lang.langCd, code.defaultLang?.langCd)
  setAcceptLanguage(lang.editorLocale)
  document.documentElement.lang = lang.langCd
}

/**
 * 언어 변경 : 사용자 설정 저장(PUT /users/me/lang) → 서버 기준 언어로 메뉴·코드·사용자 정보 다시 조회.
 * TB_USER 미등록 사용자는 저장 없이 이 브라우저에서만 바꾼다. (Accept-Language)
 */
export async function changeLang(langCd: string): Promise<void> {
  const auth = useAuthStore()
  if (auth.user?.registered) {
    await updateMyLang(langCd)
  }
  applyLang(langCd)
  useCodeStore().clearCodes()
  await Promise.all([auth.load(), useMenuStore().load()])
  if (auth.user && auth.user.langCd !== langCd && auth.user.registered) {
    applyLang(auth.user.langCd)
  }
}
