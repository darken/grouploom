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

function serve(result: () => unknown) {
  get.mockImplementation(async (path: string) => (path === '/event-types' ? typePage() : result()))
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
    for (const m of [get, put, del]) m.mockReset()
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
    get.mockImplementation(async (path: string) => (path === '/event-types' ? typePage() : new Promise((r) => (resolve = r))))
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
})
