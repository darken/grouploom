import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { api } from '@/api/client'
import { ApiError } from '@/api/errors'
import { useMembersStore } from '../members'

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

function member(id: string, overrides: object = {}) {
  return {
    id,
    name: `Member ${id}`,
    email: `m${id}@example.com`,
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

const input = { name: 'Ana', email: 'ana@example.com' }

describe('members store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    for (const m of [get, post, put, del]) m.mockReset()
  })

  it('fetch fills items and page info and sends q, page and the name sort', async () => {
    get.mockResolvedValue(pageOf([member('1'), member('2')], 1, 22))
    const store = useMembersStore()

    await store.fetch({ q: 'ana', page: 1 })

    expect(get).toHaveBeenCalledWith('/members', {
      params: { query: { q: 'ana', page: 1, size: 20, sort: ['name,asc'] } },
    })
    expect(store.items).toHaveLength(2)
    expect(store.pageInfo).toEqual({ number: 1, size: 20, totalElements: 22, totalPages: 2 })
    expect(store.page).toBe(1)
    expect(store.query).toBe('ana')
    expect(store.loading).toBe(false)
    expect(store.loaded).toBe(true)
  })

  it('fetch omits q when the query is blank', async () => {
    get.mockResolvedValue(pageOf([]))
    const store = useMembersStore()

    await store.fetch({ q: '  ', page: 0 })

    expect(get).toHaveBeenCalledWith('/members', {
      params: { query: { q: undefined, page: 0, size: 20, sort: ['name,asc'] } },
    })
  })

  it('fetch ignores a slow earlier response that arrives after a newer one', async () => {
    const slow = deferred()
    const fast = deferred()
    get.mockReturnValueOnce(slow.promise).mockReturnValueOnce(fast.promise)
    const store = useMembersStore()

    const first = store.fetch({ q: 'a', page: 0 })
    const second = store.fetch({ q: 'an', page: 0 })
    fast.resolve(pageOf([member('2')]))
    await second
    slow.resolve(pageOf([member('1')]))
    await first

    expect(store.items.map((i) => i.id)).toEqual(['2'])
    expect(store.query).toBe('an')
    expect(store.loading).toBe(false)
  })

  it('fetch ignores a failure from a superseded request', async () => {
    const slow = deferred()
    get.mockReturnValueOnce(slow.promise).mockResolvedValueOnce(pageOf([member('2')]))
    const store = useMembersStore()

    const first = store.fetch({ q: 'a', page: 0 })
    await store.fetch({ q: 'an', page: 0 })
    slow.resolve(problem(500))
    await first

    expect(store.error).toBeNull()
    expect(store.items).toHaveLength(1)
  })

  it('fetch exposes an ApiError and keeps the old items when the request fails', async () => {
    get.mockResolvedValueOnce(pageOf([member('1')]))
    const store = useMembersStore()
    await store.fetch({ q: '', page: 0 })
    get.mockResolvedValueOnce(problem(500))

    await store.fetch({ q: '', page: 0 })

    expect(store.error).toBeInstanceOf(ApiError)
    expect(store.items).toHaveLength(1)
  })

  it('create posts the input, refetches the current query and page and returns true', async () => {
    get.mockResolvedValue(pageOf([member('1')]))
    post.mockResolvedValue({ data: member('2'), response: new Response() })
    const store = useMembersStore()
    await store.fetch({ q: 'ana', page: 0 })
    get.mockClear()

    const ok = await store.create(input)

    expect(ok).toBe(true)
    expect(post).toHaveBeenCalledWith('/members', { body: input })
    expect(get).toHaveBeenCalledWith('/members', expect.objectContaining({ params: { query: expect.objectContaining({ q: 'ana', page: 0 }) } }))
    expect(store.saving).toBe(false)
  })

  it('update replaces the item in place and returns true', async () => {
    get.mockResolvedValue(pageOf([member('1'), member('2')]))
    put.mockResolvedValue({ data: member('1', { name: 'Renamed', version: 1 }), response: new Response() })
    const store = useMembersStore()
    await store.fetch({ q: '', page: 0 })
    get.mockClear()

    const ok = await store.update('1', { ...input, version: 0 })

    expect(ok).toBe(true)
    expect(put).toHaveBeenCalledWith('/members/{id}', {
      params: { path: { id: '1' } },
      body: { ...input, version: 0 },
    })
    expect(store.items[0]).toMatchObject({ name: 'Renamed', version: 1 })
    expect(store.items).toHaveLength(2)
    expect(get).not.toHaveBeenCalled()
  })

  it('create maps a 409 email-taken to a field error on email', async () => {
    post.mockResolvedValue(problem(409, { code: 'member.email-taken', field: 'email', detail: 'A member with this email already exists.' }))
    const store = useMembersStore()

    const ok = await store.create(input)

    expect(ok).toBe(false)
    expect(store.fieldErrors).toEqual({ email: 'A member with this email already exists.' })
    expect(store.stale).toBe(false)
  })

  it('create maps 400 validation errors to field errors', async () => {
    post.mockResolvedValue(problem(400, { errors: [{ field: 'name', message: 'must not be blank' }] }))
    const store = useMembersStore()

    await store.create(input)

    expect(store.fieldErrors).toEqual({ name: 'must not be blank' })
  })

  it('update marks the state stale on a 409 stale conflict', async () => {
    put.mockResolvedValue(problem(409, { code: 'member.stale' }))
    const store = useMembersStore()

    const ok = await store.update('1', { ...input, version: 0 })

    expect(ok).toBe(false)
    expect(store.stale).toBe(true)
    expect(store.fieldErrors).toEqual({})
  })

  it('clearSaveError resets the field errors and stale flag', async () => {
    put.mockResolvedValue(problem(409, { code: 'member.stale' }))
    const store = useMembersStore()
    await store.update('1', { ...input, version: 0 })

    store.clearSaveError()

    expect(store.stale).toBe(false)
  })

  it('remove deletes and refetches the current page', async () => {
    get.mockResolvedValue(pageOf([member('1'), member('2')]))
    del.mockResolvedValue({ response: new Response(null, { status: 204 }) })
    const store = useMembersStore()
    await store.fetch({ q: '', page: 0 })
    get.mockResolvedValue(pageOf([member('2')]))

    const ok = await store.remove('1')

    expect(ok).toBe(true)
    expect(del).toHaveBeenCalledWith('/members/{id}', { params: { path: { id: '1' } } })
    expect(store.items.map((i) => i.id)).toEqual(['2'])
  })

  it('remove steps back one page when the last page becomes empty', async () => {
    get.mockResolvedValueOnce(pageOf([member('21')], 1, 21))
    del.mockResolvedValue({ response: new Response(null, { status: 204 }) })
    const store = useMembersStore()
    await store.fetch({ q: '', page: 1 })
    get.mockClear()
    get.mockResolvedValueOnce(pageOf([], 1, 20))
    get.mockResolvedValueOnce(pageOf([member('1')], 0, 20))

    await store.remove('21')

    expect(get).toHaveBeenLastCalledWith('/members', {
      params: { query: { q: undefined, page: 0, size: 20, sort: ['name,asc'] } },
    })
    expect(store.page).toBe(0)
  })

  it('remove exposes an ApiError when deleting fails', async () => {
    del.mockResolvedValue(problem(404))
    const store = useMembersStore()

    const ok = await store.remove('1')

    expect(ok).toBe(false)
    expect(store.error?.status).toBe(404)
  })

  it('createPreferencesLink posts for the member and returns the url and expiry', async () => {
    post.mockResolvedValue({
      data: { url: 'https://app.example.com/m/preferences#t=abc', expiresAt: '2026-03-01T00:00:00Z' },
      response: new Response(),
    })
    const store = useMembersStore()

    const link = await store.createPreferencesLink('m1')

    expect(post).toHaveBeenCalledWith('/members/{id}/preferences-link', { params: { path: { id: 'm1' } } })
    expect(link).toEqual({ url: 'https://app.example.com/m/preferences#t=abc', expiresAt: '2026-03-01T00:00:00Z' })
    expect(store.error).toBeNull()
  })

  it('createPreferencesLink returns null and reports the error when it fails', async () => {
    post.mockResolvedValue(problem(404))
    const store = useMembersStore()

    const link = await store.createPreferencesLink('gone')

    expect(link).toBeNull()
    expect(store.error?.status).toBe(404)
  })

  it('createPreferencesLink returns null on a network failure', async () => {
    post.mockRejectedValue(new TypeError('Failed to fetch'))
    const store = useMembersStore()

    expect(await store.createPreferencesLink('m1')).toBeNull()
    expect(store.error?.status).toBe(0)
  })

  it('keeps the paused flag the server sends', async () => {
    get.mockResolvedValue(pageOf([member('1', { invitationsPaused: true }), member('2', { invitationsPaused: false })]))
    const store = useMembersStore()

    await store.fetch({ q: '', page: 0 })

    expect(store.items.map((m) => m.invitationsPaused)).toEqual([true, false])
  })
})
