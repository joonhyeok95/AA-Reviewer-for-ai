/** GET /api/v1/langs - 지원 언어 (공통코드 LANG) */
export interface Lang {
  langCd: string
  langNm: string
  /** 에디터 언어팩 (LANG.ATTR1, 예: ko-KR) - Accept-Language 로도 사용 */
  editorLocale: string
  badge: string
  defaultLang: boolean
}

/** GET /api/v1/codes/{groupCd} */
export interface Code {
  code: string
  codeNm: string
  sortOrder: number
  attr1: string | null
}
