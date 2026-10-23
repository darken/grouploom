import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import DateTimeFields from '../DateTimeFields.vue'

const value = { startDate: '2026-11-02', startTime: '10:00', endDate: '2026-11-02', endTime: '11:30' }

function lastEmitted(wrapper: ReturnType<typeof mount>) {
  const events = wrapper.emitted('update:modelValue') as unknown[][]
  return events[events.length - 1]![0]
}

describe('DateTimeFields', () => {
  it('renders native date and time inputs, with five-minute steps for time', () => {
    const wrapper = mount(DateTimeFields, { props: { modelValue: value } })

    expect(wrapper.find('input[aria-label="Start date"]').attributes('type')).toBe('date')
    expect(wrapper.find('input[aria-label="End date"]').attributes('type')).toBe('date')
    expect(wrapper.find('input[aria-label="Start time"]').attributes('type')).toBe('time')
    expect(wrapper.find('input[aria-label="Start time"]').attributes('step')).toBe('300')
    expect((wrapper.find('input[aria-label="End time"]').element as HTMLInputElement).value).toBe('11:30')
  })

  it('shifts the end by the same amount when the start time changes, keeping the duration', async () => {
    const wrapper = mount(DateTimeFields, { props: { modelValue: value } })

    await wrapper.find('input[aria-label="Start time"]').setValue('12:00')

    expect(lastEmitted(wrapper)).toEqual({ startDate: '2026-11-02', startTime: '12:00', endDate: '2026-11-02', endTime: '13:30' })
  })

  it('shifts the end date when the start date changes', async () => {
    const wrapper = mount(DateTimeFields, { props: { modelValue: value } })

    await wrapper.find('input[aria-label="Start date"]').setValue('2026-11-05')

    expect(lastEmitted(wrapper)).toEqual({ startDate: '2026-11-05', startTime: '10:00', endDate: '2026-11-05', endTime: '11:30' })
  })

  it('carries the end over midnight when the shifted end passes it', async () => {
    const wrapper = mount(DateTimeFields, {
      props: { modelValue: { startDate: '2026-11-02', startTime: '23:00', endDate: '2026-11-02', endTime: '23:30' } },
    })

    await wrapper.find('input[aria-label="Start time"]').setValue('23:45')

    expect(lastEmitted(wrapper)).toEqual({ startDate: '2026-11-02', startTime: '23:45', endDate: '2026-11-03', endTime: '00:15' })
  })

  it('changes only the end when the end fields are edited', async () => {
    const wrapper = mount(DateTimeFields, { props: { modelValue: value } })

    await wrapper.find('input[aria-label="End time"]').setValue('14:00')

    expect(lastEmitted(wrapper)).toEqual({ ...value, endTime: '14:00' })
  })

  it('does not shift the end when the start is cleared', async () => {
    const wrapper = mount(DateTimeFields, { props: { modelValue: value } })

    await wrapper.find('input[aria-label="Start time"]').setValue('')

    expect(lastEmitted(wrapper)).toEqual({ ...value, startTime: '' })
  })

  it('sets the end from a suggested duration until the end has been edited', async () => {
    const wrapper = mount(DateTimeFields, { props: { modelValue: value } })

    await wrapper.setProps({ suggestedDurationMinutes: 90 })
    expect(lastEmitted(wrapper)).toEqual({ ...value, endDate: '2026-11-02', endTime: '11:30' })

    await wrapper.setProps({ suggestedDurationMinutes: 45 })
    expect(lastEmitted(wrapper)).toEqual({ ...value, endTime: '10:45' })
  })

  it('ignores a suggested duration once the user has edited the end', async () => {
    const wrapper = mount(DateTimeFields, { props: { modelValue: value } })
    await wrapper.find('input[aria-label="End time"]').setValue('14:00')
    const emitted = (wrapper.emitted('update:modelValue') as unknown[][]).length

    await wrapper.setProps({ suggestedDurationMinutes: 90 })

    expect((wrapper.emitted('update:modelValue') as unknown[][]).length).toBe(emitted)
  })

  it('ignores a suggested duration when the end is locked, as in edit mode', async () => {
    const wrapper = mount(DateTimeFields, { props: { modelValue: value, endEdited: true } })

    await wrapper.setProps({ suggestedDurationMinutes: 90 })

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('shows start and end errors under the fields', () => {
    const wrapper = mount(DateTimeFields, {
      props: { modelValue: value, startError: 'Start is in the past.', endError: 'End must be after the start.' },
    })

    expect(wrapper.text()).toContain('Start is in the past.')
    expect(wrapper.text()).toContain('End must be after the start.')
    expect(wrapper.find('input[aria-label="Start date"]').attributes('aria-invalid')).toBe('true')
    expect(wrapper.find('input[aria-label="End date"]').attributes('aria-invalid')).toBe('true')
  })
})
