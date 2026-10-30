import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { api } from '@/api/client'
import { useAttendanceStore } from '../attendance'

vi.mock('@/api/client', () => ({
  api: {
    GET: vi.fn<() => Promise<unknown>>(),
    PUT: vi.fn<() => Promise<unknown>>(),
    DELETE: vi.fn<() => Promise<unknown>>(),
  },
}))

const get = vi.mocked(api.GET) as unknown as ReturnType<typeof vi.fn>
const put = vi.mocked(api.PUT) as unknown as ReturnType<typeof vi.fn>
const del = vi.mocked(api.DELETE) as unknown as ReturnType<typeof vi.fn>

const ok = (data: object) => ({ data, response: new Response() })
const noContent = () => ({ response: new Response(null, { status: 204 }) })
const failure = (status: number, code?: string) => ({
  error: { status, title: 'Err', ...(code ? { code } : {}) },
  response: new Response(null, { status }),
})

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((r) => (resolve = r))
  return { promise, resolve }
}

const summary = (going = 1, notGoing = 0, noReply = 1) => ({ going, notGoing, noReply, members: going + notGoing + noReply })

function row(id: string, name: string, extra: object = {}) {
  return { member: { id, name, email: `${name.toLowerCase()}@example.com` }, ...extra }
}

function page(content: object[], number = 0, totalPages = 1) {
  return { content, page: { number, size: 50, totalElements: content.length, totalPages } }
}

const going = row('m1', 'Ana', { response: 'GOING', source: 'MEMBER', respondedAt: '2026-11-01T10:00:00Z' })
const silent = row('m2', 'Bob')

/** GET answers by URL, so the summary and rows requests don't depend on call order. */
function serve(responses: { summary?: unknown; rows?: unknown }) {
  get.mockImplementation(async (url: string) =>
    url.endsWith('/summary') ? (responses.summary ?? ok(summary())) : (responses.rows ?? ok(page([going, silent]))),
  )
}

