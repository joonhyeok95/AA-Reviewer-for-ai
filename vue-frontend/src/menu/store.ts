import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { fetchMyMenus } from './api'
import type { MenuNode } from './types'

/** 내 메뉴 트리 (GET /api/v1/menus/me - 유효 권한, 사용자 언어) */
export const useMenuStore = defineStore('menu', () => {
  const menus = ref<MenuNode[]>([])

  /** 메인 메뉴 (사이드 영역 제외) */
  const mainMenus = computed(() => menus.value.filter((menu) => !menu.sideMenu))
  /** 보조 메뉴 (공지사항, 외부 링크 등 SIDE_MENU_YN='Y') */
  const sideMenus = computed(() => menus.value.filter((menu) => menu.sideMenu))

  async function load(): Promise<void> {
    menus.value = await fetchMyMenus()
  }

  function clear(): void {
    menus.value = []
  }

  /** URL 로 메뉴 찾기 (브레드크럼, 준비 중 화면) - 루트부터의 경로 반환 */
  function findTrail(url: string): MenuNode[] {
    const walk = (nodes: MenuNode[], trail: MenuNode[]): MenuNode[] => {
      for (const node of nodes) {
        const next = [...trail, node]
        if (node.menuUrl === url) {
          return next
        }
        const found = walk(node.children, next)
        if (found.length) {
          return found
        }
      }
      return []
    }
    return walk(menus.value, [])
  }

  function findById(menuId: number): MenuNode | undefined {
    const walk = (nodes: MenuNode[]): MenuNode | undefined => {
      for (const node of nodes) {
        if (node.menuId === menuId) return node
        const found = walk(node.children)
        if (found) return found
      }
      return undefined
    }
    return walk(menus.value)
  }

  return { menus, mainMenus, sideMenus, load, clear, findTrail, findById }
})
