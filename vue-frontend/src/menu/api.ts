import { deleteResult, getResult, postResult, putResult } from '@/global/api/http'
import type { MessageResponse } from '@/global/api/types'
import type {
  MenuAdminNode, MenuContent, MenuDetail, MenuNode, MenuSaveRequest,
} from './types'

const BASE = '/api/v1/menus'

export function fetchMyMenus(): Promise<MenuNode[]> {
  return getResult<MenuNode[]>(`${BASE}/me`)
}

/** 본문형(CONTENT) 메뉴 내용 - 내 메뉴에 있는 메뉴만 */
export function fetchMenuContent(menuId: number): Promise<MenuContent> {
  return getResult<MenuContent>(`${BASE}/${menuId}/content`)
}

// ---------------------------------------------------------------- 메뉴 관리 (menu.*)

export function fetchMenuTree(): Promise<MenuAdminNode[]> {
  return getResult<MenuAdminNode[]>(BASE)
}

export function fetchMenu(menuId: number): Promise<MenuDetail> {
  return getResult<MenuDetail>(`${BASE}/${menuId}`)
}

export function createMenu(body: MenuSaveRequest): Promise<MessageResponse<MenuDetail>> {
  return postResult<MenuDetail>(BASE, body)
}

export function updateMenu(menuId: number, body: MenuSaveRequest)
  : Promise<MessageResponse<MenuDetail>> {
  return putResult<MenuDetail>(`${BASE}/${menuId}`, body)
}

export function deleteMenu(menuId: number): Promise<MessageResponse<null>> {
  return deleteResult(`${BASE}/${menuId}`)
}

/** 같은 상위 메뉴 안의 순서 (목록 순서대로 1, 2, 3 …) */
export function reorderMenus(parentMenuId: number | null, menuIds: number[])
  : Promise<MessageResponse<null>> {
  return putResult<null>(`${BASE}/order`, { parentMenuId, menuIds })
}
