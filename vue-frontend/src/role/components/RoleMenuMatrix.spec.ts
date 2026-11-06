import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { i18n } from '@/global/i18n'
import type { RoleMenuNode } from '@/role/types'
import RoleMenuMatrix from './RoleMenuMatrix.vue'

function node(menuId: number, children: RoleMenuNode[] = []): RoleMenuNode {
  return {
    menuId, menuNm: `m${menuId}`, viewTypeCd: 'MENU', permCd: null, useYn: 'Y', displayYn: 'Y',
    listYn: 'N', addYn: 'N', editYn: 'N', delYn: 'N', children,
  }
}

const MENUS = [node(1, [node(2, [node(3)])]), node(9)]

function render() {
  return mount(RoleMenuMatrix, {
    props: { menus: MENUS, disabled: false, saving: false },
    global: { plugins: [i18n] },
  })
}

async function check(wrapper: ReturnType<typeof render>, id: string, on = true) {
  await wrapper.find(`[data-test="rm-${id}"]`).setValue(on)
}

describe('RoleMenuMatrix', () => {
  it('하위 메뉴의 등록을 켜면 그 메뉴 조회와 상위 메뉴 조회가 함께 켜진다', async () => {
    const wrapper = render()
    await check(wrapper, '3-addYn')

    expect((wrapper.find('[data-test="rm-3-listYn"]').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.find('[data-test="rm-2-listYn"]').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.find('[data-test="rm-1-listYn"]').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.find('[data-test="rm-9-listYn"]').element as HTMLInputElement).checked).toBe(false)
  })

  it('조회를 끄면 그 메뉴의 등록/수정/삭제도 꺼진다', async () => {
    const wrapper = render()
    await check(wrapper, '3-editYn')
    await check(wrapper, '3-listYn', false)

    expect((wrapper.find('[data-test="rm-3-editYn"]').element as HTMLInputElement).checked).toBe(false)
  })

  it('저장 시 하나라도 켜진 메뉴만 보낸다', async () => {
    const wrapper = render()
    await check(wrapper, '9-delYn')
    await wrapper.find('[data-test="role-menus-save"]').trigger('click')

    expect(wrapper.emitted('save')?.[0]).toEqual([[
      { menuId: 9, listYn: 'Y', addYn: 'N', editYn: 'N', delYn: 'Y' },
    ]])
  })
})
