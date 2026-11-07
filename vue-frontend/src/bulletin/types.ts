import type { UnitDetail } from '@/catechism/types'
import type { LiturgyText } from '@/liturgy/types'

export type YesNo = 'Y' | 'N'

/** 예배순서 항목 */
export interface OrderItem {
  itemNm: string
  detail: string | null
}

/** 교회소식 (섹션 → 항목 → 하위 항목, 최대 3단계) */
export interface NewsNode {
  title: string | null
  content: string | null
  scheduleText: string | null
  highlightYn: YesNo
  children: NewsNode[]
}

/** 섬김위원 칸 (역할 × 날짜 = 담당자) */
export interface DutyItem {
  roleNm: string
  dutyDt: string
  members: string | null
}

/** GET /api/v1/bulletins - 목록 */
export interface BulletinSummary {
  bulletinId: number
  worshipDt: string
  volumeNo: number | null
  issueNo: number | null
  title: string | null
  sermonTitle: string | null
  sermonScripture: string | null
  preacherNm: string | null
  /** 공통코드 BULLETIN_STATUS (DRAFT / PUBLISHED) */
  statusCd: string
  updatedBy: string
  updatedAt: string
}

/** GET /api/v1/bulletins/{id}, /current - 상세 (교리문답·전례문 본문 포함) */
export interface BulletinDetail {
  bulletinId: number
  worshipDt: string
  volumeNo: number | null
  issueNo: number | null
  title: string | null
  themeVerse: string | null
  themeVerseRef: string | null
  sermonTitle: string | null
  sermonScripture: string | null
  preacherNm: string | null
  sermonMemo: string | null
  statusCd: string
  orders: OrderItem[]
  news: NewsNode[]
  duties: DutyItem[]
  catechismUnits: UnitDetail[]
  liturgyTexts: LiturgyText[]
  updatedBy: string
  updatedAt: string
}

export interface BulletinSaveRequest {
  worshipDt: string
  volumeNo: number | null
  issueNo: number | null
  title: string
  themeVerse: string
  themeVerseRef: string
  sermonTitle: string
  sermonScripture: string
  preacherNm: string
  sermonMemo: string
  statusCd: string
  orders: OrderItem[]
  news: NewsNode[]
  duties: DutyItem[]
  catechismUnitIds: number[]
  liturgyTextIds: number[]
}

/** 섬김위원 표 (화면용 : 행 = 역할, 열 = 날짜) */
export interface DutyGrid {
  dates: string[]
  roles: { roleNm: string; members: Record<string, string> }[]
}
