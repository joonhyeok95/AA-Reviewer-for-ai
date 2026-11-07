import { API_BASE, deleteResult, getResult, http, postResult, putResult } from '@/global/api/http'
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

/** 주보 PDF 주소 (원본 주보와 같은 A4 가로 3단 2장, 서버가 만든다 - 기기·브라우저와 관계없이 같은 결과) */
export function bulletinPdfUrl(id: number): string {
  return `${API_BASE}${BASE}/${id}/pdf`
}

/** 대표사진 최대 크기 (서버 BulletinPhotoService.MAX_MB 와 같게) */
export const PHOTO_MAX_MB = 10
export const PHOTO_ACCEPT = 'image/jpeg,image/png,image/gif,image/webp'

/** 대표사진 올리기 (있으면 바꾸기) - 형식은 서버가 내용으로 다시 판별 */
export async function uploadBulletinPhoto(id: number, file: File): Promise<MessageResponse<null>> {
  const form = new FormData()
  form.append('file', file)
  const response = await http.post<MessageResponse<null>>(`${BASE}/${id}/photo`, form)
  return response.data
}

export function deleteBulletinPhoto(id: number): Promise<MessageResponse<null>> {
  return deleteResult(`${BASE}/${id}/photo`)
}

/** 대표사진 주소 (img src, SESSION 쿠키 사용). 수정일시로 캐시 갱신 */
export function bulletinPhotoUrl(bulletin: { bulletinId: number; updatedAt: string }): string {
  return `${API_BASE}${BASE}/${bulletin.bulletinId}/photo?v=${encodeURIComponent(bulletin.updatedAt)}`
}
