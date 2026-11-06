import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { fetchMe, logout as logoutApi } from './api'
import type { Action, AuthUser } from './types'

const SYS_ADMIN = 'ROLE_SYS_ADMIN'

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

  /** 리소스 작업 권한. 예) can('notice', 'add') */
  function can(resource: string, action: Action): boolean {
    const authorities = user.value?.authorities ?? []
    return authorities.includes(SYS_ADMIN) || authorities.includes(`SCOPE_${resource}.${action}`)
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
