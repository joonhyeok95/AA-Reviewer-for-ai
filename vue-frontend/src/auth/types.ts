/** 인증 방식 */
export type AuthType = 'OAUTH2_SESSION' | 'SAML2' | 'OAUTH2_BEARER' | 'OTHER'

/** DB 유효 권한 */
export interface EffectiveRole {
  roleCd: string
  roleTypeCd: string
  roleLevel: number
  moduleCd: string | null
}

/** GET /api/v1/auth/me */
export interface AuthUser {
  authType: AuthType
  userId: string
  userNm: string | null
  registered: boolean
  serviceAccount: boolean
  clientId: string | null
  roles: EffectiveRole[]
  /** SCOPE_{리소스}.{list|add|edit|del}, ROLE_{권한코드} */
  authorities: string[]
  /** 표시 언어 (사용자 설정 → Accept-Language → 기본 언어) */
  langCd: string
  /** 사용자 설정 언어 (미설정 null) */
  userLangCd: string | null
}

/** 리소스별 작업 */
export type Action = 'list' | 'add' | 'edit' | 'del'
