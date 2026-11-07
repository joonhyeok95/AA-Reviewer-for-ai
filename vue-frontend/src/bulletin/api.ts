import { deleteResult, getResult, postResult, putResult } from '@/global/api/http'
import type { MessageResponse, PageQuery, PageResponse } from '@/global/api/types'
import type { BulletinDetail, BulletinSaveRequest, BulletinSummary } from './types'

const BASE = '/api/v1/bulletins'

export function fetchBulletins(page: PageQuery): Promise<PageResponse<BulletinSummary>> {
  return getResult<PageResponse<BulletinSummary>>(BASE, { params: page })
}

/** 이번 주 주보 (게시된 것) */
export function fetchCurrentBulletin(): Promise<BulletinDetail> {
  return getResult<BulletinDetail>(`${BASE}/current`)
}

export function fetchBulletin(id: number): Promise<BulletinDetail> {
  return getResult<BulletinDetail>(`${BASE}/${id}`)
}

export function createBulletin(body: BulletinSaveRequest): Promise<MessageResponse<BulletinDetail>> {
  return postResult<BulletinDetail>(BASE, body)
}

export function updateBulletin(id: number, body: BulletinSaveRequest)
  : Promise<MessageResponse<BulletinDetail>> {
  return putResult<BulletinDetail>(`${BASE}/${id}`, body)
}

export function deleteBulletin(id: number): Promise<MessageResponse<null>> {
  return deleteResult(`${BASE}/${id}`)
}
