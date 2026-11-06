import { API_BASE, deleteResult, getResult, http, postResult, putResult } from '@/global/api/http'
import type { MessageResponse, PageQuery, PageResponse } from '@/global/api/types'
import type {
  NoticeDetail, NoticeEdit, NoticeFile, NoticeMailHist, NoticeSaveRequest, NoticeSearch,
  NoticeSummary,
} from './types'

const BASE = '/api/v1/notices'

export function fetchNotices(search: NoticeSearch, page: PageQuery)
  : Promise<PageResponse<NoticeSummary>> {
  const params: Record<string, unknown> = { ...page }
  if (search.keyword?.trim()) params.keyword = search.keyword.trim()
  if (search.categoryCd) params.categoryCd = search.categoryCd
  if (search.includeScheduled) params.includeScheduled = true
  return getResult<PageResponse<NoticeSummary>>(BASE, { params })
}

export function fetchPopups(): Promise<NoticeSummary[]> {
  return getResult<NoticeSummary[]>(`${BASE}/popups`)
}

/** lang : 언어 전환 (없으면 사용자 언어 → 대표 언어) */
export function fetchNotice(id: number, lang?: string): Promise<NoticeDetail> {
  return getResult<NoticeDetail>(`${BASE}/${id}`, { params: lang ? { lang } : {} })
}

export function fetchNoticeForEdit(id: number): Promise<NoticeEdit> {
  return getResult<NoticeEdit>(`${BASE}/${id}/edit`)
}

export function createNotice(body: NoticeSaveRequest): Promise<MessageResponse<NoticeEdit>> {
  return postResult<NoticeEdit>(BASE, body)
}

export function updateNotice(id: number, body: NoticeSaveRequest)
  : Promise<MessageResponse<NoticeEdit>> {
  return putResult<NoticeEdit>(`${BASE}/${id}`, body)
}

export function deleteNotice(id: number): Promise<MessageResponse<null>> {
  return deleteResult(`${BASE}/${id}`)
}

export async function uploadNoticeFiles(id: number, files: File[]): Promise<NoticeFile[]> {
  const form = new FormData()
  files.forEach((file) => form.append('files', file))
  const response = await http.post<MessageResponse<NoticeFile[]>>(`${BASE}/${id}/files`, form)
  return response.data.result
}

export function deleteNoticeFile(id: number, fileId: number): Promise<MessageResponse<null>> {
  return deleteResult(`${BASE}/${id}/files/${fileId}`)
}

/** 다운로드는 브라우저 링크로 (SESSION 쿠키 사용, Content-Disposition 파일명) */
export function noticeFileUrl(id: number, fileId: number): string {
  return `${API_BASE}${BASE}/${id}/files/${fileId}`
}

export function fetchMailHistories(id: number): Promise<NoticeMailHist[]> {
  return getResult<NoticeMailHist[]>(`${BASE}/${id}/mail-histories`)
}
