import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'

import { api } from '@/api/client'
import ToastHost from '@/components/ToastHost.vue'
import { useToast } from '@/composables/useToast'
import { installDialogPolyfill } from '@/components/__tests__/dialogPolyfill'
import { useEventsStore } from '@/stores/events'
import EventsView from '../EventsView.vue'

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

beforeAll(installDialogPolyfill)

function event(id: string, overrides: object = {}) {
  return {
    id,
    title: `Event ${id}`,
    start: '2099-11-02T09:00:00Z',
    end: '2099-11-02T11:00:00Z',
    localStart: '2099-11-02T10:00:00',
    localEnd: '2099-11-02T12:00:00',
    timeZone: 'Europe/Madrid',
    eventType: { id: 't1', name: 'Practice', color: 'green', archived: false },
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

function type(id: string, name: string, archived = false) {
  return { id, name, color: 'green', archived, version: 0, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' }
}

function pageOf(items: object[], total = items.length, number = 0) {
  return {
    data: { content: items, page: { number, size: 20, totalElements: total, totalPages: Math.max(1, Math.ceil(total / 20)) } },
    response: new Response(),
  }
}

/** Routes GET calls: events come from `events`, event types from the fixed active/archived lists. */
function serve(events: () => unknown) {
  get.mockImplementation(async (path: string, init?: { params?: { query?: { archived?: boolean } } }) => {
    if (path === '/event-types') {
      return init?.params?.query?.archived ? pageOf([type('t9', 'Old', true)]) : pageOf([type('t1', 'Practice'), type('t2', 'Board')])
    }
    return events()
  })
}

const eventCalls = () => get.mock.calls.filter(([path]) => path === '/events')

async function mountView(url = '/events', prepare?: () => void) {
  const pinia = createPinia()
  setActivePinia(pinia)
  prepare?.()
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/events', component: EventsView },
      { path: '/events/:id', component: { template: '<i/>' } },
    ],
  })
  await router.push(url)
  await router.isReady()
  const wrapper = mount({ components: { EventsView, ToastHost }, template: '<EventsView /><ToastHost />' }, {
    global: { plugins: [pinia, router] },
    attachTo: document.body,
  })
  await flushPromises()
  return { wrapper, router }
}

type Wrapper = Awaited<ReturnType<typeof mountView>>['wrapper']
const dialogOpen = (w: Wrapper) => w.findAll('dialog').filter((d) => d.attributes('open') !== undefined)
const button = (w: Wrapper, label: string) => w.findAll('button').find((b) => b.text() === label)

