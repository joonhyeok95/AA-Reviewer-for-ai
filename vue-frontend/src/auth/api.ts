import { API_BASE, getResult, http } from '@/global/api/http'
import type { AuthUser } from './types'

/** 로그인 사용자. 미로그인이면 null. (401 은 화면 이동 없이 null) */
export async function fetchMe(): Promise<AuthUser | null> {
  try {
    return await getResult<AuthUser>('/api/v1/auth/me', { silent401: true })
  } catch (error) {
    if ((error as { status?: number }).status === 401) {
      return null
    }
    throw error
  }
}

/** 로그아웃 : 서버 세션 폐기 + Keycloak SSO 세션 종료 (POST, CSRF) */
export async function logout(): Promise<void> {
  await http.post('/logout', null, { silent401: true })
}

export type LoginMethod = 'oauth2' | 'saml2'

/**
 * 로그인 시작 URL. 로그인 후 서버가 returnTo(같은 호스트 절대 경로)로 돌려보낸다.
 *  - oauth2 : Keycloak OIDC (BFF, PKCE)
 *  - saml2  : Keycloak SAML (1시간마다 재로그인)
 */
export function loginUrl(method: LoginMethod, returnTo: string): string {
  const start = method === 'saml2' ? '/saml2/authenticate/keycloak'
    : '/oauth2/authorization/keycloak'
  return `${API_BASE}${start}?returnTo=${encodeURIComponent(returnTo)}`
}
