import { deleteResult, getResult, postResult, putResult } from '@/global/api/http'
import type { MessageResponse } from '@/global/api/types'
import type { LiturgyText, LiturgyTextSaveRequest } from './types'

const BASE = '/api/v1/liturgy-texts'

export function fetchLiturgyTexts(): Promise<LiturgyText[]> {
  return getResult<LiturgyText[]>(BASE)
}

export function createLiturgyText(body: LiturgyTextSaveRequest): Promise<MessageResponse<LiturgyText>> {
  return postResult<LiturgyText>(BASE, body)
}

export function updateLiturgyText(id: number, body: LiturgyTextSaveRequest)
  : Promise<MessageResponse<LiturgyText>> {
  return putResult<LiturgyText>(`${BASE}/${id}`, body)
}

export function deleteLiturgyText(id: number): Promise<MessageResponse<null>> {
  return deleteResult(`${BASE}/${id}`)
}
