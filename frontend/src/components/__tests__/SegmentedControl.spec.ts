import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import SegmentedControl from '../SegmentedControl.vue'

const options = [
  { value: 'active', label: 'Active' },
  { value: 'archived', label: 'Archived' },
]

describe('SegmentedControl', () => {
  it('is a radiogroup with the current option checked', () => {
    const wrapper = mount(SegmentedControl, { props: { modelValue: 'archived', options, label: 'Show' } })

    expect(wrapper.find('[role=radiogroup]').attributes('aria-label')).toBe('Show')
    const checked = wrapper.findAll('[role=radio]').filter((r) => r.attributes('aria-checked') === 'true')
    expect(checked.map((r) => r.text())).toEqual(['Archived'])
  })

  it('emits the clicked option', async () => {
    const wrapper = mount(SegmentedControl, { props: { modelValue: 'active', options, label: 'Show' } })

    await wrapper.findAll('[role=radio]')[1]!.trigger('click')

    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['archived'])
  })

  it('moves with arrow keys', async () => {
    const wrapper = mount(SegmentedControl, { props: { modelValue: 'active', options, label: 'Show' }, attachTo: document.body })

    await wrapper.findAll('[role=radio]')[0]!.trigger('keydown', { key: 'ArrowRight' })

    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['archived'])
    wrapper.unmount()
  })
})
