import type { MenuNode } from './types'

/**
 * 메뉴 → 이동 대상.
 *  - LINK   : 외부 URL (새 창 여부 = NEW_WINDOW_YN)
 *  - IFRAME : /frame/{menuId} 화면 안에 iframe 으로 표시
 *  - MENU    : 메뉴 URL 이 곧 화면 경로 (화면이 없으면 "준비 중")
 *  - CONTENT : /content/{menuId} (언어별 본문 메뉴 - 본문 조회 API 는 아직 없음)
 *  - URL 없는 MENU : 하위 메뉴 묶음 (이동 없음)
 */
export type MenuLink =
  | { kind: 'route'; to: string }
  | { kind: 'external'; href: string; newWindow: boolean }
  | { kind: 'group' }

export function resolveMenuLink(menu: MenuNode): MenuLink {
  const url = menu.menuUrl
  if (menu.viewTypeCd === 'LINK' && url) {
    return { kind: 'external', href: url, newWindow: menu.newWindow }
  }
  if (menu.viewTypeCd === 'CONTENT') {
    return { kind: 'route', to: `/content/${menu.menuId}` }
  }
  if (menu.viewTypeCd === 'IFRAME' && url) {
    return { kind: 'route', to: `/frame/${menu.menuId}` }
  }
  if (menu.viewTypeCd === 'MENU' && url && url.startsWith('/')) {
    // 하위 메뉴가 있으면 묶음 (예: /tx, /tx/trades) - 실제 화면은 말단 메뉴
    return menu.children.length ? { kind: 'group' } : { kind: 'route', to: url }
  }
  return { kind: 'group' }
}

/** 현재 경로와 가장 길게 일치하는 메뉴 URL (상세/수정 화면도 목록 메뉴를 활성으로) */
export function matchMenuUrl(path: string, menus: MenuNode[]): string | undefined {
  let best: string | undefined
  const walk = (nodes: MenuNode[]) => {
    for (const node of nodes) {
      const url = node.menuUrl
      if (url && url.startsWith('/') && (path === url || path.startsWith(`${url}/`))
        && (!best || url.length > best.length)) {
        best = url
      }
      walk(node.children)
    }
  }
  walk(menus)
  return best
}

/** 외부 URL 은 http(s) 만 허용 (javascript: 등 차단) */
export function isSafeExternalUrl(url: string): boolean {
  try {
    const parsed = new URL(url)
    return parsed.protocol === 'https:' || parsed.protocol === 'http:'
  } catch {
    return false
  }
}
