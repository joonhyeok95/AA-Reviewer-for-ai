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

/** 언어별 이름 (언어코드 → 이름). 기본 언어 이름 필수 */
export type LangNames = Record<string, string>

export type YesNo = 'Y' | 'N'

/** GET /api/v1/code-groups - 코드 그룹 (관리) */
export interface CodeGroupAdmin {
  groupCd: string
  groupNm: string
  names: LangNames
  groupDesc: string | null
  sortOrder: number
  /** 시스템 그룹 : 삭제 불가, 코드 삭제 불가 */
  systemYn: YesNo
  useYn: YesNo
  codeCnt: number
}

export interface CodeGroupSaveRequest {
  groupCd: string
  names: LangNames
  groupDesc: string
  sortOrder: number
  useYn: YesNo
}

/** GET /api/v1/code-groups/{groupCd}/codes - 코드 (관리) */
export interface CodeAdmin {
  groupCd: string
  code: string
  codeNm: string
  names: LangNames
  codeDesc: string | null
  sortOrder: number
  attr1: string | null
  attr2: string | null
  attr3: string | null
  useYn: YesNo
}

export interface CodeSaveRequest {
  code: string
  names: LangNames
  codeDesc: string
  sortOrder: number
  attr1: string
  attr2: string
  attr3: string
  useYn: YesNo
}
