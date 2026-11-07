export type YesNo = 'Y' | 'N'

/**
 * 교리문답 → 단위 트리(부 → 주일 / 장) → 문답 → 답 단락 → 근거 성구
 */

/** 단위 트리 노드 (GET /api/v1/catechisms) */
export interface UnitNode {
  unitId: number
  parentUnitId: number | null
  /** 공통코드 CATECHISM_UNIT_TYPE (PART 부, LORDS_DAY 주일, CHAPTER 장) */
  unitTypeCd: string
  unitNo: number | null
  title: string | null
  sortOrder: number
  qaCount: number
  firstQuestionNo: number | null
  lastQuestionNo: number | null
  children: UnitNode[]
}

export interface Catechism {
  catechismId: number
  catechismCd: string
  title: string
  description: string | null
  sortOrder: number
  useYn: YesNo
  units: UnitNode[]
}

export interface CatechismSaveRequest {
  catechismCd: string
  title: string
  description: string
  sortOrder: number
  useYn: YesNo
}

export interface Proof {
  bibleRef: string
  bibleText: string | null
}

export interface Answer {
  content: string
  proofs: Proof[]
}

export interface Qa {
  qaId?: number
  questionNo: number | null
  question: string
  answers: Answer[]
}

export interface PathItem {
  unitId: number
  unitTypeCd: string
  unitNo: number | null
  title: string | null
}

/** GET /api/v1/catechisms/units/{unitId} - 단위 상세 */
export interface UnitDetail {
  unitId: number
  catechismId: number
  catechismTitle: string
  parentUnitId: number | null
  unitTypeCd: string
  unitNo: number | null
  title: string | null
  sortOrder: number
  path: PathItem[]
  qas: Qa[]
}

export interface UnitSaveRequest {
  parentUnitId: number | null
  unitTypeCd: string
  unitNo: number | null
  title: string
  sortOrder: number
  qas: Qa[]
}
