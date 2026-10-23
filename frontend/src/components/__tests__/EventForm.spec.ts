import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'

import type { EventItem } from '@/stores/events'
import { browserTimeZone } from '@/ui/timeZones'
import EventForm from '../EventForm.vue'

const types = [
  { id: 't1', name: 'Practice', color: 'green' as const, archived: false, defaultDurationMinutes: 90 },
  { id: 't2', name: 'Board', color: 'red' as const, archived: false },
]

const existing: EventItem = {
  id: 'e1',
  title: 'Rehearsal',
  description: 'Bring music',
  location: 'Gym',
  start: '2020-03-10T09:00:00Z',
  end: '2020-03-10T11:00:00Z',
  localStart: '2020-03-10T10:00:00',
  localEnd: '2020-03-10T12:00:00',
  timeZone: 'Europe/Madrid',
  eventType: { id: 't1', name: 'Practice', color: 'green', archived: false },
  version: 4,
  createdAt: '2020-01-01T00:00:00Z',
  updatedAt: '2020-01-01T00:00:00Z',
}

const mountForm = (props: object = {}) =>
  mount(EventForm, { props: { eventTypes: types, ...props }, global: { stubs: { RouterLink: RouterLinkStub } } })

const save = (w: ReturnType<typeof mount>) => w.find('button[type=submit]')
const input = (w: ReturnType<typeof mount>, label: string) => w.find(`input[aria-label="${label}"]`)

async function fillValid(wrapper: ReturnType<typeof mount>) {
  await wrapper.find('input[name=title]').setValue('Practice')
  await wrapper.find('select[name=eventTypeId]').setValue('t2')
}

