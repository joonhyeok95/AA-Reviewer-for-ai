export type YesNo = 'Y' | 'N'

/** 전례문 줄 (화자 : 공통코드 LITURGY_SPEAKER, null = 표시 안 함) */
export interface LiturgyLine {
  speakerCd: string | null
  content: string
}

/** GET /api/v1/liturgy-texts - 사도신경·주기도문·교독문 */
export interface LiturgyText {
  textId: number
  /** 공통코드 LITURGY_TYPE */
  textTypeCd: string
  title: string
  versionNm: string | null
  intro: string | null
  sortOrder: number
  useYn: YesNo
  lines: LiturgyLine[]
  updatedBy: string
  updatedAt: string
}

export interface LiturgyTextSaveRequest {
  textTypeCd: string
  title: string
  versionNm: string
  intro: string
  sortOrder: number
  useYn: YesNo
  lines: LiturgyLine[]
}