describe('EventsView', () => {
  beforeEach(() => {
    for (const m of [get, post]) m.mockReset()
    useToast().dismiss()
    document.body.innerHTML = ''
  })
  afterEach(() => vi.useRealTimers())

  it('renders the title and the upcoming events grouped by day', async () => {
    serve(() => pageOf([event('1'), event('2')]))

    const { wrapper } = await mountView()

    expect(wrapper.find('h1').text()).toBe('Events')
    expect(wrapper.findAll('li a')).toHaveLength(2)
    expect(eventCalls()[0]![1]).toEqual({ params: { query: { when: 'upcoming', eventTypeId: undefined, page: 0, size: 20 } } })
  })

  it('refetches on arrival even when the store already holds a list, so edits made elsewhere show up', async () => {
    serve(() => pageOf([event('1'), event('2')]))

    const { wrapper } = await mountView('/events', () => {
      const store = useEventsStore()
      store.$patch({
        items: [],
        loaded: true,
        when: 'upcoming',
        eventTypeId: '',
        pageInfo: { number: 0, size: 20, totalElements: 0, totalPages: 1 },
      })
    })

    expect(eventCalls()).toHaveLength(1)
    expect(wrapper.findAll('li a')).toHaveLength(2)
  })

  it('reads when, type and page from the URL (page is 1-based)', async () => {
    serve(() => pageOf([event('1')], 45, 1))

    const { wrapper } = await mountView('/events?when=past&type=t2&page=2')

    expect(eventCalls()[0]![1]).toEqual({ params: { query: { when: 'past', eventTypeId: 't2', page: 1, size: 20 } } })
    expect(wrapper.find('[role=radio][aria-checked=true]').text()).toBe('Past')
    expect((wrapper.find('select[aria-label="Event type"]').element as HTMLSelectElement).value).toBe('t2')
  })

  it('offers all, active and archived types in the filter', async () => {
    serve(() => pageOf([event('1')]))

    const { wrapper } = await mountView()

    expect(wrapper.findAll('select[aria-label="Event type"] option').map((o) => o.text())).toEqual([
      'All types',
      'Practice',
      'Board',
      'Old (archived)',
    ])
  })

  it('shows skeleton day sections during the first load', async () => {
    let resolve!: (v: unknown) => void
    get.mockImplementation(async (path: string) =>
      path === '/event-types' ? pageOf([]) : new Promise((r) => (resolve = r)),
    )
    const { wrapper } = await mountView()

    expect(wrapper.findAll('[data-skeleton]').length).toBeGreaterThan(0)

    resolve(pageOf([event('1')]))
    await flushPromises()
    expect(wrapper.findAll('[data-skeleton]')).toHaveLength(0)
  })

  it('switching to Past updates the URL, resets the page and refetches', async () => {
    serve(() => pageOf([event('1')], 45, 1))
    const { wrapper, router } = await mountView('/events?page=2')
    get.mockClear()
    serve(() => pageOf([event('1')]))

    await wrapper.findAll('[role=radio]').find((r) => r.text() === 'Past')!.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query).toEqual({ when: 'past' })
    expect(eventCalls()[0]![1]).toEqual({ params: { query: { when: 'past', eventTypeId: undefined, page: 0, size: 20 } } })
  })

  it('switching back to Upcoming drops when from the URL', async () => {
    serve(() => pageOf([event('1')]))
    const { wrapper, router } = await mountView('/events?when=past')

    await wrapper.findAll('[role=radio]').find((r) => r.text() === 'Upcoming')!.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query).toEqual({})
  })

  it('choosing a type updates the URL, keeps when, resets the page and refetches', async () => {
    serve(() => pageOf([event('1')], 45, 1))
    const { wrapper, router } = await mountView('/events?when=past&page=2')
    get.mockClear()
    serve(() => pageOf([event('1')]))

    await wrapper.find('select[aria-label="Event type"]').setValue('t2')
    await flushPromises()

    expect(router.currentRoute.value.query).toEqual({ when: 'past', type: 't2' })
    expect(eventCalls()[0]![1]).toEqual({ params: { query: { when: 'past', eventTypeId: 't2', page: 0, size: 20 } } })
  })

  it('goes to another page through the URL while keeping the filters', async () => {
    serve(() => pageOf([event('1')], 45))
    const { wrapper, router } = await mountView('/events?type=t1')

    await button(wrapper, 'Next')!.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query).toEqual({ type: 't1', page: '2' })
  })

  it('keeps the old rows and shows a progress bar while refetching', async () => {
    serve(() => pageOf([event('1')]))
    const { wrapper, router } = await mountView()
    let resolve!: (v: unknown) => void
    get.mockImplementation(async (path: string) => (path === '/event-types' ? pageOf([]) : new Promise((r) => (resolve = r))))

    await router.push('/events?when=past')
    await flushPromises()

    expect(wrapper.findAll('li a')).toHaveLength(1)
    expect(wrapper.find('[role=progressbar]').exists()).toBe(true)
    resolve(pageOf([]))
  })

  it('shows the upcoming empty state with a New event call to action', async () => {
    serve(() => pageOf([]))

    const { wrapper } = await mountView()

    expect(wrapper.find('h2').text()).toBe('Nothing scheduled.')
    expect(wrapper.text()).toContain("Plan your group's next practice, meeting or outing.")
    await wrapper.findAll('button').filter((b) => b.text() === 'New event').pop()!.trigger('click')
    expect(dialogOpen(wrapper)).toHaveLength(1)
  })

  it('shows a plain empty message for past events', async () => {
    serve(() => pageOf([]))

    const { wrapper } = await mountView('/events?when=past')

    expect(wrapper.text()).toContain('No past events yet.')
  })

  it('shows "No events of this type." with Show all types when the filter hides everything', async () => {
    serve(() => pageOf([]))
    const { wrapper, router } = await mountView('/events?type=t2')
    expect(wrapper.text()).toContain('No events of this type.')

    await button(wrapper, 'Show all types')!.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query.type).toBeUndefined()
  })

  it('opens the create dialog from New event', async () => {
    serve(() => pageOf([event('1')]))
    const { wrapper } = await mountView()

    await button(wrapper, 'New event')!.trigger('click')

    expect(dialogOpen(wrapper)).toHaveLength(1)
    expect(dialogOpen(wrapper)[0]!.find('h2').text()).toBe('New event')
    expect(dialogOpen(wrapper)[0]!.findAll('select[name=eventTypeId] option').map((o) => o.text())).toEqual([
      'Choose a type',
      'Practice',
      'Board',
    ])
  })

  it('creates an event, closes the dialog, toasts and opens the new event', async () => {
    serve(() => pageOf([event('1')]))
    post.mockResolvedValue({ data: event('7'), response: new Response() })
    const { wrapper, router } = await mountView()
    await button(wrapper, 'New event')!.trigger('click')

    await wrapper.find('dialog input[name=title]').setValue('Practice')
    await wrapper.find('dialog select[name=eventTypeId]').setValue('t1')
    await wrapper.find('dialog form').trigger('submit')
    await flushPromises()

    expect(post).toHaveBeenCalledWith('/events', {
      body: expect.objectContaining({ title: 'Practice', eventTypeId: 't1', timeZone: expect.any(String) }),
    })
    expect(dialogOpen(wrapper)).toHaveLength(0)
    expect(wrapper.find('[aria-live=polite]').text()).toContain('Event created')
    expect(router.currentRoute.value.path).toBe('/events/7')
  })

  it('shows a server error under its field and keeps the dialog open', async () => {
    serve(() => pageOf([event('1')]))
    post.mockResolvedValue({
      error: {
        status: 400,
        code: 'event.event-type-archived',
        errors: [{ field: 'eventTypeId', message: 'This event type is archived. Choose another one.' }],
      },
      response: new Response(null, { status: 400 }),
    })
    const { wrapper, router } = await mountView()
    await button(wrapper, 'New event')!.trigger('click')
    await wrapper.find('dialog input[name=title]').setValue('Practice')
    await wrapper.find('dialog select[name=eventTypeId]').setValue('t1')

    await wrapper.find('dialog form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('dialog').text()).toContain('This event type is archived. Choose another one.')
    expect(dialogOpen(wrapper)).toHaveLength(1)
    expect(router.currentRoute.value.path).toBe('/events')
  })

  it('shows an error banner with Retry when loading fails', async () => {
    get.mockImplementation(async (path: string) =>
      path === '/event-types'
        ? pageOf([])
        : { error: { status: 500, title: 'Server error' }, response: new Response(null, { status: 500 }) },
    )
    const { wrapper } = await mountView()
    expect(wrapper.find('[role=alert]').exists()).toBe(true)

    serve(() => pageOf([event('1')]))
    await button(wrapper, 'Retry')!.trigger('click')
    await flushPromises()

    expect(wrapper.findAll('li a')).toHaveLength(1)
  })
})
