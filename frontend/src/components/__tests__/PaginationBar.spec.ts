import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import PaginationBar from '../PaginationBar.vue'

describe('PaginationBar', () => {
  it('is hidden when everything fits on one page', () => {
    const wrapper = mount(PaginationBar, { props: { page: 0, size: 20, total: 20 } })

    expect(wrapper.text()).toBe('')
  })

  it('shows the visible range and total', () => {
    const wrapper = mount(PaginationBar, { props: { page: 0, size: 20, total: 43 } })
    expect(wrapper.text()).toContain('1–20 of 43')

    const last = mount(PaginationBar, { props: { page: 2, size: 20, total: 43 } })
    expect(last.text()).toContain('41–43 of 43')
  })

  it('disables Previous on the first page and Next on the last', () => {
    const first = mount(PaginationBar, { props: { page: 0, size: 20, total: 43 } })
    const last = mount(PaginationBar, { props: { page: 2, size: 20, total: 43 } })

    const button = (w: typeof first, name: string) => w.findAll('button').find((b) => b.text() === name)!
    expect(button(first, 'Previous').attributes('disabled')).toBeDefined()
    expect(button(first, 'Next').attributes('disabled')).toBeUndefined()
    expect(button(last, 'Next').attributes('disabled')).toBeDefined()
  })

  it('emits the new page index', async () => {
    const wrapper = mount(PaginationBar, { props: { page: 1, size: 20, total: 43 } })

    await wrapper.findAll('button').find((b) => b.text() === 'Next')!.trigger('click')
    await wrapper.findAll('button').find((b) => b.text() === 'Previous')!.trigger('click')

    expect(wrapper.emitted('update:page')).toEqual([[2], [0]])
  })
})
