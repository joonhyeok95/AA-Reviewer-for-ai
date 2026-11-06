import type { LangNames, YesNo } from '@/code/types'

/** GET /api/v1/roles - 권한 (관리) */
export interface RoleAdmin {
  roleId: number
  roleCd: string
  roleNm: string
  names: LangNames
  /** 공통코드 ROLE_TYPE */
  roleTypeCd: string
  /** 1 시스템관리자 ~ 4 일반사용자 */
  roleLevel: number
  moduleCd: string | null
  roleDesc: string | null
  sortOrder: number
  /** 기본 권한 : 삭제·유형/레벨 변경 불가 */
  systemYn: YesNo
  useYn: YesNo
  userGrantCnt: number
  deptGrantCnt: number
  menuCnt: number
}

export interface RoleSaveRequest {
  roleCd: string
  names: LangNames
  roleTypeCd: string
  roleLevel: number
  moduleCd: string | null
  roleDesc: string
  sortOrder: number
  useYn: YesNo
}

/** 권한별 메뉴 접근 트리 */
export interface RoleMenuNode {
  menuId: number
  menuNm: string
  viewTypeCd: string
  /** 있으면 체크한 작업이 API 권한(permCd.list 등)이 된다 */
  permCd: string | null
  useYn: YesNo
  displayYn: YesNo
  listYn: YesNo
  addYn: YesNo
  editYn: YesNo
  delYn: YesNo
  children: RoleMenuNode[]
}

export interface RoleMenus {
  roleId: number
  roleCd: string
  /** 시스템관리자 권한 - 모든 메뉴·작업 허용 (설정 불필요) */
  allMenus: boolean
  menus: RoleMenuNode[]
}

export interface RoleMenuItem {
  menuId: number
  listYn: YesNo
  addYn: YesNo
  editYn: YesNo
  delYn: YesNo
}

export type GrantTargetType = 'USER' | 'DEPT'
export type GrantStatus = 'ACTIVE' | 'SCHEDULED' | 'EXPIRED'

export interface RoleGrant {
  grantId: number
  targetType: GrantTargetType
  targetId: string
  targetNm: string | null
  /** 사용자 주부서명 / 부서의 사업장명 */
  targetSubNm: string | null
  startDt: string
  /** 무기한 9999-12-31 */
  endDt: string
  grantReason: string | null
  status: GrantStatus
  updatedBy: string
  updatedAt: string
}

export type GrantAction = 'GRANT' | 'UPDATE' | 'REVOKE'

/** 권한 부여 변경 이력 (감사) */
export interface RoleGrantHistory {
  histId: number
  actionCd: GrantAction
  targetType: GrantTargetType
  targetId: string
  targetNm: string | null
  startDt: string
  endDt: string
  reason: string | null
  changedBy: string
  changedByNm: string | null
  changedAt: string
}

export interface RoleGrants {
  roleId: number
  users: RoleGrant[]
  depts: RoleGrant[]
}

export interface RoleGrantSaveRequest {
  targetType: GrantTargetType
  targetId: string
  startDt: string
  endDt: string | null
  grantReason: string
}
