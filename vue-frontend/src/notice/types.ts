import type { YesNo } from '@/sample/types'

/** 제공 언어 배지 */
export interface LangBadge {
  langCd: string
  badge: string
}

/** 목록 항목 - 사용자 언어 버전(없으면 대표 언어 버전, fallback=true) */
export interface NoticeSummary {
  noticeId: number
  categoryCd: string
  categoryNm: string
  tagCd: string | null
  tagNm: string | null
  title: string
  displayLangCd: string
  fallback: boolean
  langs: LangBadge[]
  postDate: string
  /** 게시일시 전 (관리자만 조회) */
  scheduled: boolean
  popupYn: YesNo
  viewCnt: number
  fileCnt: number
}

export interface NoticeFile {
  fileId: number
  orgFileNm: string
  fileSize: number
  fileExt: string
}

/** 상세 - 표시 언어 = lang(전환) → 사용자 언어 → 대표 언어 */
export interface NoticeDetail {
  noticeId: number
  categoryCd: string
  categoryNm: string
  tagCd: string | null
  tagNm: string | null
  baseLangCd: string
  displayLangCd: string
  fallback: boolean
  langs: LangBadge[]
  title: string
  /** 서버에서 정화(jsoup)된 HTML */
  contentHtml: string
  postDate: string
  popupYn: YesNo
  popupStartDt: string | null
  popupEndDt: string | null
  viewCnt: number
  files: NoticeFile[]
  createdBy: string
  createdAt: string
  updatedAt: string
}

export type MailTargetType = 'WORKPLACE' | 'DEPT'

export interface NoticeI18n {
  langCd: string
  title: string
  contentHtml: string
}

export interface MailTarget {
  targetType: MailTargetType
  targetCd: string
  targetNm?: string
  includeSubYn: YesNo
}

/** 메일 상태 (공지 단위) READY → SENDING → DONE / FAIL */
export type NoticeMailStatus = 'READY' | 'SENDING' | 'DONE' | 'FAIL' | 'CANCEL'

/** 수정 화면 데이터 - 작성된 전체 언어 버전 */
export interface NoticeEdit {
  noticeId: number
  categoryCd: string
  tagCd: string | null
  baseLangCd: string
  postDate: string
  popupYn: YesNo
  popupStartDt: string | null
  popupEndDt: string | null
  mailSendTypeCd: string
  mailStatusCd: NoticeMailStatus | null
  mailSendDt: string | null
  /** 메일 발송 전이라 메일 설정 변경 가능 */
  mailChangeable: boolean
  i18n: NoticeI18n[]
  mailTargets: MailTarget[]
  files: NoticeFile[]
}

export interface NoticeSaveRequest {
  categoryCd: string
  tagCd: string | null
  baseLangCd: string | null
  postDate: string | null
  popupYn: YesNo
  popupStartDt: string | null
  popupEndDt: string | null
  mailSendTypeCd: string
  i18n: NoticeI18n[]
  mailTargets: Omit<MailTarget, 'targetNm'>[]
}

export interface NoticeSearch {
  keyword?: string
  categoryCd?: string
  includeScheduled?: boolean
}

export interface NoticeMailHist {
  histId: number
  receiverId: string
  langCd: string
  sendDt: string | null
  statusCd: string
  errorMsg: string | null
}
