import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { i18n } from '@/global/i18n'
import UiPagination from './UiPagination.vue'

function render(page: number, totalPages: number) {
  return mount(UiPagination, { props: { page, totalPages }, global: { plugins: [i18n] } })
}

describe('UiPagination', () => {
  it('한 페이지면 표시하지 않는다', () => {
    expect(render(0, 1).find('nav').exists()).toBe(false)
  })

  it('현재 페이지 주변 5개를 1부터 표시하고, 누르면 0부터인 페이지로 알린다', async () => {
    const wrapper = render(5, 20)
    const numbers = wrapper.findAll('.pager-btn.num').map((button) => button.text())
    expect(numbers).toEqual(['4', '5', '6', '7', '8'])
    expect(wrapper.find('[aria-current="page"]').text()).toBe('6')

    await wrapper.findAll('.pager-btn.num')[0]!.trigger('click')
    expect(wrapper.emitted('change')?.[0]).toEqual([3])
  })

  it('첫 페이지에서는 이전 버튼 비활성', () => {
    const wrapper = render(0, 3)
    expect(wrapper.findAll('.pager-btn')[0]!.attributes('disabled')).toBeDefined()
  })
})
