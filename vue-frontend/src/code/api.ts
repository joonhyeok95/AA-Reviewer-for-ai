import { getResult } from '@/global/api/http'
import type { Code, Lang } from './types'

export function fetchLangs(): Promise<Lang[]> {
  return getResult<Lang[]>('/api/v1/langs')
}

export function fetchCodes(groupCd: string): Promise<Code[]> {
  return getResult<Code[]>(`/api/v1/codes/${encodeURIComponent(groupCd)}`)
}
