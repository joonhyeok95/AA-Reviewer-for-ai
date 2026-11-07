import { addDays } from '@/calendar/dates'
import type { CalendarOccurrence } from '@/calendar/types'
import type {
  BulletinDetail, BulletinSaveRequest, DutyGrid, DutyItem, NewsNode, OrderItem,
} from './types'

/**
 * 주보 등록·수정 화면 상태 ↔ API 변환.
 *  - 섬김위원은 화면에서 표(역할 × 날짜)로 편집하고 저장할 때 칸 목록으로 바꾼다
 *  - 새 주보는 가장 최근 주보를 템플릿으로 : 내용은 모두 복사, 예배일 +7일, 호 +1, 작성 중
 */

/** 화면 상태 (섬김위원은 표) */
export interface BulletinForm extends Omit<BulletinSaveRequest, 'duties'> {
  duties: DutyGrid
}

export const MAX_NEWS_DEPTH = 3

export function emptyNews(): NewsNode {
  return { title: '', content: '', scheduleText: '', highlightYn: 'N', children: [] }
}

export function emptyOrder(): OrderItem {
  return { itemNm: '', detail: '' }
}

export function emptyForm(worshipDt: string): BulletinForm {
  return {
    worshipDt, volumeNo: null, issueNo: null, title: '', themeVerse: '', themeVerseRef: '',
    sermonTitle: '', sermonScripture: '', preacherNm: '', sermonMemo: '', statusCd: 'DRAFT',
    orders: [emptyOrder()], news: [], duties: { dates: [worshipDt], roles: [] },
    catechismUnitIds: [], liturgyTextIds: [],
  }
}

export function formFromDetail(detail: BulletinDetail): BulletinForm {
  return {
    worshipDt: detail.worshipDt, volumeNo: detail.volumeNo, issueNo: detail.issueNo,
    title: detail.title ?? '', themeVerse: detail.themeVerse ?? '',
    themeVerseRef: detail.themeVerseRef ?? '', sermonTitle: detail.sermonTitle ?? '',
    sermonScripture: detail.sermonScripture ?? '', preacherNm: detail.preacherNm ?? '',
    sermonMemo: detail.sermonMemo ?? '', statusCd: detail.statusCd,
    orders: detail.orders.map((order) => ({ ...order, detail: order.detail ?? '' })),
    news: cloneNews(detail.news),
    duties: toGrid(detail.duties),
    catechismUnitIds: detail.catechismUnits.map((unit) => unit.unitId),
    liturgyTextIds: detail.liturgyTexts.map((text) => text.textId),
  }
}

/**
 * 지난 주보를 템플릿으로 새 주보 : 내용(설교·말씀메모·예배순서·교회소식·섬김위원·교리문답·전례문)은 모두 그대로,
 * 예배일만 +7일(minDate 보다 이르면 minDate), 호 +1, 작성 중
 */
export function templateForm(detail: BulletinDetail, minDate?: string): BulletinForm {
  const form = formFromDetail(detail)
  const next = addDays(detail.worshipDt, 7)
  form.worshipDt = minDate && minDate > next ? minDate : next
  form.issueNo = detail.issueNo === null ? null : detail.issueNo + 1
  form.statusCd = 'DRAFT'
  return form
}

export function toSaveRequest(form: BulletinForm): BulletinSaveRequest {
  return {
    ...form,
    orders: form.orders.filter((order) => order.itemNm.trim()),
    news: cleanNews(form.news),
    duties: fromGrid(form.duties),
  }
}

// ------------------------------------------------------------------ 섬김위원 표

export function toGrid(duties: DutyItem[]): DutyGrid {
  const dates = [...new Set(duties.map((duty) => duty.dutyDt))].sort()
  const roles: DutyGrid['roles'] = []
  for (const duty of duties) {
    let role = roles.find((each) => each.roleNm === duty.roleNm)
    if (!role) {
      role = { roleNm: duty.roleNm, members: {} }
      roles.push(role)
    }
    role.members[duty.dutyDt] = duty.members ?? ''
  }
  return { dates, roles }
}

/** 빈 역할명은 빼고, 담당자가 빈 칸도 그대로 저장 (표 모양 유지) */
export function fromGrid(grid: DutyGrid): DutyItem[] {
  return grid.roles.filter((role) => role.roleNm.trim()).flatMap((role) =>
    grid.dates.filter(Boolean).map((dutyDt) => ({
      roleNm: role.roleNm.trim(), dutyDt, members: role.members[dutyDt]?.trim() || null,
    })))
}

/** 가장 오래된 날짜를 빼고 마지막 날짜 +7일을 추가 (4주 순환표) */
export function shiftGrid(grid: DutyGrid): DutyGrid {
  if (!grid.dates.length) return grid
  const last = grid.dates[grid.dates.length - 1]
  const dates = [...grid.dates.slice(grid.dates.length > 1 ? 1 : 0), addDays(last, 7)]
  return {
    dates,
    roles: grid.roles.map((role) => ({
      roleNm: role.roleNm,
      members: Object.fromEntries(dates.map((day) => [day, role.members[day] ?? ''])),
    })),
  }
}

// ------------------------------------------------------------------ 교회소식

export function cloneNews(nodes: NewsNode[]): NewsNode[] {
  return nodes.map((node) => ({
    title: node.title ?? '', content: node.content ?? '', scheduleText: node.scheduleText ?? '',
    highlightYn: node.highlightYn ?? 'N', children: cloneNews(node.children ?? []),
  }))
}

/** 제목·내용·하위가 모두 빈 항목은 뺀다 */
export function cleanNews(nodes: NewsNode[]): NewsNode[] {
  return nodes.map((node) => ({ ...node, children: cleanNews(node.children) }))
    .filter((node) => node.title?.trim() || node.content?.trim() || node.children.length)
}

/**
 * 달력(생일·결혼기념일)으로 교회소식 "축하" 섹션 만들기.
 * 예배일이 속한 주(일 ~ 토)의 일정을 "이름(15 주일)" 형식으로 묶는다.
 */
export function celebrationNews(occurrences: CalendarOccurrence[],
  typeNames: Record<string, string>, weekday: (iso: string) => string, title: string): NewsNode {
  const groups = new Map<string, string[]>()
  for (const item of occurrences) {
    if (!(item.eventTypeCd in typeNames)) continue
    const name = item.title.replace(/\s*(생일|결혼기념일)$/, '')
    const day = Number(item.startDt.slice(8, 10))
    groups.set(item.eventTypeCd,
      [...(groups.get(item.eventTypeCd) ?? []), `${name}(${day} ${weekday(item.startDt)})`])
  }
  return {
    ...emptyNews(),
    title,
    children: [...groups.entries()].map(([type, names]) => ({
      ...emptyNews(), title: typeNames[type], content: names.join(', '),
    })),
  }
}

/** 예배일이 속한 주의 일요일 ~ 토요일 */
export function weekOf(worshipDt: string): { from: string; to: string } {
  const [year, month, day] = worshipDt.split('-').map(Number)
  const offset = new Date(year, month - 1, day).getDay()
  const from = addDays(worshipDt, -offset)
  return { from, to: addDays(from, 6) }
}
