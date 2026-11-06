import { deleteResult, getResult, postResult, putResult } from '@/global/api/http'
import type { MessageResponse, PageQuery, PageResponse } from '@/global/api/types'
import type { Sample, SampleSaveRequest, SampleSearch } from './types'

const BASE = '/api/v1/samples'

export function fetchSamples(search: SampleSearch, page: PageQuery): Promise<PageResponse<Sample>> {
  return getResult<PageResponse<Sample>>(BASE, {
    params: { ...compact(search), ...page },
  })
}

export function fetchSample(id: number): Promise<Sample> {
  return getResult<Sample>(`${BASE}/${id}`)
}

export function createSample(body: SampleSaveRequest): Promise<MessageResponse<Sample>> {
  return postResult<Sample>(BASE, body)
}

export function updateSample(id: number, body: SampleSaveRequest): Promise<MessageResponse<Sample>> {
  return putResult<Sample>(`${BASE}/${id}`, body)
}

export function deleteSample(id: number): Promise<MessageResponse<null>> {
  return deleteResult(`${BASE}/${id}`)
}

function compact(search: SampleSearch): Record<string, string> {
  return Object.fromEntries(
    Object.entries(search).filter(([, value]) => value !== undefined && value !== ''),
  ) as Record<string, string>
}
