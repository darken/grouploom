import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import PreferenceRow from '../PreferenceRow.vue'

function mountRow(props: Record<string, unknown> = {}) {
  return mount(PreferenceRow, {
    props: { name: 'Practice', description: 'Tuesdays at the club', color: 'blue', modelValue: true, ...props },
  })
}

describe('PreferenceRow', () => {
  it('shows the name, description and color dot, and a switch named after the row', () => {
    const wrapper = mountRow()

    expect(wrapper.text()).toContain('Practice')
    expect(wrapper.text()).toContain('Tuesdays at the club')
    expect(wrapper.get('[data-dot]').classes()).toContain('bg-blue-500')
    const toggle = wrapper.get('[role=switch]')
    expect(toggle.attributes('aria-checked')).toBe('true')
    const labelledBy = toggle.attributes('aria-labelledby')!
    expect(wrapper.get(`#${labelledBy}`).text()).toBe('Practice')
    expect(wrapper.get(`#${toggle.attributes('aria-describedby')!}`).text()).toBe('Tuesdays at the club')
  })

  it('works without a description', () => {
    const wrapper = mountRow({ description: undefined })

    expect(wrapper.get('[role=switch]').attributes('aria-describedby')).toBeUndefined()
  })

  it('toggles when anywhere on the row is clicked', async () => {
    const wrapper = mountRow()

    await wrapper.get('[data-name]').trigger('click')
    await wrapper.get('[data-dot]').trigger('click')
    await wrapper.trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([[false], [false], [false]])
  })

  it('toggles only once when the switch itself is clicked', async () => {
    const wrapper = mountRow({ modelValue: false })

    await wrapper.get('[role=switch]').trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([[true]])
  })

  it('does nothing when disabled, and looks dimmed', async () => {
    const wrapper = mountRow({ disabled: true })

    await wrapper.get('[data-name]').trigger('click')
    await wrapper.get('[role=switch]').trigger('click')

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.get('[role=switch]').attributes('disabled')).toBeDefined()
    expect(wrapper.classes()).toContain('opacity-50')
  })

  it('has a row at least 44px tall', () => {
    expect(mountRow().classes()).toContain('min-h-[3.25rem]')
  })
})
