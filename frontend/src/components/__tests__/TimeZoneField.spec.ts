import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import TimeZoneField from '../TimeZoneField.vue'

describe('TimeZoneField', () => {
  it('is collapsed by default and shows the current zone with a Change button', () => {
    const wrapper = mount(TimeZoneField, { props: { modelValue: 'Europe/Madrid' } })

    expect(wrapper.text()).toContain('Time zone: Europe/Madrid')
    expect(wrapper.find('select').exists()).toBe(false)
    expect(wrapper.findAll('button').map((b) => b.text())).toEqual(['Change'])
  })

  it('expands into a select of zones with the current one selected', async () => {
    const wrapper = mount(TimeZoneField, { props: { modelValue: 'Europe/Madrid' } })

    await wrapper.find('button').trigger('click')

    const select = wrapper.find('select')
    expect(select.attributes('aria-label')).toBe('Time zone')
    expect((select.element as HTMLSelectElement).value).toBe('Europe/Madrid')
    expect(select.findAll('option').length).toBeGreaterThan(50)
    expect(select.find('option[value="America/New_York"]').exists()).toBe(true)
  })

  it('emits the chosen zone', async () => {
    const wrapper = mount(TimeZoneField, { props: { modelValue: 'Europe/Madrid' } })
    await wrapper.find('button').trigger('click')

    await wrapper.find('select').setValue('Asia/Tokyo')

    expect(wrapper.emitted('update:modelValue')).toEqual([['Asia/Tokyo']])
  })

  it('shows a server error under the field', () => {
    const wrapper = mount(TimeZoneField, { props: { modelValue: 'Europe/Madrid', error: 'must be a valid time zone' } })

    expect(wrapper.text()).toContain('must be a valid time zone')
  })
})
