import { getResult, putResult } from '@/global/api/http'
import type { OrgWorkplace, UserLang, UserSummary } from './types'

export function fetchOrgTree(): Promise<OrgWorkplace[]> {
  return getResult<OrgWorkplace[]>('/api/v1/org/tree')
}

export async function updateMyLang(langCd: string): Promise<UserLang> {
  return (await putResult<UserLang>('/api/v1/users/me/lang', { langCd })).result
}

/** 사용자 검색 (ID·이름 일부, 최대 20건) - role.list */
export function searchUsers(keyword: string): Promise<UserSummary[]> {
  return getResult<UserSummary[]>('/api/v1/users', { params: { keyword } })
}
