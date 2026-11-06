import { deleteResult, getResult, postResult, putResult } from '@/global/api/http'
import type { MessageResponse } from '@/global/api/types'
import type {
  Code, CodeAdmin, CodeGroupAdmin, CodeGroupSaveRequest, CodeSaveRequest, Lang,
} from './types'

export function fetchLangs(): Promise<Lang[]> {
  return getResult<Lang[]>('/api/v1/langs')
}

export function fetchCodes(groupCd: string): Promise<Code[]> {
  return getResult<Code[]>(`/api/v1/codes/${encodeURIComponent(groupCd)}`)
}

// ---------------------------------------------------------------- 공통코드 관리 (code.*)

const GROUPS = '/api/v1/code-groups'

export function fetchCodeGroups(): Promise<CodeGroupAdmin[]> {
  return getResult<CodeGroupAdmin[]>(GROUPS)
}

export function createCodeGroup(body: CodeGroupSaveRequest): Promise<MessageResponse<CodeGroupAdmin>> {
  return postResult<CodeGroupAdmin>(GROUPS, body)
}

export function updateCodeGroup(groupCd: string, body: CodeGroupSaveRequest)
  : Promise<MessageResponse<CodeGroupAdmin>> {
  return putResult<CodeGroupAdmin>(`${GROUPS}/${encodeURIComponent(groupCd)}`, body)
}

export function deleteCodeGroup(groupCd: string): Promise<MessageResponse<null>> {
  return deleteResult(`${GROUPS}/${encodeURIComponent(groupCd)}`)
}

export function fetchAdminCodes(groupCd: string): Promise<CodeAdmin[]> {
  return getResult<CodeAdmin[]>(`${GROUPS}/${encodeURIComponent(groupCd)}/codes`)
}

export function createCode(groupCd: string, body: CodeSaveRequest)
  : Promise<MessageResponse<CodeAdmin>> {
  return postResult<CodeAdmin>(`${GROUPS}/${encodeURIComponent(groupCd)}/codes`, body)
}

export function updateCode(groupCd: string, code: string, body: CodeSaveRequest)
  : Promise<MessageResponse<CodeAdmin>> {
  return putResult<CodeAdmin>(
    `${GROUPS}/${encodeURIComponent(groupCd)}/codes/${encodeURIComponent(code)}`, body)
}

export function deleteCode(groupCd: string, code: string): Promise<MessageResponse<null>> {
  return deleteResult(`${GROUPS}/${encodeURIComponent(groupCd)}/codes/${encodeURIComponent(code)}`)
}
