import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import type { EventType } from '@/stores/eventTypes'
import EventTypeForm from '../EventTypeForm.vue'

const existing: EventType = {
  id: 'abc',
  name: 'Weekly practice',
  description: 'Every Tuesday',
  color: 'green',
  defaultDurationMinutes: 90,
  archived: false,
  version: 3,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

const save = (w: ReturnType<typeof mount>) => w.find('button[type=submit]')

describe('EventTypeForm', () => {
  it('shows an error on blur when the name is blank', async () => {
    const wrapper = mount(EventTypeForm)

    await wrapper.find('input[name=name]').trigger('blur')

    expect(wrapper.text()).toContain('Enter a name.')
    expect(wrapper.find('input[name=name]').attributes('aria-invalid')).toBe('true')
  })

  it('keeps Save disabled until a name is entered', async () => {
    const wrapper = mount(EventTypeForm)
    expect(save(wrapper).attributes('disabled')).toBeDefined()

    await wrapper.find('input[name=name]').setValue('Practice')

    expect(save(wrapper).attributes('disabled')).toBeUndefined()
  })

  it('submits a trimmed create payload', async () => {
    const wrapper = mount(EventTypeForm)
    await wrapper.find('input[name=name]').setValue('  Practice  ')
    await wrapper.find('textarea[name=description]').setValue('  Weekly  ')
    await wrapper.find('[aria-label=Purple]').trigger('click')
    await wrapper.find('select').setValue('90')

    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('submit')?.[0]).toEqual([
      { name: 'Practice', description: 'Weekly', color: 'purple', defaultDurationMinutes: 90 },
    ])
  })

  it('omits an empty description and duration from the payload', async () => {
    const wrapper = mount(EventTypeForm)
    await wrapper.find('input[name=name]').setValue('Practice')

    await wrapper.find('form').trigger('submit')

    const payload = wrapper.emitted('submit')?.[0]?.[0] as Record<string, unknown>
    expect(payload.description).toBeUndefined()
    expect(payload.defaultDurationMinutes).toBeUndefined()
    expect(payload.color).toBe('blue')
  })

  it('does not submit an invalid form and shows the errors', async () => {
    const wrapper = mount(EventTypeForm)

    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('submit')).toBeUndefined()
    expect(wrapper.text()).toContain('Enter a name.')
  })

  it('rejects a custom duration outside 5–1440', async () => {
    const wrapper = mount(EventTypeForm)
    await wrapper.find('input[name=name]').setValue('Practice')
    await wrapper.find('select').setValue('custom')
    await wrapper.find('input[type=number]').setValue('3')

    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('submit')).toBeUndefined()
    expect(wrapper.text()).toContain('Enter 5 to 1440 minutes.')
  })

  it('prefills in edit mode and includes archived and version in the payload', async () => {
    const wrapper = mount(EventTypeForm, { props: { eventType: existing } })

    expect((wrapper.find('input[name=name]').element as HTMLInputElement).value).toBe('Weekly practice')
    expect((wrapper.find('textarea[name=description]').element as HTMLTextAreaElement).value).toBe('Every Tuesday')
    expect(wrapper.find('[aria-label=Green]').attributes('aria-checked')).toBe('true')
    expect((wrapper.find('select').element as HTMLSelectElement).value).toBe('90')

    await wrapper.find('input[name=name]').setValue('Practice')
    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('submit')?.[0]).toEqual([
      { name: 'Practice', description: 'Every Tuesday', color: 'green', defaultDurationMinutes: 90, archived: false, version: 3 },
    ])
  })

  it('keeps Save disabled in edit mode while nothing has changed', async () => {
    const wrapper = mount(EventTypeForm, { props: { eventType: existing } })
    expect(save(wrapper).attributes('disabled')).toBeDefined()

    await wrapper.find('[aria-label=Red]').trigger('click')

    expect(save(wrapper).attributes('disabled')).toBeUndefined()
  })

  it('shows server field errors', () => {
    const wrapper = mount(EventTypeForm, { props: { fieldErrors: { name: 'An event type with this name already exists.' } } })

    expect(wrapper.text()).toContain('An event type with this name already exists.')
  })

  it('submits with Cmd/Ctrl+Enter when valid', async () => {
    const wrapper = mount(EventTypeForm)
    await wrapper.find('input[name=name]').setValue('Practice')

    await wrapper.find('form').trigger('keydown', { key: 'Enter', ctrlKey: true })

    expect(wrapper.emitted('submit')).toHaveLength(1)
  })

  it('emits cancel and disables the form while saving', async () => {
    const wrapper = mount(EventTypeForm, { props: { saving: true } })

    expect(wrapper.find('input[name=name]').attributes('disabled')).toBeDefined()
    await wrapper.findAll('button').find((b) => b.text() === 'Cancel')!.trigger('click')

    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })
})
