import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from './store'
import type { AuthUser } from './types'

function user(authorities: string[]): AuthUser {
  return {
    authType: 'OAUTH2_SESSION', userId: 'tx.viewer', userNm: 'TX조회매니저', registered: true,
    serviceAccount: false, clientId: 'flowlog-bff', roles: [], authorities, langCd: 'ko',
    userLangCd: null,
  }
}

describe('auth store can()', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('SCOPE_{리소스}.{작업} 이 있을 때만 허용', () => {
    const auth = useAuthStore()
    auth.user = user(['SCOPE_board.list', 'SCOPE_sample.list', 'ROLE_TX_MANAGER_READ'])

    expect(auth.can('board', 'list')).toBe(true)
    expect(auth.can('board', 'add')).toBe(false)
    expect(auth.can('sample', 'del')).toBe(false)
  })

  it('scope 동사 동의어 - write=add, update=edit, delete=del', () => {
    const auth = useAuthStore()
    auth.user = user(['SCOPE_board.list', 'SCOPE_board.write', 'SCOPE_board.update'])

    expect(auth.can('board', 'add')).toBe(true)
    expect(auth.can('board', 'edit')).toBe(true)
    expect(auth.can('board', 'del')).toBe(false)
  })

  it('시스템관리자(ROLE_SYS_ADMIN)는 전체 허용', () => {
    const auth = useAuthStore()
    auth.user = user(['ROLE_SYS_ADMIN'])

    expect(auth.can('board', 'del')).toBe(true)
    expect(auth.isSysAdmin).toBe(true)
  })

  it('미로그인은 모두 불가', () => {
    expect(useAuthStore().can('board', 'list')).toBe(false)
  })
})
