import { describe, expect, it } from 'vitest'
import type { CalendarOccurrence } from '@/calendar/types'
import {
  celebrationNews, cleanNews, emptyNews, fromGrid, nextWeekForm, shiftGrid, toGrid, weekOf,
} from './form'
import type { BulletinDetail, DutyItem } from './types'

const duties: DutyItem[] = [
  { roleNm: '식사', dutyDt: '2026-11-01', members: '가람' },
  { roleNm: '식사', dutyDt: '2026-11-08', members: '나래' },
  { roleNm: '안내', dutyDt: '2026-11-01', members: '다온' },
  { roleNm: '안내', dutyDt: '2026-11-08', members: null },
]

function detail(): BulletinDetail {
  return {
    bulletinId: 1, worshipDt: '2026-11-08', volumeNo: 9, issueNo: 45, title: null, themeVerse: '말씀',
    themeVerseRef: '엡 2:15', sermonTitle: '설교', sermonScripture: '약 5:12', preacherNm: '목사',
    sermonMemo: '메모', statusCd: 'PUBLISHED', orders: [{ itemNm: '찬양', detail: '8장' }],
    news: [{ title: '기도제목', content: null, scheduleText: null, highlightYn: 'N', children: [] }],
    duties, catechismUnits: [], liturgyTexts: [], updatedBy: 'admin', updatedAt: '2026-11-08T10:00:00',
  }
}

describe('주보 화면 변환', () => {
  it('섬김위원 칸 목록 ↔ 표 (역할은 나온 순서, 날짜는 오름차순, 빈 칸 유지)', () => {
    const grid = toGrid(duties)
    expect(grid.dates).toEqual(['2026-11-01', '2026-11-08'])
    expect(grid.roles.map((role) => role.roleNm)).toEqual(['식사', '안내'])
    expect(fromGrid(grid)).toHaveLength(4)
    expect(fromGrid({ ...grid, roles: [...grid.roles, { roleNm: ' ', members: {} }] })).toHaveLength(4)
  })

  it('섬김위원 한 주 밀기 : 가장 오래된 날짜를 빼고 마지막 +7일 빈 열 추가', () => {
    const shifted = shiftGrid(toGrid(duties))
    expect(shifted.dates).toEqual(['2026-11-08', '2026-11-15'])
    expect(shifted.roles[0].members).toEqual({ '2026-11-08': '나래', '2026-11-15': '' })
  })

  it('다음 주 주보 : 예배일 +7, 호 +1, 작성 중, 설교 제목·메모·교리문답 비움', () => {
    const form = nextWeekForm(detail())
    expect(form.worshipDt).toBe('2026-11-15')
    expect(form.issueNo).toBe(46)
    expect(form.statusCd).toBe('DRAFT')
    expect(form.sermonTitle).toBe('')
    expect(form.sermonMemo).toBe('')
    expect(form.catechismUnitIds).toEqual([])
    expect(form.orders).toEqual([{ itemNm: '찬양', detail: '8장' }])
  })

  it('교회소식 정리 : 제목·내용·하위가 모두 빈 항목은 뺀다 (하위부터)', () => {
    const nodes = [
      { ...emptyNews(), title: '섹션', children: [emptyNews(), { ...emptyNews(), content: '내용' }] },
      { ...emptyNews(), children: [emptyNews()] },
    ]
    const cleaned = cleanNews(nodes)
    expect(cleaned).toHaveLength(1)
    expect(cleaned[0].children).toHaveLength(1)
  })

  it('예배일이 속한 주 (일 ~ 토)', () => {
    expect(weekOf('2026-11-08')).toEqual({ from: '2026-11-08', to: '2026-11-14' })
    expect(weekOf('2026-11-11')).toEqual({ from: '2026-11-08', to: '2026-11-14' })
  })

  it('달력 → 축하 섹션 : 유형별로 "이름(일 요일)" 묶음, 제목의 " 생일" 은 뺀다', () => {
    const occurrence = (eventTypeCd: string, title: string, startDt: string): CalendarOccurrence => ({
      eventId: 1, eventTypeCd, title, startDt, endDt: startDt, repeatYearlyYn: 'Y', yearsSince: 0,
      originStartDt: startDt, memo: null,
    })
    const section = celebrationNews([
      occurrence('BIRTHDAY', '가람 생일', '2026-11-15'),
      occurrence('BIRTHDAY', '나래 생일', '2026-11-20'),
      occurrence('WEDDING', '다온/라희 결혼기념일', '2026-11-11'),
      occurrence('HOLIDAY', '휴일', '2026-11-12'),
    ], { BIRTHDAY: '생일', WEDDING: '결혼기념일' }, () => '요일', '축하')
    expect(section.title).toBe('축하')
    expect(section.children.map((child) => [child.title, child.content])).toEqual([
      ['생일', '가람(15 요일), 나래(20 요일)'],
      ['결혼기념일', '다온/라희(11 요일)'],
    ])
  })
})