describe('EventForm', () => {
  beforeEach(() => {
    // Local time 12:20 on 1 June 2026, so a new event defaults to 13:00-14:00.
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 5, 1, 12, 20))
  })
  afterEach(() => vi.useRealTimers())

  it('defaults a new event to the next full hour, one hour long, in the browser zone', () => {
    const wrapper = mountForm()

    expect((input(wrapper, 'Start date').element as HTMLInputElement).value).toBe('2026-06-01')
    expect((input(wrapper, 'Start time').element as HTMLInputElement).value).toBe('13:00')
    expect((input(wrapper, 'End date').element as HTMLInputElement).value).toBe('2026-06-01')
    expect((input(wrapper, 'End time').element as HTMLInputElement).value).toBe('14:00')
    expect(wrapper.text()).toContain(`Time zone: ${browserTimeZone()}`)
  })

  it('shows an error on blur when the title is blank', async () => {
    const wrapper = mountForm()

    await wrapper.find('input[name=title]').trigger('blur')

    expect(wrapper.text()).toContain('Enter a title.')
    expect(wrapper.find('input[name=title]').attributes('aria-invalid')).toBe('true')
  })

  it('shows a title length error over 120 characters', async () => {
    const wrapper = mountForm()

    await wrapper.find('input[name=title]').setValue('x'.repeat(121))
    await wrapper.find('input[name=title]').trigger('blur')

    expect(wrapper.text()).toContain('Use 120 characters or fewer.')
  })

  it('asks for a type on submit', async () => {
    const wrapper = mountForm()
    await wrapper.find('input[name=title]').setValue('Practice')

    await wrapper.find('form').trigger('submit')

    expect(wrapper.text()).toContain('Choose an event type.')
    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('shows an error on the end when it is not after the start', async () => {
    const wrapper = mountForm()
    await fillValid(wrapper)

    await input(wrapper, 'End time').setValue('13:00')
    await input(wrapper, 'End time').trigger('blur')

    expect(wrapper.text()).toContain('End must be after the start.')
    expect(save(wrapper).attributes('disabled')).toBeDefined()
  })

  it('shows an error when a new event starts in the past', async () => {
    const wrapper = mountForm()
    await fillValid(wrapper)

    await input(wrapper, 'Start date').setValue('2020-01-01')
    await input(wrapper, 'Start date').trigger('blur')

    expect(wrapper.text()).toContain("The start can't be in the past.")
  })

  it('shows an error when an event lasts more than seven days', async () => {
    const wrapper = mountForm()
    await fillValid(wrapper)

    await input(wrapper, 'End date').setValue('2026-06-09')

    expect(wrapper.text()).toContain('An event can last at most 7 days.')
  })

  it('accepts an event of exactly seven days', async () => {
    const wrapper = mountForm()
    await fillValid(wrapper)

    await input(wrapper, 'End date').setValue('2026-06-08')
    await input(wrapper, 'End time').setValue('13:00')

    expect(wrapper.text()).not.toContain('at most 7 days')
  })

  it('shows length errors for location and description', async () => {
    const wrapper = mountForm()

    await wrapper.find('input[name=location]').setValue('l'.repeat(201))
    await wrapper.find('input[name=location]').trigger('blur')
    await wrapper.find('textarea[name=description]').setValue('d'.repeat(2001))
    await wrapper.find('textarea[name=description]').trigger('blur')

    expect(wrapper.text()).toContain('Use 200 characters or fewer.')
    expect(wrapper.text()).toContain('Use 2000 characters or fewer.')
  })

  it('keeps Save disabled until the form is valid', async () => {
    const wrapper = mountForm()
    expect(save(wrapper).attributes('disabled')).toBeDefined()

    await fillValid(wrapper)

    expect(save(wrapper).attributes('disabled')).toBeUndefined()
  })

  it('submits a trimmed payload with wall-clock times (YYYY-MM-DDTHH:mm:00) and the zone', async () => {
    const wrapper = mountForm()
    await wrapper.find('input[name=title]').setValue('  Practice ')
    await wrapper.find('select[name=eventTypeId]').setValue('t2')
    await wrapper.find('input[name=location]').setValue(' Gym ')
    await wrapper.find('textarea[name=description]').setValue('  Bring music ')

    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('submit')?.[0]).toEqual([
      {
        eventTypeId: 't2',
        title: 'Practice',
        description: 'Bring music',
        location: 'Gym',
        localStart: '2026-06-01T13:00:00',
        localEnd: '2026-06-01T14:00:00',
        timeZone: browserTimeZone(),
      },
    ])
  })

  it('omits empty description and location from the payload', async () => {
    const wrapper = mountForm()
    await fillValid(wrapper)

    await wrapper.find('form').trigger('submit')

    const emitted = wrapper.emitted('submit') ?? []
    const payload = (emitted[0] as [Record<string, unknown>])[0]
    expect(payload.description).toBeUndefined()
    expect(payload.location).toBeUndefined()
  })

  it("fills the end from the chosen type's default duration", async () => {
    const wrapper = mountForm()

    await wrapper.find('select[name=eventTypeId]').setValue('t1')

    expect((input(wrapper, 'End time').element as HTMLInputElement).value).toBe('14:30')
  })

  it("keeps an end the user edited when the type's default duration would change it", async () => {
    const wrapper = mountForm()
    await input(wrapper, 'End time').setValue('16:00')

    await wrapper.find('select[name=eventTypeId]').setValue('t1')

    expect((input(wrapper, 'End time').element as HTMLInputElement).value).toBe('16:00')
  })

  it('submits with Ctrl+Enter or Cmd+Enter when valid', async () => {
    const wrapper = mountForm()
    await fillValid(wrapper)

    await wrapper.find('input[name=title]').trigger('keydown', { key: 'Enter', ctrlKey: true })
    await wrapper.find('input[name=title]').trigger('keydown', { key: 'Enter', metaKey: true })

    expect(wrapper.emitted('submit')).toHaveLength(2)
  })

  it('shows server field errors under the matching fields', () => {
    const wrapper = mountForm({
      fieldErrors: { localStart: "The start can't be in the past.", eventTypeId: 'This event type is archived.' },
    })

    expect(wrapper.text()).toContain("The start can't be in the past.")
    expect(wrapper.text()).toContain('This event type is archived.')
  })

  it('disables Save and links to event types when the group has none', async () => {
    const wrapper = mountForm({ eventTypes: [] })
    await wrapper.find('input[name=title]').setValue('Practice')

    expect(wrapper.text()).toContain('Create an event type first')
    expect(save(wrapper).attributes('disabled')).toBeDefined()
  })

  it('prefills edit mode from the local times and zone and includes the version', async () => {
    const wrapper = mountForm({ event: existing })

    expect((wrapper.find('input[name=title]').element as HTMLInputElement).value).toBe('Rehearsal')
    expect((wrapper.find('select[name=eventTypeId]').element as HTMLSelectElement).value).toBe('t1')
    expect((input(wrapper, 'Start date').element as HTMLInputElement).value).toBe('2020-03-10')
    expect((input(wrapper, 'Start time').element as HTMLInputElement).value).toBe('10:00')
    expect((input(wrapper, 'End time').element as HTMLInputElement).value).toBe('12:00')
    expect(wrapper.text()).toContain('Time zone: Europe/Madrid')
    expect((wrapper.find('input[name=location]').element as HTMLInputElement).value).toBe('Gym')
    expect((wrapper.find('textarea[name=description]').element as HTMLTextAreaElement).value).toBe('Bring music')

    await wrapper.find('input[name=title]').setValue('Corrected')
    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('submit')?.[0]).toEqual([
      {
        eventTypeId: 't1',
        title: 'Corrected',
        description: 'Bring music',
        location: 'Gym',
        localStart: '2020-03-10T10:00:00',
        localEnd: '2020-03-10T12:00:00',
        timeZone: 'Europe/Madrid',
        version: 4,
      },
    ])
  })

  it('keeps Save disabled in edit mode while nothing has changed', async () => {
    const wrapper = mountForm({ event: existing })
    expect(save(wrapper).attributes('disabled')).toBeDefined()

    await wrapper.find('input[name=location]').setValue('Studio')

    expect(save(wrapper).attributes('disabled')).toBeUndefined()
  })

  it('allows correcting a past event while its start is unchanged', async () => {
    const wrapper = mountForm({ event: existing })

    await wrapper.find('input[name=title]').setValue('Corrected')

    expect(wrapper.text()).not.toContain("can't be in the past")
    expect(save(wrapper).attributes('disabled')).toBeUndefined()
  })

  it('rejects moving the start of an event into the past', async () => {
    const wrapper = mountForm({ event: { ...existing, localStart: '2027-03-10T10:00:00', localEnd: '2027-03-10T12:00:00' } })

    await input(wrapper, 'Start date').setValue('2026-01-01')

    expect(wrapper.text()).toContain("The start can't be in the past.")
  })

  it('keeps an archived current type selected in edit mode', () => {
    const wrapper = mountForm({
      event: { ...existing, eventType: { id: 'old', name: 'Old', color: 'gray', archived: true } },
    })

    expect((wrapper.find('select[name=eventTypeId]').element as HTMLSelectElement).value).toBe('old')
    expect(wrapper.find('select[name=eventTypeId]').text()).toContain('Old (archived)')
  })

  it('emits cancel from the Cancel button', async () => {
    const wrapper = mountForm()

    await wrapper.findAll('button').find((b) => b.text() === 'Cancel')!.trigger('click')

    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })
})
