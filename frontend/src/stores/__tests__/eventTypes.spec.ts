import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { api } from '@/api/client'
import { ApiError } from '@/api/errors'
import { useEventTypesStore } from '../eventTypes'

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

function item(id: string, overrides: object = {}) {
  return {
    id,
    name: `Type ${id}`,
    color: 'blue',
    archived: false,
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

const input = { name: 'Practice', color: 'green' as const }

describe('event types store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    for (const m of [get, post, put, del]) m.mockReset()
  })

  it('fetch fills items and page info for the requested filter and page', async () => {
    get.mockResolvedValue(pageOf([item('1'), item('2')], 1, 22))
    const store = useEventTypesStore()

    await store.fetch({ archived: true, page: 1 })

    expect(get).toHaveBeenCalledWith('/event-types', {
      params: { query: { archived: true, page: 1, size: 20, sort: ['name,asc'] } },
    })
    expect(store.items).toHaveLength(2)
    expect(store.pageInfo).toEqual({ number: 1, size: 20, totalElements: 22, totalPages: 2 })
    expect(store.archived).toBe(true)
    expect(store.loading).toBe(false)
    expect(store.loaded).toBe(true)
  })

  it('fetch exposes an ApiError and keeps the old items when the request fails', async () => {
    get.mockResolvedValueOnce(pageOf([item('1')]))
    const store = useEventTypesStore()
    await store.fetch({ archived: false, page: 0 })
    get.mockResolvedValueOnce(problem(500))

    await store.fetch({ archived: false, page: 0 })

    expect(store.error).toBeInstanceOf(ApiError)
    expect(store.items).toHaveLength(1)
  })

  it('create posts the input, refetches the current page and returns true', async () => {
    get.mockResolvedValue(pageOf([item('1')]))
    post.mockResolvedValue({ data: item('2'), response: new Response() })
    const store = useEventTypesStore()
    await store.fetch({ archived: false, page: 0 })
    get.mockClear()

    const ok = await store.create(input)

    expect(ok).toBe(true)
    expect(post).toHaveBeenCalledWith('/event-types', { body: input })
    expect(get).toHaveBeenCalledOnce()
    expect(store.saving).toBe(false)
  })

  it('create maps a 409 name-taken to a field error on name', async () => {
    post.mockResolvedValue(problem(409, { code: 'event-type.name-taken', field: 'name', detail: 'An event type with this name already exists.' }))
    const store = useEventTypesStore()

    const ok = await store.create(input)

    expect(ok).toBe(false)
    expect(store.fieldErrors).toEqual({ name: 'An event type with this name already exists.' })
    expect(store.stale).toBe(false)
  })

  it('create maps 400 validation errors to field errors', async () => {
    post.mockResolvedValue(problem(400, { errors: [{ field: 'color', message: 'must not be null' }] }))
    const store = useEventTypesStore()

    await store.create(input)

    expect(store.fieldErrors).toEqual({ color: 'must not be null' })
  })

  it('update replaces the item in place when it still matches the filter', async () => {
    get.mockResolvedValue(pageOf([item('1'), item('2')]))
    put.mockResolvedValue({ data: item('1', { name: 'Renamed', version: 1 }), response: new Response() })
    const store = useEventTypesStore()
    await store.fetch({ archived: false, page: 0 })

    const ok = await store.update('1', { ...input, archived: false, version: 0 })

    expect(ok).toBe(true)
    expect(put).toHaveBeenCalledWith('/event-types/{id}', {
      params: { path: { id: '1' } },
      body: { ...input, archived: false, version: 0 },
    })
    expect(store.items[0]).toMatchObject({ name: 'Renamed', version: 1 })
    expect(store.items).toHaveLength(2)
  })

  it('update drops the item when its archived state no longer matches the filter', async () => {
    get.mockResolvedValue(pageOf([item('1'), item('2')], 0, 2))
    put.mockResolvedValue({ data: item('1', { archived: true, version: 1 }), response: new Response() })
    const store = useEventTypesStore()
    await store.fetch({ archived: false, page: 0 })

    await store.update('1', { ...input, archived: true, version: 0 })

    expect(store.items.map((i) => i.id)).toEqual(['2'])
    expect(store.pageInfo?.totalElements).toBe(1)
  })

  it('update records the saved item as lastSaved, even when it leaves the list', async () => {
    get.mockResolvedValue(pageOf([item('1')]))
    put.mockResolvedValue({ data: item('1', { archived: true, version: 4 }), response: new Response() })
    const store = useEventTypesStore()
    await store.fetch({ archived: false, page: 0 })

    await store.update('1', { ...input, archived: true, version: 3 })

    expect(store.lastSaved).toMatchObject({ id: '1', archived: true, version: 4 })
  })

  it('update marks the state stale on a 409 stale conflict', async () => {
    put.mockResolvedValue(problem(409, { code: 'event-type.stale' }))
    const store = useEventTypesStore()

    const ok = await store.update('1', { ...input, archived: false, version: 0 })

    expect(ok).toBe(false)
    expect(store.stale).toBe(true)
    expect(store.fieldErrors).toEqual({})
  })

  it('clearSaveError resets the field errors and stale flag', async () => {
    put.mockResolvedValue(problem(409, { code: 'event-type.stale' }))
    const store = useEventTypesStore()
    await store.update('1', { ...input, archived: false, version: 0 })

    store.clearSaveError()

    expect(store.stale).toBe(false)
  })

  it('remove deletes and refetches the current page', async () => {
    get.mockResolvedValue(pageOf([item('1'), item('2')]))
    del.mockResolvedValue({ response: new Response(null, { status: 204 }) })
    const store = useEventTypesStore()
    await store.fetch({ archived: false, page: 0 })
    get.mockClear()
    get.mockResolvedValue(pageOf([item('2')]))

    const ok = await store.remove('1')

    expect(ok).toBe(true)
    expect(del).toHaveBeenCalledWith('/event-types/{id}', { params: { path: { id: '1' } } })
    expect(store.items.map((i) => i.id)).toEqual(['2'])
  })

  it('remove steps back one page when the last page becomes empty', async () => {
    get.mockResolvedValueOnce(pageOf([item('21')], 1, 21))
    del.mockResolvedValue({ response: new Response(null, { status: 204 }) })
    const store = useEventTypesStore()
    await store.fetch({ archived: false, page: 1 })
    get.mockClear()
    get.mockResolvedValueOnce(pageOf([], 1, 20))
    get.mockResolvedValueOnce(pageOf([item('1')], 0, 20))

    await store.remove('21')

    expect(get).toHaveBeenLastCalledWith('/event-types', {
      params: { query: { archived: false, page: 0, size: 20, sort: ['name,asc'] } },
    })
    expect(store.pageInfo?.number).toBe(0)
  })

  it('remove exposes an ApiError when deleting fails', async () => {
    del.mockResolvedValue(problem(404))
    const store = useEventTypesStore()

    const ok = await store.remove('1')

    expect(ok).toBe(false)
    expect(store.error?.status).toBe(404)
  })

  describe('picker options', () => {
    it('fetchActiveOptions loads up to 100 active types sorted by name', async () => {
      get.mockResolvedValue(pageOf([item('1'), item('2')]))
      const store = useEventTypesStore()

      await store.fetchActiveOptions()

      expect(get).toHaveBeenCalledWith('/event-types', {
        params: { query: { archived: false, page: 0, size: 100, sort: ['name,asc'] } },
      })
      expect(store.activeOptions.map((i) => i.id)).toEqual(['1', '2'])
      expect(store.optionsLoaded).toBe(true)
    })

    it('fetchActiveOptions is cached until something changes', async () => {
      get.mockResolvedValue(pageOf([item('1')]))
      const store = useEventTypesStore()

      await store.fetchActiveOptions()
      await store.fetchActiveOptions()

      expect(get).toHaveBeenCalledOnce()
    })

    it('fetchArchivedOptions loads archived types for the filter', async () => {
      get.mockResolvedValue(pageOf([item('9', { archived: true })]))
      const store = useEventTypesStore()

      await store.fetchArchivedOptions()

      expect(get).toHaveBeenCalledWith('/event-types', {
        params: { query: { archived: true, page: 0, size: 100, sort: ['name,asc'] } },
      })
      expect(store.archivedOptions.map((i) => i.id)).toEqual(['9'])
    })

    it('create, update and remove drop the cache so the next fetch reloads', async () => {
      get.mockResolvedValue(pageOf([item('1')]))
      post.mockResolvedValue({ data: item('2'), response: new Response() })
      put.mockResolvedValue({ data: item('1', { archived: true }), response: new Response() })
      del.mockResolvedValue({ response: new Response(null, { status: 204 }) })
      const store = useEventTypesStore()
      await store.fetch({ archived: false, page: 0 })
      await store.fetchActiveOptions()
      expect(get).toHaveBeenCalledTimes(2)

      await store.create(input)
      await store.fetchActiveOptions()
      await store.update('1', { ...input, archived: true, version: 0 })
      await store.fetchActiveOptions()
      await store.remove('1')
      await store.fetchActiveOptions()

      // 2 initial + 1 reload per mutation on the list (create/remove) + 1 options reload per mutation.
      const optionCalls = get.mock.calls.filter(([, init]) => init?.params?.query?.size === 100)
      expect(optionCalls).toHaveLength(4)
    })
  })
})