describe('attendance store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    get.mockReset()
    put.mockReset()
    del.mockReset()
  })

  describe('fetchSummary', () => {
    it("loads the event's counts", async () => {
      const store = useAttendanceStore()
      serve({ summary: ok(summary(12, 3, 9)) })

      await store.fetchSummary('e1')

      expect(get).toHaveBeenCalledWith('/events/{eventId}/attendance/summary', { params: { path: { eventId: 'e1' } } })
      expect(store.summary).toEqual({ going: 12, notGoing: 3, noReply: 9, members: 24 })
    })

    it('forgets the previous event when another one is opened', async () => {
      const store = useAttendanceStore()
      serve({})
      await store.fetchSummary('e1')
      await store.fetchRows({ filter: 'ALL', page: 0 })

      await store.fetchSummary('e2')

      expect(store.rows).toEqual([])
    })

    it('keeps the numbers when the same event is looked at again', async () => {
      const store = useAttendanceStore()
      serve({})
      await store.fetchSummary('e1')

      get.mockReturnValue(new Promise(() => undefined))
      void store.fetchSummary('e1')

      expect(store.summary).not.toBeNull()
    })

    it('reports a failure', async () => {
      const store = useAttendanceStore()
      get.mockResolvedValue(failure(500))

      await store.fetchSummary('e1')

      expect(store.error?.status).toBe(500)
    })
  })

  describe('fetchRows', () => {
    it('requests the filter and page, fifty at a time, and keeps the paging info', async () => {
      const store = useAttendanceStore()
      serve({ rows: ok(page([going, silent], 1, 3)) })
      await store.fetchSummary('e1')
      get.mockClear()

      await store.fetchRows({ filter: 'NO_REPLY', page: 1 })

      expect(get).toHaveBeenCalledWith('/events/{eventId}/attendance', {
        params: { path: { eventId: 'e1' }, query: { filter: 'NO_REPLY', page: 1, size: 50 } },
      })
      expect(store.rows).toHaveLength(2)
      expect(store.filter).toBe('NO_REPLY')
      expect(store.page).toBe(1)
      expect(store.pageInfo?.totalPages).toBe(3)
      expect(store.loading).toBe(false)
    })

    it('ignores a response that arrives after a newer request', async () => {
      const store = useAttendanceStore()
      serve({})
      await store.fetchSummary('e1')
      const slow = deferred<unknown>()
      const fast = deferred<unknown>()
      get.mockReset()
      get.mockReturnValueOnce(slow.promise).mockReturnValueOnce(fast.promise)

      const first = store.fetchRows({ filter: 'GOING', page: 0 })
      const second = store.fetchRows({ filter: 'NOT_GOING', page: 0 })
      fast.resolve(ok(page([row('m3', 'Cy', { response: 'NOT_GOING' })])))
      await second
      slow.resolve(ok(page([going])))
      await first

      expect(store.rows.map((r) => r.member.name)).toEqual(['Cy'])
      expect(store.filter).toBe('NOT_GOING')
      expect(store.loading).toBe(false)
    })

    it('reports a failure and stops loading', async () => {
      const store = useAttendanceStore()
      await store.fetchSummary('e1')
      get.mockResolvedValue(failure(500))

      await store.fetchRows({ filter: 'ALL', page: 0 })

      expect(store.error?.status).toBe(500)
      expect(store.loading).toBe(false)
    })
  })

  describe('set', () => {
    async function loaded() {
      const store = useAttendanceStore()
      serve({ summary: ok(summary(1, 0, 1)) })
      await store.fetchSummary('e1')
      await store.fetchRows({ filter: 'ALL', page: 0 })
      get.mockClear()
      return store
    }

    it('updates the row before the server answers, then refreshes the summary and the rows', async () => {
      const store = await loaded()
      const pending = deferred<unknown>()
      put.mockReturnValue(pending.promise)
      serve({ summary: ok(summary(2, 0, 0)), rows: ok(page([going, { ...silent, response: 'GOING', source: 'ORGANIZER' }])) })

      const done = store.set('m2', 'GOING')

      const optimistic = store.rows.find((r) => r.member.id === 'm2')
      expect(optimistic?.response).toBe('GOING')
      expect(optimistic?.source).toBe('ORGANIZER')
      pending.resolve(ok({ ...silent, response: 'GOING', source: 'ORGANIZER', respondedAt: '2026-11-01T11:00:00Z' }))
      await done

      expect(put).toHaveBeenCalledWith('/events/{eventId}/attendance/{memberId}', {
        params: { path: { eventId: 'e1', memberId: 'm2' } },
        body: { response: 'GOING' },
      })
      expect(store.summary?.going).toBe(2)
      expect(get).toHaveBeenCalledWith('/events/{eventId}/attendance/summary', expect.anything())
    })

    it('puts the row back and reports the error when the change fails', async () => {
      const store = await loaded()
      put.mockResolvedValue(failure(409, 'attendance.closed'))

      await store.set('m2', 'NOT_GOING')

      const reverted = store.rows.find((r) => r.member.id === 'm2')
      expect(reverted?.response).toBeUndefined()
      expect(store.error?.problem.code).toBe('attendance.closed')
      expect(store.summary?.noReply).toBe(1)
    })
  })

  describe('clear', () => {
    async function loaded() {
      const store = useAttendanceStore()
      serve({ summary: ok(summary(1, 0, 1)) })
      await store.fetchSummary('e1')
      await store.fetchRows({ filter: 'ALL', page: 0 })
      get.mockClear()
      return store
    }

    it('removes the answer from the row at once and refreshes the summary', async () => {
      const store = await loaded()
      const pending = deferred<unknown>()
      del.mockReturnValue(pending.promise)
      serve({ summary: ok(summary(0, 0, 2)) })

      const done = store.clear('m1')

      const optimistic = store.rows.find((r) => r.member.id === 'm1')
      expect(optimistic?.response).toBeUndefined()
      expect(optimistic?.source).toBeUndefined()
      pending.resolve(noContent())
      await done

      expect(del).toHaveBeenCalledWith('/events/{eventId}/attendance/{memberId}', {
        params: { path: { eventId: 'e1', memberId: 'm1' } },
      })
      expect(store.summary?.noReply).toBe(2)
    })

    it('restores the answer when clearing fails', async () => {
      const store = await loaded()
      del.mockResolvedValue(failure(500))

      await store.clear('m1')

      expect(store.rows.find((r) => r.member.id === 'm1')?.response).toBe('GOING')
      expect(store.error?.status).toBe(500)
    })
  })
})
