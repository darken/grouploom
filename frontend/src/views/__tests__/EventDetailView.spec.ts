import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'

import { api } from '@/api/client'
import ToastHost from '@/components/ToastHost.vue'
import { useToast } from '@/composables/useToast'
import { installDialogPolyfill } from '@/components/__tests__/dialogPolyfill'
import { browserTimeZone } from '@/ui/timeZones'
import EventDetailView from '../EventDetailView.vue'

vi.mock('@/api/client', () => ({
  api: {
    GET: vi.fn<() => Promise<unknown>>(),
    POST: vi.fn<() => Promise<unknown>>(),
    PUT: vi.fn<() => Promise<unknown>>(),
    DELETE: vi.fn<() => Promise<unknown>>(),
  },
}))

const get = vi.mocked(api.GET) as unknown as ReturnType<typeof vi.fn>
const post = vi.mocked(api.POST) as unknown as ReturnType<typeof vi.fn>
const put = vi.mocked(api.PUT) as unknown as ReturnType<typeof vi.fn>
const del = vi.mocked(api.DELETE) as unknown as ReturnType<typeof vi.fn>

beforeAll(installDialogPolyfill)

function event(overrides: object = {}) {
  return {
    id: 'e1',
    title: 'Rehearsal',
    description: 'Bring music\nand water',
    location: 'Main gym',
    start: '2099-11-02T09:00:00Z',
    end: '2099-11-02T11:00:00Z',
    localStart: '2099-11-02T10:00:00',
    localEnd: '2099-11-02T12:00:00',
    timeZone: 'Europe/Madrid',
    eventType: { id: 't1', name: 'Practice', color: 'green', archived: false },
    version: 3,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

function typePage() {
  const t = { id: 't1', name: 'Practice', color: 'green', archived: false, version: 0, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' }
  return { data: { content: [t], page: { number: 0, size: 100, totalElements: 1, totalPages: 1 } }, response: new Response() }
}

let notificationItems: object[] = []

function notification(overrides: object = {}) {
  return {
    id: 'n1',
    kind: 'REMINDER',
    status: 'SCHEDULED',
    sendAt: '2099-11-01T09:00:00Z',
    localSendAt: '2099-11-01T10:00:00',
    timeZone: 'Europe/Madrid',
    delivery: { pending: 0, sent: 0, failed: 0 },
    createdAt: '2099-10-01T12:00:00Z',
    ...overrides,
  }
}

function serve(result: () => unknown) {
  get.mockImplementation(async (path: string) => {
    if (path === '/event-types') return typePage()
    if (path === '/events/{eventId}/notifications') return { data: notificationItems, response: new Response() }
    if (path === '/events/{eventId}/notifications/audience') {
      return { data: { eligible: 18, total: 24 }, response: new Response() }
    }
    return result()
  })
}

const found = (overrides: object = {}) => () => ({ data: event(overrides), response: new Response() })

async function mountView(url = '/events/e1?when=past&page=2') {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/events', component: { template: '<i/>' } },
      { path: '/events/:id', component: EventDetailView },
    ],
  })
  await router.push(url)
  await router.isReady()
  const wrapper = mount({ components: { EventDetailView, ToastHost }, template: '<EventDetailView /><ToastHost />' }, {
    global: { plugins: [pinia, router] },
    attachTo: document.body,
  })
  await flushPromises()
  return { wrapper, router }
}

type Wrapper = Awaited<ReturnType<typeof mountView>>['wrapper']
const dialogOpen = (w: Wrapper) => w.findAll('dialog').filter((d) => d.attributes('open') !== undefined)
const button = (w: Wrapper, label: string) => w.findAll('button').find((b) => b.text() === label)

