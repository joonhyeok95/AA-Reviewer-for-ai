/** 메뉴 유형 (공통코드 MENU_VIEW_TYPE) */
export type MenuViewType = 'MENU' | 'CONTENT' | 'IFRAME' | 'LINK'

export interface MenuPermission {
  list: boolean
  add: boolean
  edit: boolean
  del: boolean
}

/** GET /api/v1/menus/me - 사용자 메뉴 트리 (유효 권한, 사용자 언어) */
export interface MenuNode {
  menuId: number
  menuNm: string
  moduleCd: string | null
  viewTypeCd: MenuViewType
  menuUrl: string | null
  newWindow: boolean
  adminMenu: boolean
  sideMenu: boolean
  permission: MenuPermission
  children: MenuNode[]
}
