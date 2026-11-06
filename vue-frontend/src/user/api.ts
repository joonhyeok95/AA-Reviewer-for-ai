import { getResult, putResult } from '@/global/api/http'
import type { OrgWorkplace, UserLang } from './types'

export function fetchOrgTree(): Promise<OrgWorkplace[]> {
  return getResult<OrgWorkplace[]>('/api/v1/org/tree')
}

export async function updateMyLang(langCd: string): Promise<UserLang> {
  return (await putResult<UserLang>('/api/v1/users/me/lang', { langCd })).result
}
