import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { api } from '@/api/client'
import { ApiError } from '@/api/errors'
import { useEventsStore } from '../events'

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

function event(id: string, overrides: object = {}) {
  return {
    id,
    title: `Event ${id}`,
    start: '2026-11-02T09:00:00Z',
    end: '2026-11-02T11:00:00Z',
    localStart: '2026-11-02T10:00:00',
    localEnd: '2026-11-02T12:00:00',
    timeZone: 'Europe/Madrid',
    eventType: { id: 't1', name: 'Practice', color: 'green', archived: false },
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

function pageOf(items: object[], number = 0, totalElements = items.length, size = 20) {
  return {
    data: {
      content: items,
      page: { number, size, totalElements, totalPages: Math.max(1, Math.ceil(totalElements / size)) },
    },
    response: new Response(),
  }
}

function problem(status: number, extra: object = {}) {
  return { error: { status, title: 'Err', ...extra }, response: new Response(null, { status }) }
}

function deferred() {
  let resolve!: (value: unknown) => void
  const promise = new Promise((r) => (resolve = r))
  return { promise, resolve }
}

const input = {
  eventTypeId: 't1',
  title: 'Practice',
  localStart: '2026-11-02T10:00:00',
  localEnd: '2026-11-02T12:00:00',
  timeZone: 'Europe/Madrid',
}

describe('events store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    for (const m of [get, post, put, del]) m.mockReset()
  })

  it('fetch fills items and page info and sends when, eventTypeId and page', async () => {
    get.mockResolvedValue(pageOf([event('1'), event('2')], 1, 22))
    const store = useEventsStore()

    await store.fetch({ when: 'past', eventTypeId: 't1', page: 1 })

    expect(get).toHaveBeenCalledWith('/events', {
      params: { query: { when: 'past', eventTypeId: 't1', page: 1, size: 20 } },
    })
    expect(store.items).toHaveLength(2)
    expect(store.pageInfo).toEqual({ number: 1, size: 20, totalElements: 22, totalPages: 2 })
    expect(store.page).toBe(1)
    expect(store.when).toBe('past')
    expect(store.eventTypeId).toBe('t1')
    expect(store.loaded).toBe(true)
    expect(store.loading).toBe(false)
  })

  it('fetch omits the type filter when it is empty', async () => {
    get.mockResolvedValue(pageOf([]))
    const store = useEventsStore()

    await store.fetch({ when: 'upcoming', eventTypeId: '', page: 0 })

    expect(get).toHaveBeenCalledWith('/events', {
      params: { query: { when: 'upcoming', eventTypeId: undefined, page: 0, size: 20 } },
    })
  })

  it('fetch ignores a slow earlier response that arrives after a newer one', async () => {
    const slow = deferred()
    const fast = deferred()
    get.mockReturnValueOnce(slow.promise).mockReturnValueOnce(fast.promise)
    const store = useEventsStore()

    const first = store.fetch({ when: 'upcoming', eventTypeId: '', page: 0 })
    const second = store.fetch({ when: 'past', eventTypeId: '', page: 0 })
    fast.resolve(pageOf([event('2')]))
    await second
    slow.resolve(pageOf([event('1')]))
    await first

    expect(store.items.map((i) => i.id)).toEqual(['2'])
    expect(store.when).toBe('past')
    expect(store.loading).toBe(false)
  })

  it('fetch exposes an ApiError and keeps the old items when the request fails', async () => {
    get.mockResolvedValueOnce(pageOf([event('1')]))
    const store = useEventsStore()
    await store.fetch({ when: 'upcoming', eventTypeId: '', page: 0 })
    get.mockResolvedValueOnce(problem(500))

    await store.fetch({ when: 'upcoming', eventTypeId: '', page: 0 })

    expect(store.error).toBeInstanceOf(ApiError)
    expect(store.items).toHaveLength(1)
  })

  it('fetchOne loads the event into current', async () => {
    get.mockResolvedValue({ data: event('1'), response: new Response() })
    const store = useEventsStore()

    await store.fetchOne('1')

    expect(get).toHaveBeenCalledWith('/events/{id}', { params: { path: { id: '1' } } })
    expect(store.current?.id).toBe('1')
    expect(store.currentLoading).toBe(false)
  })

  it('fetchOne exposes a 404 and clears current', async () => {
    get.mockResolvedValueOnce({ data: event('1'), response: new Response() })
    const store = useEventsStore()
    await store.fetchOne('1')
    get.mockResolvedValueOnce(problem(404))

    await store.fetchOne('2')

    expect(store.current).toBeNull()
    expect(store.currentError?.status).toBe(404)
  })

  it('fetchOne clears a different event while the new one loads, but keeps the same one', async () => {
    const slow = deferred()
    get.mockResolvedValueOnce({ data: event('1'), response: new Response() })
    const store = useEventsStore()
    await store.fetchOne('1')
    get.mockReturnValueOnce(slow.promise)

    const reload = store.fetchOne('1')
    expect(store.current?.id).toBe('1')
    slow.resolve({ data: event('1'), response: new Response() })
    await reload

    get.mockReturnValueOnce(slow.promise)
    const other = store.fetchOne('2')
    expect(store.current).toBeNull()
    await other.catch(() => undefined)
  })

  it('fetchOne ignores an earlier response that arrives after a newer one', async () => {
    const slow = deferred()
    get.mockReturnValueOnce(slow.promise).mockResolvedValueOnce({ data: event('2'), response: new Response() })
    const store = useEventsStore()

    const first = store.fetchOne('1')
    await store.fetchOne('2')
    slow.resolve({ data: event('1'), response: new Response() })
    await first

    expect(store.current?.id).toBe('2')
  })

  it('fetchNextUp requests the three nearest upcoming events', async () => {
    get.mockResolvedValue(pageOf([event('1'), event('2')]))
    const store = useEventsStore()

    await store.fetchNextUp()

    expect(get).toHaveBeenCalledWith('/events', { params: { query: { when: 'upcoming', page: 0, size: 3 } } })
    expect(store.nextUp.map((e) => e.id)).toEqual(['1', '2'])
    expect(store.nextUpLoaded).toBe(true)
  })

  it('fetchNextUp does not touch the list state', async () => {
    get.mockResolvedValueOnce(pageOf([event('1')]))
    const store = useEventsStore()
    await store.fetch({ when: 'past', eventTypeId: '', page: 0 })
    get.mockResolvedValueOnce(pageOf([event('9')]))

    await store.fetchNextUp()

    expect(store.items.map((i) => i.id)).toEqual(['1'])
    expect(store.when).toBe('past')
  })

  it('create posts the input and returns the created event', async () => {
    post.mockResolvedValue({ data: event('7'), response: new Response() })
    const store = useEventsStore()

    const created = await store.create(input)

    expect(post).toHaveBeenCalledWith('/events', { body: input })
    expect(created?.id).toBe('7')
    expect(store.saving).toBe(false)
  })

  it('create returns null and maps 400 validation errors to field errors', async () => {
    post.mockResolvedValue(
      problem(400, {
        code: 'event.start-in-past',
        errors: [{ field: 'localStart', message: "The start can't be in the past." }],
      }),
    )
    const store = useEventsStore()

    const created = await store.create(input)

    expect(created).toBeNull()
    expect(store.fieldErrors).toEqual({ localStart: "The start can't be in the past." })
    expect(store.stale).toBe(false)
  })

  it('maps an event-type error to the eventTypeId field', async () => {
    post.mockResolvedValue(
      problem(400, { code: 'event.event-type-archived', errors: [{ field: 'eventTypeId', message: 'This event type is archived.' }] }),
    )
    const store = useEventsStore()

    await store.create(input)

    expect(store.fieldErrors).toEqual({ eventTypeId: 'This event type is archived.' })
  })

  it('update patches both current and the list item in place', async () => {
    get.mockResolvedValueOnce(pageOf([event('1'), event('2')]))
    get.mockResolvedValueOnce({ data: event('1'), response: new Response() })
    put.mockResolvedValue({ data: event('1', { title: 'Renamed', version: 1 }), response: new Response() })
    const store = useEventsStore()
    await store.fetch({ when: 'upcoming', eventTypeId: '', page: 0 })
    await store.fetchOne('1')

    const ok = await store.update('1', { ...input, version: 0 })

    expect(ok).toBe(true)
    expect(put).toHaveBeenCalledWith('/events/{id}', { params: { path: { id: '1' } }, body: { ...input, version: 0 } })
    expect(store.current).toMatchObject({ title: 'Renamed', version: 1 })
    expect(store.items[0]).toMatchObject({ title: 'Renamed', version: 1 })
    expect(store.items).toHaveLength(2)
  })

  it('update leaves current alone when it is a different event', async () => {
    get.mockResolvedValueOnce({ data: event('9'), response: new Response() })
    put.mockResolvedValue({ data: event('1', { title: 'Renamed' }), response: new Response() })
    const store = useEventsStore()
    await store.fetchOne('9')

    await store.update('1', { ...input, version: 0 })

    expect(store.current?.id).toBe('9')
  })

  it('update marks the state stale on a 409 stale conflict', async () => {
    put.mockResolvedValue(problem(409, { code: 'event.stale' }))
    const store = useEventsStore()

    const ok = await store.update('1', { ...input, version: 0 })

    expect(ok).toBe(false)
    expect(store.stale).toBe(true)
    expect(store.fieldErrors).toEqual({})
  })

  it('clearSaveError resets the field errors and stale flag', async () => {
    put.mockResolvedValue(problem(409, { code: 'event.stale' }))
    const store = useEventsStore()
    await store.update('1', { ...input, version: 0 })

    store.clearSaveError()

    expect(store.stale).toBe(false)
  })

  it('remove deletes and returns "ok"', async () => {
    del.mockResolvedValue({ response: new Response(null, { status: 204 }) })
    const store = useEventsStore()

    const result = await store.remove('1')

    expect(result).toBe('ok')
    expect(del).toHaveBeenCalledWith('/events/{id}', { params: { path: { id: '1' } } })
  })

  it('remove drops the event from the list and clears current', async () => {
    get.mockResolvedValueOnce(pageOf([event('1'), event('2')], 0, 2))
    get.mockResolvedValueOnce({ data: event('1'), response: new Response() })
    del.mockResolvedValue({ response: new Response(null, { status: 204 }) })
    const store = useEventsStore()
    await store.fetch({ when: 'upcoming', eventTypeId: '', page: 0 })
    await store.fetchOne('1')

    await store.remove('1')

    expect(store.items.map((i) => i.id)).toEqual(['2'])
    expect(store.pageInfo?.totalElements).toBe(1)
    expect(store.current).toBeNull()
  })

  it('remove exposes an ApiError when deleting fails', async () => {
    del.mockResolvedValue(problem(404))
    const store = useEventsStore()

    const result = await store.remove('1')

    expect(result).toBe('error')
    expect(store.error?.status).toBe(404)
  })

  it('remove maps the 409 event.has-notifications to a typed result and keeps the event', async () => {
    get.mockResolvedValueOnce({ data: event('1'), response: new Response() })
    del.mockResolvedValue(problem(409, { code: 'event.has-notifications' }))
    const store = useEventsStore()
    await store.fetchOne('1')

    const result = await store.remove('1')

    expect(result).toBe('has-notifications')
    expect(store.error).toBeNull()
    expect(store.current?.id).toBe('1')
  })

  it('cancel posts the message and updates current and the list item', async () => {
    get.mockResolvedValueOnce(pageOf([event('1'), event('2')], 0, 2))
    get.mockResolvedValueOnce({ data: event('1'), response: new Response() })
    const store = useEventsStore()
    await store.fetch({ when: 'upcoming', eventTypeId: '', page: 0 })
    await store.fetchOne('1')
    const cancelled = event('1', { cancelledAt: '2026-10-20T08:00:00Z', cancellationMessage: 'Gym closed', version: 4 })
    post.mockResolvedValue({ data: cancelled, response: new Response() })

    const result = await store.cancel('1', 'Gym closed')

    expect(post).toHaveBeenCalledWith('/events/{id}/cancel', {
      params: { path: { id: '1' } },
      body: { message: 'Gym closed' },
    })
    expect(result).toEqual({ status: 'ok', event: cancelled })
    expect(store.current?.cancelledAt).toBe('2026-10-20T08:00:00Z')
    expect(store.items.find((i) => i.id === '1')?.cancelledAt).toBe('2026-10-20T08:00:00Z')
    expect(store.items.find((i) => i.id === '2')?.cancelledAt).toBeUndefined()
  })

  it('cancel without a message sends none', async () => {
    post.mockResolvedValue({ data: event('1', { cancelledAt: '2026-10-20T08:00:00Z' }), response: new Response() })
    const store = useEventsStore()

    await store.cancel('1', '  ')

    expect(post).toHaveBeenCalledWith('/events/{id}/cancel', { params: { path: { id: '1' } }, body: {} })
  })

  it.each([
    ['event.cancelled', 'cancelled'],
    ['event.ended', 'ended'],
  ])('cancel maps the 409 %s to the typed result %s', async (code, status) => {
    post.mockResolvedValue(problem(409, { code }))
    const store = useEventsStore()

    expect(await store.cancel('1', '')).toEqual({ status })
  })

  it('cancel keeps other failures as an error', async () => {
    post.mockResolvedValue(problem(500))
    const store = useEventsStore()

    expect(await store.cancel('1', '')).toEqual({ status: 'error' })
    expect(store.saveError?.status).toBe(500)
  })
})
