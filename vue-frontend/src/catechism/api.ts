import { deleteResult, getResult, postResult, putResult } from '@/global/api/http'
import type { MessageResponse } from '@/global/api/types'
import type { Catechism, CatechismSaveRequest, UnitDetail, UnitSaveRequest } from './types'

const BASE = '/api/v1/catechisms'

export function fetchCatechisms(): Promise<Catechism[]> {
  return getResult<Catechism[]>(BASE)
}

export function createCatechism(body: CatechismSaveRequest): Promise<MessageResponse<Catechism>> {
  return postResult<Catechism>(BASE, body)
}

export function updateCatechism(id: number, body: CatechismSaveRequest)
  : Promise<MessageResponse<Catechism>> {
  return putResult<Catechism>(`${BASE}/${id}`, body)
}

export function deleteCatechism(id: number): Promise<MessageResponse<null>> {
  return deleteResult(`${BASE}/${id}`)
}

export function fetchUnit(unitId: number): Promise<UnitDetail> {
  return getResult<UnitDetail>(`${BASE}/units/${unitId}`)
}

export function createUnit(catechismId: number, body: UnitSaveRequest)
  : Promise<MessageResponse<UnitDetail>> {
  return postResult<UnitDetail>(`${BASE}/${catechismId}/units`, body)
}

export function updateUnit(unitId: number, body: UnitSaveRequest): Promise<MessageResponse<UnitDetail>> {
  return putResult<UnitDetail>(`${BASE}/units/${unitId}`, body)
}

export function deleteUnit(unitId: number): Promise<MessageResponse<null>> {
  return deleteResult(`${BASE}/units/${unitId}`)
}
