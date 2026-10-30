import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import AttendanceSummary from '../AttendanceSummary.vue'

const summary = { going: 12, notGoing: 3, noReply: 9, members: 24 }

function mountSummary(filter: 'ALL' | 'GOING' | 'NOT_GOING' | 'NO_REPLY' = 'ALL') {
  return mount(AttendanceSummary, { props: { summary, filter } })
}

describe('AttendanceSummary', () => {
  it('shows the three figures on one line, in order', () => {
    const wrapper = mountSummary()

    expect(wrapper.text().replace(/\s+/g, ' ').replace(/\s*·\s*/g, ' · ')).toBe("12 Going · 3 Can't go · 9 No reply")
    expect(wrapper.findAll('button')).toHaveLength(3)
  })

  it('sets the numbers in tabular figures', () => {
    for (const number of mountSummary().findAll('[data-count]')) expect(number.classes()).toContain('tabular-nums')
  })

  it('filters the list by the figure that is tapped', async () => {
    const wrapper = mountSummary()
    const [going, notGoing, noReply] = wrapper.findAll('button')

    await going!.trigger('click')
    await notGoing!.trigger('click')
    await noReply!.trigger('click')

    expect(wrapper.emitted('select')).toEqual([['GOING'], ['NOT_GOING'], ['NO_REPLY']])
  })

  it('shows all members again when the active figure is tapped a second time', async () => {
    const wrapper = mountSummary('GOING')

    await wrapper.findAll('button')[0]!.trigger('click')

    expect(wrapper.emitted('select')).toEqual([['ALL']])
  })

  it('marks the figure that matches the current filter as pressed', () => {
    const pressed = (filter: 'ALL' | 'GOING' | 'NOT_GOING' | 'NO_REPLY') =>
      mountSummary(filter).findAll('button').map((b) => b.attributes('aria-pressed'))

    expect(pressed('ALL')).toEqual(['false', 'false', 'false'])
    expect(pressed('NOT_GOING')).toEqual(['false', 'true', 'false'])
  })

  it('has touch targets of at least 44px', () => {
    for (const button of mountSummary().findAll('button')) expect(button.classes().join(' ')).toMatch(/min-h-11/)
  })
})
