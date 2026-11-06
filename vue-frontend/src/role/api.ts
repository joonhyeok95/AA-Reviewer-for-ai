import { deleteResult, getResult, postResult, putResult } from '@/global/api/http'
import type { MessageResponse } from '@/global/api/types'
import type {
  GrantTargetType, RoleAdmin, RoleGrantHistory, RoleGrants, RoleGrantSaveRequest, RoleMenuItem,
  RoleMenus,
  RoleSaveRequest,
} from './types'

const BASE = '/api/v1/roles'

export function fetchRoles(): Promise<RoleAdmin[]> {
  return getResult<RoleAdmin[]>(BASE)
}

export function createRole(body: RoleSaveRequest): Promise<MessageResponse<RoleAdmin>> {
  return postResult<RoleAdmin>(BASE, body)
}

export function updateRole(roleId: number, body: RoleSaveRequest)
  : Promise<MessageResponse<RoleAdmin>> {
  return putResult<RoleAdmin>(`${BASE}/${roleId}`, body)
}

export function deleteRole(roleId: number): Promise<MessageResponse<null>> {
  return deleteResult(`${BASE}/${roleId}`)
}

export function fetchRoleMenus(roleId: number): Promise<RoleMenus> {
  return getResult<RoleMenus>(`${BASE}/${roleId}/menus`)
}

/** 메뉴 접근 저장 (전체 교체) */
export function saveRoleMenus(roleId: number, items: RoleMenuItem[])
  : Promise<MessageResponse<RoleMenus>> {
  return putResult<RoleMenus>(`${BASE}/${roleId}/menus`, { items })
}

export function fetchRoleGrants(roleId: number): Promise<RoleGrants> {
  return getResult<RoleGrants>(`${BASE}/${roleId}/grants`)
}

export function createRoleGrant(roleId: number, body: RoleGrantSaveRequest)
  : Promise<MessageResponse<RoleGrants>> {
  return postResult<RoleGrants>(`${BASE}/${roleId}/grants`, body)
}

export function updateRoleGrant(roleId: number, type: GrantTargetType, grantId: number,
  body: RoleGrantSaveRequest): Promise<MessageResponse<RoleGrants>> {
  return putResult<RoleGrants>(`${BASE}/${roleId}/grants/${type}/${grantId}`, body)
}

export function deleteRoleGrant(roleId: number, type: GrantTargetType, grantId: number)
  : Promise<MessageResponse<null>> {
  return deleteResult(`${BASE}/${roleId}/grants/${type}/${grantId}`)
}

/** 부여 변경 이력 (최신순 - 부여·수정·회수, 회수된 부여도 남는다) */
export function fetchRoleGrantHistories(roleId: number): Promise<RoleGrantHistory[]> {
  return getResult<RoleGrantHistory[]>(`${BASE}/${roleId}/grant-histories`)
}