describe('EventDetailView', () => {
  beforeEach(() => {
    for (const m of [get, post, put, del]) m.mockReset()
    notificationItems = []
    useToast().dismiss()
    document.body.innerHTML = ''
  })

  it('loads the event by the route id and sets the page title', async () => {
    serve(found())

    const { wrapper } = await mountView()

    expect(get).toHaveBeenCalledWith('/events/{id}', { params: { path: { id: 'e1' } } })
    expect(wrapper.find('h1').text()).toBe('Rehearsal')
    expect(document.title).toBe('Rehearsal · GroupLoom')
  })

  it('shows the type chip, the time line in the event zone, the location and the description', async () => {
    serve(found())

    const { wrapper } = await mountView()

    expect(wrapper.find('[data-type-chip]').text()).toBe('Practice')
    expect(wrapper.find('[data-type-chip] [data-color-dot]').classes().join(' ')).toContain('bg-green-500')
    const time = wrapper.find('[data-time]').text()
    expect(time).toContain('10:00 – 12:00')
    expect(wrapper.find('[data-location]').text()).toBe('Main gym')
    expect(wrapper.find('[data-description]').text()).toBe('Bring music\nand water')
    expect(wrapper.find('[data-description]').classes()).toContain('whitespace-pre-line')
    // The zone abbreviation shows only for a viewer in another zone.
    expect(time.endsWith('CET')).toBe(browserTimeZone() !== 'Europe/Madrid')
  })

  it('renders the description as text, never as HTML', async () => {
    serve(found({ description: 'Bring <b>music</b><img src=x onerror=alert(1)>' }))

    const { wrapper } = await mountView()

    const description = wrapper.find('[data-description]')
    expect(description.text()).toBe('Bring <b>music</b><img src=x onerror=alert(1)>')
    expect(description.element.querySelector('b, img')).toBeNull()
  })

  it('leaves out the location and description when there are none', async () => {
    serve(found({ location: undefined, description: undefined }))

    const { wrapper } = await mountView()

    expect(wrapper.find('[data-location]').exists()).toBe(false)
    expect(wrapper.find('[data-description]').exists()).toBe(false)
  })

  it('marks an archived type in the chip', async () => {
    serve(found({ eventType: { id: 't1', name: 'Old', color: 'gray', archived: true } }))

    const { wrapper } = await mountView()

    expect(wrapper.find('[data-type-chip]').text()).toBe('Old (archived)')
  })

  it('links back to the events list keeping its query state', async () => {
    serve(found())

    const { wrapper } = await mountView('/events/e1?when=past&type=t1&page=2')

    expect(wrapper.find('a[data-back]').attributes('href')).toBe('/events?when=past&type=t1&page=2')
    expect(wrapper.find('a[data-back]').text()).toContain('Events')
  })

  it('shows a loading skeleton, then the event', async () => {
    let resolve!: (v: unknown) => void
    get.mockImplementation(async (path: string) => {
      if (path === '/event-types') return typePage()
      if (path.startsWith('/events/{eventId}')) return { data: [], response: new Response() }
      return new Promise((r) => (resolve = r))
    })
    const { wrapper } = await mountView()

    expect(wrapper.find('[data-skeleton]').exists()).toBe(true)

    resolve({ data: event(), response: new Response() })
    await flushPromises()
    expect(wrapper.find('[data-skeleton]').exists()).toBe(false)
    expect(wrapper.find('h1').text()).toBe('Rehearsal')
  })

  it('shows the not-found state with a way back', async () => {
    serve(() => ({ error: { status: 404, title: 'Not Found' }, response: new Response(null, { status: 404 }) }))

    const { wrapper } = await mountView('/events/gone?when=past')

    expect(wrapper.text()).toContain("This event doesn't exist or was deleted.")
    const back = wrapper.findAll('a').find((a) => a.text() === 'Back to events')
    expect(back!.attributes('href')).toBe('/events?when=past')
  })

  it('shows an error banner with Retry for other load failures', async () => {
    serve(() => ({ error: { status: 500, title: 'Server error' }, response: new Response(null, { status: 500 }) }))
    const { wrapper } = await mountView()
    expect(wrapper.find('[role=alert]').exists()).toBe(true)

    serve(found())
    await button(wrapper, 'Retry')!.trigger('click')
    await flushPromises()

    expect(wrapper.find('h1').text()).toBe('Rehearsal')
  })

  it('opens the edit dialog prefilled and saves with the version', async () => {
    serve(found())
    put.mockResolvedValue({ data: event({ title: 'Renamed', version: 4 }), response: new Response() })
    const { wrapper } = await mountView()

    await button(wrapper, 'Edit')!.trigger('click')
    expect(dialogOpen(wrapper)[0]!.find('h2').text()).toBe('Edit event')
    expect((wrapper.find('dialog input[name=title]').element as HTMLInputElement).value).toBe('Rehearsal')
    await wrapper.find('dialog input[name=title]').setValue('Renamed')
    await wrapper.find('dialog form').trigger('submit')
    await flushPromises()

    expect(put).toHaveBeenCalledWith('/events/{id}', {
      params: { path: { id: 'e1' } },
      body: expect.objectContaining({ title: 'Renamed', version: 3, eventTypeId: 't1', timeZone: 'Europe/Madrid' }),
    })
    expect(dialogOpen(wrapper)).toHaveLength(0)
    expect(wrapper.find('h1').text()).toBe('Renamed')
    expect(wrapper.find('[aria-live=polite]').text()).toContain('Saved')
  })

  it('offers Reload when the event was changed by someone else', async () => {
    serve(found())
    put.mockResolvedValue({ error: { status: 409, code: 'event.stale' }, response: new Response(null, { status: 409 }) })
    const { wrapper } = await mountView()
    await button(wrapper, 'Edit')!.trigger('click')
    await wrapper.find('dialog input[name=title]').setValue('Other')
    await wrapper.find('dialog form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('dialog [role=alert]').text()).toContain('Changed by someone else')
    get.mockClear()
    serve(found({ title: 'Theirs', version: 4 }))
    await button(wrapper, 'Reload')!.trigger('click')
    await flushPromises()

    expect(dialogOpen(wrapper)).toHaveLength(0)
    expect(wrapper.find('h1').text()).toBe('Theirs')
  })

  it('asks for confirmation before deleting, then goes back to the list and toasts', async () => {
    serve(found())
    del.mockResolvedValue({ response: new Response(null, { status: 204 }) })
    const { wrapper, router } = await mountView('/events/e1?when=past&page=2')

    await wrapper.find('button[aria-label="More actions"]').trigger('click')
    await wrapper.findAll('[role=menuitem]').find((m) => m.text() === 'Delete')!.trigger('click')

    expect(del).not.toHaveBeenCalled()
    const confirm = dialogOpen(wrapper)[0]!
    expect(confirm.find('h2').text()).toBe("Delete 'Rehearsal'?")
    expect(confirm.text()).toContain("This can't be undone.")

    await confirm.findAll('button').find((b) => b.text() === 'Delete')!.trigger('click')
    await flushPromises()

    expect(del).toHaveBeenCalledWith('/events/{id}', { params: { path: { id: 'e1' } } })
    expect(router.currentRoute.value.fullPath).toBe('/events?when=past&page=2')
    expect(wrapper.find('[aria-live=polite]').text()).toContain('Deleted')
  })

  it('does not delete when the confirmation is cancelled', async () => {
    serve(found())
    const { wrapper } = await mountView()
    await wrapper.find('button[aria-label="More actions"]').trigger('click')
    await wrapper.findAll('[role=menuitem]').find((m) => m.text() === 'Delete')!.trigger('click')

    await dialogOpen(wrapper)[0]!.findAll('button').find((b) => b.text() === 'Cancel')!.trigger('click')

    expect(del).not.toHaveBeenCalled()
    expect(dialogOpen(wrapper)).toHaveLength(0)
  })

  // --- cancelled events ---

  const CANCELLED = { cancelledAt: '2099-10-20T08:00:00Z', cancellationMessage: 'The gym is closed' }

  it('shows a quiet banner for a cancelled event, hides Edit and Schedule, and leaves only Delete in the menu', async () => {
    serve(found(CANCELLED))

    const { wrapper } = await mountView()

    const banner = wrapper.find('[data-cancelled-banner]')
    expect(banner.text()).toContain('Cancelled · Oct 20')
    expect(banner.text()).toContain('The gym is closed')
    expect(button(wrapper, 'Edit')).toBeUndefined()
    expect(button(wrapper, 'Schedule…')).toBeUndefined()
    await wrapper.find('button[aria-label="More actions"]').trigger('click')
    expect(wrapper.findAll('[role=menuitem]').map((m) => m.text())).toEqual(['Delete'])
  })

  it('shows the banner without a message when there is none', async () => {
    serve(found({ cancelledAt: '2099-10-20T08:00:00Z' }))

    const { wrapper } = await mountView()

    expect(wrapper.find('[data-cancelled-banner]').text()).toBe('Cancelled · Oct 20')
  })

  it('offers "Cancel event…" above Delete for an upcoming event', async () => {
    serve(found())
    const { wrapper } = await mountView()

    await wrapper.find('button[aria-label="More actions"]').trigger('click')

    const items = wrapper.findAll('[role=menuitem]')
    expect(items.map((m) => m.text())).toEqual(['Cancel event…', 'Delete'])
    expect(items[0]!.classes().join(' ')).toContain('danger')
  })

  it('does not offer cancelling for an event that has ended', async () => {
    serve(found({ start: '2020-11-02T09:00:00Z', end: '2020-11-02T11:00:00Z' }))
    const { wrapper } = await mountView()

    await wrapper.find('button[aria-label="More actions"]').trigger('click')

    expect(wrapper.findAll('[role=menuitem]').map((m) => m.text())).toEqual(['Delete'])
  })

  it('cancels from the menu: explains who is told, posts the message, then shows the banner and a toast', async () => {
    notificationItems = [notification({ kind: 'INVITATION', status: 'SENT', sentAt: '2099-10-01T12:00:00Z', recipientCount: 4 })]
    serve(found())
    post.mockResolvedValue({ data: event(CANCELLED), response: new Response() })
    const { wrapper } = await mountView()

    await wrapper.find('button[aria-label="More actions"]').trigger('click')
    await wrapper.findAll('[role=menuitem]').find((m) => m.text() === 'Cancel event…')!.trigger('click')

    const dialog = dialogOpen(wrapper)[0]!
    expect(dialog.find('h2').text()).toBe("Cancel 'Rehearsal'?")
    expect(dialog.text()).toContain('Members who were notified will get a cancellation email.')
    await dialog.find('textarea').setValue('The gym is closed')
    await dialog.findAll('button').find((b) => b.text() === 'Cancel event')!.trigger('click')
    await flushPromises()

    expect(post).toHaveBeenCalledWith('/events/{id}/cancel', {
      params: { path: { id: 'e1' } },
      body: { message: 'The gym is closed' },
    })
    expect(dialogOpen(wrapper)).toHaveLength(0)
    expect(wrapper.find('[data-cancelled-banner]').text()).toContain('The gym is closed')
    expect(wrapper.find('[aria-live=polite]').text()).toContain('Event cancelled')
  })

  it('says no one has been notified when nothing was sent', async () => {
    serve(found())
    const { wrapper } = await mountView()

    await wrapper.find('button[aria-label="More actions"]').trigger('click')
    await wrapper.findAll('[role=menuitem]').find((m) => m.text() === 'Cancel event…')!.trigger('click')

    expect(dialogOpen(wrapper)[0]!.text()).toContain('No one has been notified yet.')
  })

  // --- delete after notifying ---

  it('switches the delete dialog to "Cancel it instead?" when members were notified, and opens the cancel dialog', async () => {
    serve(found())
    del.mockResolvedValue({ error: { status: 409, code: 'event.has-notifications' }, response: new Response(null, { status: 409 }) })
    const { wrapper } = await mountView()

    await wrapper.find('button[aria-label="More actions"]').trigger('click')
    await wrapper.findAll('[role=menuitem]').find((m) => m.text() === 'Delete')!.trigger('click')
    await dialogOpen(wrapper)[0]!.findAll('button').find((b) => b.text() === 'Delete')!.trigger('click')
    await flushPromises()

    const blocked = dialogOpen(wrapper)[0]!
    expect(blocked.text()).toContain('Members were notified about this event. Cancel it instead?')
    expect(wrapper.find('[aria-live=polite]').text()).not.toContain('Deleted')
    await blocked.findAll('button').find((b) => b.text() === 'Cancel event…')!.trigger('click')
    await flushPromises()

    expect(dialogOpen(wrapper).map((d) => d.find('h2').text())).toEqual(["Cancel 'Rehearsal'?"])
  })

  it('explains that a cancelled event that was notified cannot be deleted, with no way to cancel again', async () => {
    serve(found(CANCELLED))
    del.mockResolvedValue({ error: { status: 409, code: 'event.has-notifications' }, response: new Response(null, { status: 409 }) })
    const { wrapper } = await mountView()

    await wrapper.find('button[aria-label="More actions"]').trigger('click')
    await wrapper.findAll('[role=menuitem]').find((m) => m.text() === 'Delete')!.trigger('click')
    await dialogOpen(wrapper)[0]!.findAll('button').find((b) => b.text() === 'Delete')!.trigger('click')
    await flushPromises()

    const blocked = dialogOpen(wrapper)[0]!
    expect(blocked.text()).toContain("Members were notified about this event, so it can't be deleted.")
    expect(blocked.findAll('button').map((b) => b.text())).toEqual(['OK'])
  })

  // --- notifications ---

  it('shows the empty state with a call to action when nothing is scheduled', async () => {
    serve(found())

    const { wrapper } = await mountView()

    expect(get).toHaveBeenCalledWith('/events/{eventId}/notifications', { params: { path: { eventId: 'e1' } } })
    const section = wrapper.find('[data-notifications]')
    expect(section.find('h2').text()).toBe('Notifications')
    expect(section.text()).toContain('No notifications yet.')
    expect(section.text()).toContain('Invite members to this event by email.')
    expect(button(wrapper, 'Schedule invitation')).toBeDefined()
    expect(button(wrapper, 'Schedule…')).toBeDefined()
  })

  it('lists the notifications, with Unschedule for scheduled ones', async () => {
    notificationItems = [notification()]
    serve(found())

    const { wrapper } = await mountView()

    expect(wrapper.find('[data-notifications] li [data-kind]').text()).toBe('Reminder')
    expect(wrapper.text()).not.toContain('No notifications yet.')
    expect(button(wrapper, 'Schedule invitation')).toBeUndefined()
  })

  it('hides "Schedule…" and the call to action once the event has started', async () => {
    serve(found({ start: '2020-11-02T09:00:00Z', end: '2099-11-02T11:00:00Z' }))

    const { wrapper } = await mountView()

    expect(button(wrapper, 'Schedule…')).toBeUndefined()
    expect(button(wrapper, 'Schedule invitation')).toBeUndefined()
    expect(wrapper.find('[data-notifications]').exists()).toBe(true)
  })

  it('schedules an invitation now: loads the audience, posts, closes and toasts "Sending invitation…"', async () => {
    serve(found())
    post.mockResolvedValue({ data: notification({ kind: 'INVITATION', sendAt: '2099-01-01T00:00:00Z' }), response: new Response() })
    const { wrapper } = await mountView()

    await button(wrapper, 'Schedule invitation')!.trigger('click')
    await flushPromises()
    const dialog = dialogOpen(wrapper)[0]!
    expect(get).toHaveBeenCalledWith('/events/{eventId}/notifications/audience', { params: { path: { eventId: 'e1' } } })
    expect(dialog.find('[data-audience]').text()).toContain('Will be sent to 18 of 24 members.')
    await dialog.findAll('button').find((b) => b.text() === 'Send now')!.trigger('click')
    await flushPromises()

    expect(post).toHaveBeenCalledWith('/events/{eventId}/notifications', {
      params: { path: { eventId: 'e1' } },
      body: { kind: 'INVITATION', timing: 'NOW' },
    })
    expect(dialogOpen(wrapper)).toHaveLength(0)
    expect(wrapper.find('[aria-live=polite]').text()).toContain('Sending invitation…')
  })

  it('schedules a reminder and toasts when it will go out', async () => {
    serve(found())
    post.mockResolvedValue({ data: notification(), response: new Response() })
    const { wrapper } = await mountView()

    await button(wrapper, 'Schedule…')!.trigger('click')
    await flushPromises()
    const dialog = dialogOpen(wrapper)[0]!
    await dialog.findAll('[role=radio]')[1]!.trigger('click')
    await dialog.findAll('input[name=timing]')[2]!.setValue(true)
    await dialog.findAll('button').find((b) => b.text() === 'Schedule')!.trigger('click')
    await flushPromises()

    expect(post).toHaveBeenCalledWith('/events/{eventId}/notifications', {
      params: { path: { eventId: 'e1' } },
      body: { kind: 'REMINDER', timing: 'BEFORE_START', offsetMinutes: 1440 },
    })
    expect(wrapper.find('[aria-live=polite]').text()).toContain('Reminder scheduled for Sun, Nov 1, 10:00')
    expect(wrapper.find('[data-notifications] li').exists()).toBe(true)
  })

  it('keeps the dialog open and explains when the limit is reached', async () => {
    serve(found())
    post.mockResolvedValue({ error: { status: 409, code: 'notification.limit' }, response: new Response(null, { status: 409 }) })
    const { wrapper } = await mountView()

    await button(wrapper, 'Schedule…')!.trigger('click')
    await flushPromises()
    await dialogOpen(wrapper)[0]!.findAll('button').find((b) => b.text() === 'Send now')!.trigger('click')
    await flushPromises()

    expect(dialogOpen(wrapper)).toHaveLength(1)
    expect(dialogOpen(wrapper)[0]!.text()).toContain('10 scheduled notifications')
  })

  it('unschedules a notification and toasts', async () => {
    notificationItems = [notification()]
    serve(found())
    del.mockResolvedValue({ response: new Response(null, { status: 204 }) })
    const { wrapper } = await mountView()

    await wrapper.find('[data-notifications] button[aria-haspopup=menu]').trigger('click')
    await wrapper.find('[data-notifications] [role=menuitem]').trigger('click')
    await flushPromises()

    expect(del).toHaveBeenCalledWith('/events/{eventId}/notifications/{notificationId}', {
      params: { path: { eventId: 'e1', notificationId: 'n1' } },
    })
    expect(wrapper.find('[data-notifications] li').exists()).toBe(false)
    expect(wrapper.find('[aria-live=polite]').text()).toContain('Unscheduled')
  })
})
