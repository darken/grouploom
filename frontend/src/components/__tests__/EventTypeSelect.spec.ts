import { describe, expect, it } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'

import EventTypeSelect from '../EventTypeSelect.vue'

const options = [
  { id: 'a', name: 'Practice', color: 'green' as const, archived: false },
  { id: 'b', name: 'Board', color: 'red' as const, archived: false },
]

const mountSelect = (props: object) =>
  mount(EventTypeSelect, { props: { modelValue: '', options, ...props }, global: { stubs: { RouterLink: RouterLinkStub } } })

describe('EventTypeSelect', () => {
  it('lists the given active types after a placeholder', () => {
    const wrapper = mountSelect({})

    expect(wrapper.findAll('option').map((o) => o.text())).toEqual(['Choose a type', 'Practice', 'Board'])
  })

  it('emits the chosen type id', async () => {
    const wrapper = mountSelect({})

    await wrapper.find('select').setValue('b')

    expect(wrapper.emitted('update:modelValue')).toEqual([['b']])
  })

  it('shows a color dot for the chosen type', () => {
    const wrapper = mountSelect({ modelValue: 'a' })

    expect(wrapper.find('[data-color-dot]').classes().join(' ')).toContain('bg-green-500')
  })

  it('shows no dot before a type is chosen', () => {
    expect(mountSelect({}).find('[data-color-dot]').exists()).toBe(false)
  })

  it('adds an archived current type as "(archived)" and keeps it selected', () => {
    const wrapper = mountSelect({
      modelValue: 'z',
      current: { id: 'z', name: 'Old', color: 'gray', archived: true },
    })

    expect(wrapper.findAll('option').map((o) => o.text())).toContain('Old (archived)')
    expect((wrapper.find('select').element as HTMLSelectElement).value).toBe('z')
  })

  it('does not duplicate a current type that is already among the options', () => {
    const wrapper = mountSelect({ modelValue: 'a', current: options[0] })

    expect(wrapper.findAll('option').filter((o) => o.text().startsWith('Practice'))).toHaveLength(1)
  })

  it('shows a link to create an event type when there are none', () => {
    const wrapper = mountSelect({ options: [] })

    expect(wrapper.text()).toContain('Create an event type first')
    expect(wrapper.findComponent(RouterLinkStub).props('to')).toBe('/event-types')
    expect(wrapper.find('select').exists()).toBe(false)
  })

  it('still shows the select when there are no active types but the event has an archived one', () => {
    const wrapper = mountSelect({ options: [], modelValue: 'z', current: { id: 'z', name: 'Old', color: 'gray', archived: true } })

    expect(wrapper.find('select').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('Create an event type first')
  })

  it('shows an error and marks the select invalid', () => {
    const wrapper = mountSelect({ error: 'This event type is archived.' })

    expect(wrapper.text()).toContain('This event type is archived.')
    expect(wrapper.find('select').attributes('aria-invalid')).toBe('true')
  })
})
