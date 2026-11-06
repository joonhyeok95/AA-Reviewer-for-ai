export type YesNo = 'Y' | 'N'

export interface Sample {
  id: number
  name: string
  description: string | null
  useYn: YesNo
  createdBy: string
  createdAt: string
  updatedBy: string
  updatedAt: string
}

export interface SampleSearch {
  name?: string
  useYn?: YesNo | ''
}

/** 등록/수정 요청 (SampleCreateRequest / SampleUpdateRequest 동일 구조) */
export interface SampleSaveRequest {
  name: string
  description: string
  useYn: YesNo
}
