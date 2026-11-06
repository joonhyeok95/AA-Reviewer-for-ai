import { describe, expect, it } from 'vitest'
import { isSafeExternalUrl, matchMenuUrl, resolveMenuLink } from './link'
import type { MenuNode } from './types'

const PERM = { list: true, add: false, edit: false, del: false }

function node(partial: Partial<MenuNode>): MenuNode {
  return {
    menuId: 1, menuNm: 'm', moduleCd: null, viewTypeCd: 'MENU', menuUrl: null, newWindow: false,
    adminMenu: false, sideMenu: false, permission: PERM, children: [], ...partial,
  }
}

describe('resolveMenuLink', () => {
  it('말단 MENU 는 메뉴 URL 이 화면 경로', () => {
    expect(resolveMenuLink(node({ menuUrl: '/support/notices' })))
      .toEqual({ kind: 'route', to: '/support/notices' })
  })

  it('하위 메뉴가 있으면 묶음', () => {
    expect(resolveMenuLink(node({ menuUrl: '/tx', children: [node({ menuId: 2 })] })).kind)
      .toBe('group')
  })

  it('LINK 는 외부 URL + 새 창 여부, IFRAME 은 /frame/{menuId}', () => {
    expect(resolveMenuLink(node({ viewTypeCd: 'LINK', menuUrl: 'https://example.com', newWindow: true })))
      .toEqual({ kind: 'external', href: 'https://example.com', newWindow: true })
    expect(resolveMenuLink(node({ menuId: 10, viewTypeCd: 'IFRAME', menuUrl: 'https://d.example.com' })))
      .toEqual({ kind: 'route', to: '/frame/10' })
  })

  it('CONTENT 는 /content/{menuId}, URL 없는 MENU 는 묶음', () => {
    expect(resolveMenuLink(node({ menuId: 6, viewTypeCd: 'CONTENT' })))
      .toEqual({ kind: 'route', to: '/content/6' })
    expect(resolveMenuLink(node({ menuUrl: null })).kind).toBe('group')
  })
})

describe('matchMenuUrl', () => {
  const menus = [node({
    menuUrl: '/support', children: [node({ menuId: 2, menuUrl: '/support/notices' })],
  })]

  it('상세/수정 화면은 가장 길게 일치하는 목록 메뉴를 활성으로', () => {
    expect(matchMenuUrl('/support/notices/3/edit', menus)).toBe('/support/notices')
    expect(matchMenuUrl('/support/noticesX', menus)).toBe('/support')
    expect(matchMenuUrl('/other', menus)).toBeUndefined()
  })
})

describe('isSafeExternalUrl', () => {
  it('http(s) 만 허용', () => {
    expect(isSafeExternalUrl('https://www.example.com')).toBe(true)
    expect(isSafeExternalUrl('javascript:alert(1)')).toBe(false)
    expect(isSafeExternalUrl('/relative')).toBe(false)
  })
})
