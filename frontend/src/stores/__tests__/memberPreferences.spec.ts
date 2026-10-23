import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { api } from '@/api/client'
import { useMemberPreferencesStore } from '../memberPreferences'

vi.mock('@/api/client', () => ({
  api: { GET: vi.fn<() => Promise<unknown>>(), PUT: vi.fn<() => Promise<unknown>>() },
}))

const get = vi.mocked(api.GET) as unknown as ReturnType<typeof vi.fn>
const put = vi.mocked(api.PUT) as unknown as ReturnType<typeof vi.fn>

const STORAGE_KEY = 'gl_member_token'

function prefs(overrides: object = {}) {
  return {
    memberName: 'Ana',
    maskedEmail: 'a••@example.com',
    groupName: 'Chess Club',
    invitationsPaused: false,
    eventTypes: [
      { id: 't1', name: 'Practice', color: 'blue', subscribed: true },
      { id: 't2', name: 'Game night', description: 'Fridays', color: 'green', subscribed: true },
    ],
    ...overrides,
  }
}

const ok = (data: object) => ({ data, response: new Response() })
const problem = (status: number, code?: string) => ({
  error: { status, title: 'Err', ...(code ? { code } : {}) },
  response: new Response(null, { status }),
})

function deferred() {
  let resolve!: (value: unknown) => void
  const promise = new Promise((r) => (resolve = r))
  return { promise, resolve }
}

const bodyOf = (call: number) => put.mock.calls[call]![1].body

function readyStore() {
  const store = useMemberPreferencesStore()
  store.init('#t=abc')
  get.mockResolvedValue(ok(prefs()))
  return store
}

