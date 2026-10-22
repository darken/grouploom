import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import DurationSelect from '../DurationSelect.vue'

describe('DurationSelect', () => {
  it('offers the presets, None and Custom', () => {
    const wrapper = mount(DurationSelect, { props: { modelValue: undefined } })

    expect(wrapper.findAll('option').map((o) => o.text())).toEqual([
      'None', '30 min', '1 hr', '1.5 hr', '2 hr', '3 hr', '4 hr', 'All day', 'Custom…',
    ])
  })

  it('emits the minutes of a preset and undefined for None', async () => {
    const wrapper = mount(DurationSelect, { props: { modelValue: undefined } })

    await wrapper.find('select').setValue('90')
    await wrapper.find('select').setValue('')

    expect(wrapper.emitted('update:modelValue')).toEqual([[90], [undefined]])
  })

  it('selects the matching preset for a known value', () => {
    const wrapper = mount(DurationSelect, { props: { modelValue: 1440 } })

    expect((wrapper.find('select').element as HTMLSelectElement).value).toBe('1440')
    expect(wrapper.find('input[type=number]').exists()).toBe(false)
  })

  it('shows the custom number field for a non-preset value and emits typed minutes', async () => {
    const wrapper = mount(DurationSelect, { props: { modelValue: 45 } })

    const input = wrapper.find('input[type=number]')
    expect(input.exists()).toBe(true)
    expect((input.element as HTMLInputElement).value).toBe('45')

    await input.setValue('50')

    expect(wrapper.emitted('update:modelValue')?.slice(-1)[0]).toEqual([50])
  })

  it('reveals the custom field when Custom… is chosen', async () => {
    const wrapper = mount(DurationSelect, { props: { modelValue: undefined } })

    await wrapper.find('select').setValue('custom')

    expect(wrapper.find('input[type=number]').exists()).toBe(true)
  })
})
