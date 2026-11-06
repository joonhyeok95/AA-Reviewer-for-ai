import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { fetchMe, logout as logoutApi } from './api'
import type { Action, AuthUser } from './types'

const SYS_ADMIN = 'ROLE_SYS_ADMIN'

/** 작업 → scope 동사 (서버 ScopeActions 와 같음 : write=add, update=edit, delete=del) */
const SCOPE_ALIASES: Record<Action, string[]> = {
  list: ['list'],
  add: ['add', 'write'],
  edit: ['edit', 'update'],
  del: ['del', 'delete'],
}

function scopeNames(action: Action): string[] {
  return SCOPE_ALIASES[action]
}

/**
 * 로그인 사용자.
 *
 * 화면 권한 = 서버가 계산한 authorities 그대로 (IdP scope ∩ DB 업무 권한).
 * 버튼 노출만 결정하며, 실제 인가는 항상 서버(SecurityConfig)가 한다.
 */
export const useAuthStore = defineStore('auth', () => {
  const user = ref<AuthUser | null>(null)
  const loaded = ref(false)

  const isLoggedIn = computed(() => user.value !== null)
  const isSysAdmin = computed(() => user.value?.authorities.includes(SYS_ADMIN) ?? false)
  const displayName = computed(() => user.value?.userNm || user.value?.userId || '')

  /**
   * 리소스 작업 권한. 예) can('board', 'add')
   * 작업은 DB 권한 기준(list/add/edit/del)이고, scope 동사 동의어(write/update/delete)도 인정한다.
   */
  function can(resource: string, action: Action): boolean {
    const authorities = user.value?.authorities ?? []
    return authorities.includes(SYS_ADMIN)
      || scopeNames(action).some((name) => authorities.includes(`SCOPE_${resource}.${name}`))
  }

  async function load(): Promise<AuthUser | null> {
    user.value = await fetchMe()
    loaded.value = true
    return user.value
  }

  async function ensureLoaded(): Promise<AuthUser | null> {
    return loaded.value ? user.value : load()
  }

  function clear(): void {
    user.value = null
    loaded.value = true
  }

  async function logout(): Promise<void> {
    try {
      await logoutApi()
    } finally {
      clear()
    }
  }

  return { user, loaded, isLoggedIn, isSysAdmin, displayName, can, load, ensureLoaded, clear, logout }
})
