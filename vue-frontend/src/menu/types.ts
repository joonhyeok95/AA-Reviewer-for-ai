import type { YesNo } from '@/code/types'

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

/** GET /api/v1/menus - 메뉴 트리 (관리, 미사용·미노출 포함) */
export interface MenuAdminNode {
  menuId: number
  parentMenuId: number | null
  menuNm: string
  moduleCd: string | null
  viewTypeCd: MenuViewType
  menuUrl: string | null
  permCd: string | null
  sortOrder: number
  newWindowYn: YesNo
  adminMenuYn: YesNo
  sideMenuYn: YesNo
  displayYn: YesNo
  useYn: YesNo
  children: MenuAdminNode[]
}

/** 언어별 메뉴명·본문 */
export interface MenuI18n {
  langCd: string
  menuNm: string
  menuContent: string | null
}

/** GET /api/v1/menus/{id} - 메뉴 상세 */
export interface MenuDetail {
  menuId: number
  parentMenuId: number | null
  moduleCd: string | null
  viewTypeCd: MenuViewType
  menuUrl: string | null
  permCd: string | null
  sortOrder: number
  newWindowYn: YesNo
  adminMenuYn: YesNo
  sideMenuYn: YesNo
  displayYn: YesNo
  useYn: YesNo
  i18n: MenuI18n[]
  /** 접근 권한이 설정된 권한 수 */
  roleCnt: number
  childCnt: number
}

export interface MenuSaveRequest {
  parentMenuId: number | null
  moduleCd: string | null
  viewTypeCd: MenuViewType
  menuUrl: string | null
  permCd: string | null
  sortOrder: number
  newWindowYn: YesNo
  adminMenuYn: YesNo
  sideMenuYn: YesNo
  displayYn: YesNo
  useYn: YesNo
  i18n: MenuI18n[]
}

/** GET /api/v1/menus/{id}/content - 본문 메뉴 내용 */
export interface MenuContent {
  menuId: number
  menuNm: string | null
  langCd: string
  fallback: boolean
  contentHtml: string | null
}