describe('memberPreferences store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    get.mockReset()
    put.mockReset()
    sessionStorage.clear()
    window.history.replaceState(null, '', '/m/preferences')
  })

  afterEach(() => vi.restoreAllMocks())

  describe('init', () => {
    it('reads the token from the fragment, keeps it for reloads and removes the fragment', () => {
      window.history.replaceState(null, '', '/m/preferences?x=1#t=abc123')
      const store = useMemberPreferencesStore()

      store.init(window.location.hash)

      expect(store.token).toBe('abc123')
      expect(sessionStorage.getItem(STORAGE_KEY)).toBe('abc123')
      expect(window.location.hash).toBe('')
      expect(window.location.pathname + window.location.search).toBe('/m/preferences?x=1')
    })

    it('finds the token among other fragment parameters', () => {
      const store = useMemberPreferencesStore()

      store.init('#foo=1&t=abc-_9&bar=2')

      expect(store.token).toBe('abc-_9')
    })

    it('falls back to the token saved for this tab when the fragment is gone', () => {
      sessionStorage.setItem(STORAGE_KEY, 'saved')
      const store = useMemberPreferencesStore()

      store.init('')

      expect(store.token).toBe('saved')
    })

    it('prefers a new fragment token over a saved one', () => {
      sessionStorage.setItem(STORAGE_KEY, 'old')
      const store = useMemberPreferencesStore()

      store.init('#t=new')

      expect(store.token).toBe('new')
      expect(sessionStorage.getItem(STORAGE_KEY)).toBe('new')
    })

    it('is invalid without any token, and never calls the API', async () => {
      const store = useMemberPreferencesStore()

      store.init('')
      await store.load()

      expect(store.token).toBeNull()
      expect(store.status).toBe('invalid')
      expect(get).not.toHaveBeenCalled()
    })

    it('treats an empty t parameter as no token', () => {
      const store = useMemberPreferencesStore()

      store.init('#t=')

      expect(store.status).toBe('invalid')
    })

    it('still works when sessionStorage is unavailable', () => {
      vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
        throw new Error('blocked')
      })
      vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
        throw new Error('blocked')
      })
      const store = useMemberPreferencesStore()

      store.init('#t=abc')

      expect(store.token).toBe('abc')
    })
  })

  describe('load', () => {
    it('fetches the preferences with the member token header', async () => {
      const store = readyStore()

      await store.load()

      expect(get).toHaveBeenCalledWith('/public/preferences', { params: { header: { 'X-Member-Token': 'abc' } } })
      expect(store.status).toBe('ready')
      expect(store.data?.memberName).toBe('Ana')
    })

    it('starts out loading', () => {
      const store = readyStore()
      get.mockReturnValue(new Promise(() => {}))

      void store.load()

      expect(store.status).toBe('loading')
    })

    it('maps a 401 member-token.expired to expired', async () => {
      const store = readyStore()
      get.mockResolvedValue(problem(401, 'member-token.expired'))

      await store.load()

      expect(store.status).toBe('expired')
      expect(store.data).toBeNull()
    })

    it('maps any other 401 to invalid', async () => {
      const store = readyStore()
      get.mockResolvedValue(problem(401, 'member-token.invalid'))

      await store.load()
      expect(store.status).toBe('invalid')

      get.mockResolvedValue(problem(401))
      await store.load()
      expect(store.status).toBe('invalid')
    })

    it('reports other failures as an error that can be retried', async () => {
      const store = readyStore()
      get.mockResolvedValueOnce(problem(500))

      await store.load()
      expect(store.status).toBe('error')
      expect(store.error?.status).toBe(500)

      await store.load()
      expect(store.status).toBe('ready')
      expect(store.error).toBeNull()
    })

    it('reports a network failure as an error', async () => {
      const store = readyStore()
      get.mockRejectedValue(new TypeError('Failed to fetch'))

      await store.load()

      expect(store.status).toBe('error')
    })
  })

  describe('saving', () => {
    it('updates optimistically and sends the full state', async () => {
      const store = readyStore()
      await store.load()
      const pending = deferred()
      put.mockReturnValue(pending.promise)

      store.setSubscribed('t1', false)

      expect(store.data?.eventTypes[0]?.subscribed).toBe(false)
      expect(store.saveStatus).toBe('saving')
      expect(put).toHaveBeenCalledWith('/public/preferences', {
        params: { header: { 'X-Member-Token': 'abc' } },
        body: {
          invitationsPaused: false,
          eventTypes: [
            { id: 't1', subscribed: false },
            { id: 't2', subscribed: true },
          ],
        },
      })

      pending.resolve(ok(prefs({ eventTypes: [{ id: 't1', name: 'Practice', color: 'blue', subscribed: false }] })))
      await vi.waitFor(() => expect(store.saveStatus).toBe('saved'))
      expect(store.data?.eventTypes).toHaveLength(1)
    })

    it('pauses and unpauses with the same request', async () => {
      const store = readyStore()
      await store.load()
      put.mockResolvedValue(ok(prefs({ invitationsPaused: true })))

      store.setPaused(true)

      expect(store.data?.invitationsPaused).toBe(true)
      expect(bodyOf(0).invitationsPaused).toBe(true)
      await vi.waitFor(() => expect(store.saveStatus).toBe('saved'))
    })

    it('keeps one request in flight and saves only the latest state afterwards', async () => {
      const store = readyStore()
      await store.load()
      const first = deferred()
      const second = deferred()
      put.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)

      store.setSubscribed('t1', false)
      store.setSubscribed('t2', false)
      store.setSubscribed('t1', true)
      store.setPaused(true)

      expect(put).toHaveBeenCalledTimes(1)
      expect(store.data?.eventTypes.map((t) => t.subscribed)).toEqual([true, false])

      first.resolve(ok(prefs()))
      await vi.waitFor(() => expect(put).toHaveBeenCalledTimes(2))
      expect(bodyOf(1)).toEqual({
        invitationsPaused: true,
        eventTypes: [
          { id: 't1', subscribed: true },
          { id: 't2', subscribed: false },
        ],
      })
      // The stale response of the first request must not overwrite what the user just chose.
      expect(store.data?.invitationsPaused).toBe(true)
      expect(store.saveStatus).toBe('saving')

      second.resolve(ok(prefs({ invitationsPaused: true })))
      await vi.waitFor(() => expect(store.saveStatus).toBe('saved'))
      expect(put).toHaveBeenCalledTimes(2)
    })

    it('reverts to the last saved state and reports an error when saving fails', async () => {
      const store = readyStore()
      await store.load()
      put.mockResolvedValue(problem(500))

      store.setSubscribed('t1', false)
      await vi.waitFor(() => expect(store.saveStatus).toBe('error'))

      expect(store.data?.eventTypes[0]?.subscribed).toBe(true)
      expect(store.error?.status).toBe(500)
      expect(store.status).toBe('ready')
    })

    it('reverts to the last successful save, not to the initial state', async () => {
      const store = readyStore()
      await store.load()
      put.mockResolvedValueOnce(ok(prefs({ invitationsPaused: true })))
      store.setPaused(true)
      await vi.waitFor(() => expect(store.saveStatus).toBe('saved'))
      put.mockResolvedValueOnce(problem(500))

      store.setSubscribed('t2', false)
      await vi.waitFor(() => expect(store.saveStatus).toBe('error'))

      expect(store.data?.invitationsPaused).toBe(true)
      expect(store.data?.eventTypes[1]?.subscribed).toBe(true)
    })

    it('retries the failed change', async () => {
      const store = readyStore()
      await store.load()
      put.mockResolvedValueOnce(problem(500))
      store.setSubscribed('t1', false)
      await vi.waitFor(() => expect(store.saveStatus).toBe('error'))
      put.mockResolvedValueOnce(
        ok(prefs({ eventTypes: [{ id: 't1', name: 'Practice', color: 'blue', subscribed: false }] })),
      )

      store.retry()

      expect(store.data?.eventTypes[0]?.subscribed).toBe(false)
      expect(store.error).toBeNull()
      await vi.waitFor(() => expect(store.saveStatus).toBe('saved'))
      expect(bodyOf(1).eventTypes[0]).toEqual({ id: 't1', subscribed: false })
    })

    it('clears a previous error when the member changes something else', async () => {
      const store = readyStore()
      await store.load()
      put.mockResolvedValueOnce(problem(500))
      store.setSubscribed('t1', false)
      await vi.waitFor(() => expect(store.saveStatus).toBe('error'))
      put.mockResolvedValueOnce(ok(prefs({ invitationsPaused: true })))

      store.setPaused(true)

      expect(store.error).toBeNull()
      await vi.waitFor(() => expect(store.saveStatus).toBe('saved'))
    })

    it('switches to the expired page when the link expires while the page is open', async () => {
      const store = readyStore()
      await store.load()
      put.mockResolvedValue(problem(401, 'member-token.expired'))

      store.setPaused(true)

      await vi.waitFor(() => expect(store.status).toBe('expired'))
    })

    it('reloads when the event types changed under the member', async () => {
      const store = readyStore()
      await store.load()
      put.mockResolvedValue(problem(400, 'preference.unknown-event-type'))
      get.mockResolvedValue(ok(prefs({ eventTypes: [{ id: 't2', name: 'Game night', color: 'green', subscribed: true }] })))

      store.setSubscribed('t1', false)

      await vi.waitFor(() => expect(store.data?.eventTypes).toHaveLength(1))
      expect(store.saveStatus).toBe('error')
    })

    it('ignores changes before the preferences are loaded', () => {
      const store = readyStore()

      store.setPaused(true)
      store.setSubscribed('t1', false)

      expect(put).not.toHaveBeenCalled()
    })

    it('ignores unknown event types', async () => {
      const store = readyStore()
      await store.load()

      store.setSubscribed('nope', false)

      expect(put).not.toHaveBeenCalled()
    })
  })
})
