import { describe, expect, it } from 'vitest'
import { langState, validateNotice, type NoticeFormInput } from './validation'

function input(partial: Partial<NoticeFormInput>): NoticeFormInput {
  return {
    langCds: ['ko', 'en'],
    contents: { ko: { title: '', contentHtml: '' }, en: { title: '', contentHtml: '' } },
    categoryCd: 'GENERAL', popupYn: 'N', popupStartDt: '', popupEndDt: '',
    mailSendTypeCd: 'NONE', mailTargetCount: 0, ...partial,
  }
}

describe('langState', () => {
  it('제목+본문 = filled, 하나만 = partial, 에디터 빈 태그만 = 비어 있음', () => {
    expect(langState({ title: '점검', contentHtml: '<p>내용</p>' })).toBe('filled')
    expect(langState({ title: '점검', contentHtml: '<p><br></p>' })).toBe('partial')
    expect(langState({ title: ' ', contentHtml: '<p></p>' })).toBe('empty')
  })
})

describe('validateNotice', () => {
  it('1개 언어만 입력해도 통과 (나머지 언어는 대표 언어로 대체 표시)', () => {
    const errors = validateNotice(input({
      contents: { ko: { title: '점검', contentHtml: '<p>점검</p>' }, en: { title: '', contentHtml: '' } },
    }))
    expect(errors).toEqual({})
  })

  it('아무 언어도 없으면 오류', () => {
    expect(validateNotice(input({})).summary).toBeTruthy()
  })

  it('일부만 입력한 언어는 언어별 오류', () => {
    const errors = validateNotice(input({
      contents: {
        ko: { title: '점검', contentHtml: '<p>점검</p>' }, en: { title: 'Notice', contentHtml: '' },
      },
    }))
    expect(Object.keys(errors.langs ?? {})).toEqual(['en'])
  })

  it('팝업 기간, 대상 지정 메일 대상 필수', () => {
    const errors = validateNotice(input({
      contents: { ko: { title: '점검', contentHtml: '<p>점검</p>' } },
      popupYn: 'Y', popupStartDt: '2026-11-10', popupEndDt: '2026-11-01',
      mailSendTypeCd: 'TARGET', mailTargetCount: 0,
    }))
    expect(errors.popup).toBeTruthy()
    expect(errors.mailTargets).toBeTruthy()
  })
})
