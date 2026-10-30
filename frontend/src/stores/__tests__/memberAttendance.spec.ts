import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { api } from '@/api/client'
import { useMemberAttendanceStore } from '../memberAttendance'

vi.mock('@/api/client', () => ({
  api: { GET: vi.fn<() => Promise<unknown>>(), PUT: vi.fn<() => Promise<unknown>>() },
}))

const get = vi.mocked(api.GET) as unknown as ReturnType<typeof vi.fn>
const put = vi.mocked(api.PUT) as unknown as ReturnType<typeof vi.fn>

const STORAGE_KEY = 'gl_member_token_attendance'

function reply(overrides: object = {}) {
  return {
    groupName: 'Chess Club',
    memberName: 'Ana',
    event: {
      title: 'Practice',
      start: '2026-11-07T09:00:00Z',
      end: '2026-11-07T11:00:00Z',
      localStart: '2026-11-07T10:00:00',
      localEnd: '2026-11-07T12:00:00',
      timeZone: 'Europe/Madrid',
      typeName: 'Practice',
      typeColor: 'green',
      cancelled: false,
    },
    open: true,
    ...overrides,
  }
}

const ok = (data: object) => ({ data, response: new Response() })
const problem = (status: number, code?: string) => ({
  error: { status, title: 'Err', ...(code ? { code } : {}) },
  response: new Response(null, { status }),
})

describe('memberAttendance store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    get.mockReset()
    put.mockReset()
    sessionStorage.clear()
    window.history.replaceState(null, '', '/m/attend')
  })

  afterEach(() => vi.restoreAllMocks())

  describe('init', () => {
    it('reads the token and preselects the choice from the link, and keeps the token under its own key', () => {
      window.history.replaceState(null, '', '/m/attend#t=abc&r=going')
      const store = useMemberAttendanceStore()

      store.init(window.location.hash)

      expect(store.token).toBe('abc')
      expect(store.choice).toBe('GOING')
      expect(sessionStorage.getItem(STORAGE_KEY)).toBe('abc')
      expect(window.location.hash).toBe('')
    })

    it('maps r=not-going to NOT_GOING and ignores unknown values', () => {
      const store = useMemberAttendanceStore()

      store.init('#t=abc&r=not-going')
      expect(store.choice).toBe('NOT_GOING')

      store.init('#t=abc&r=maybe')
      expect(store.choice).toBeNull()
    })

    it('does not touch the preferences token', () => {
      sessionStorage.setItem('gl_member_token', 'prefs')
      const store = useMemberAttendanceStore()

      store.init('#t=abc')

      expect(sessionStorage.getItem('gl_member_token')).toBe('prefs')
    })

    it('is invalid without any token', () => {
      const store = useMemberAttendanceStore()

      store.init('')

      expect(store.status).toBe('invalid')
    })
  })

  describe('load', () => {
    it('fetches with the token in a header, never in the URL, and does not send an answer', async () => {
      const store = useMemberAttendanceStore()
      store.init('#t=abc&r=going')
      get.mockResolvedValue(ok(reply()))

      await store.load()

      expect(get).toHaveBeenCalledWith('/public/attendance', { params: { header: { 'X-Member-Token': 'abc' } } })
      expect(put).not.toHaveBeenCalled()
      expect(store.status).toBe('ready')
      expect(store.data?.memberName).toBe('Ana')
    })

    it('keeps the choice from the link over the existing answer', async () => {
      const store = useMemberAttendanceStore()
      store.init('#t=abc&r=going')
      get.mockResolvedValue(ok(reply({ response: 'NOT_GOING', note: 'Away' })))

      await store.load()

      expect(store.choice).toBe('GOING')
    })

    it('takes the choice and note from the existing answer when the link has none', async () => {
      const store = useMemberAttendanceStore()
      store.init('#t=abc')
      get.mockResolvedValue(ok(reply({ response: 'NOT_GOING', note: 'Away' })))

      await store.load()

      expect(store.choice).toBe('NOT_GOING')
      expect(store.note).toBe('Away')
    })

    it('has no choice when there is neither a link choice nor an answer', async () => {
      const store = useMemberAttendanceStore()
      store.init('#t=abc')
      get.mockResolvedValue(ok(reply()))

      await store.load()

      expect(store.choice).toBeNull()
    })

    it('is invalid for a 401 invalid and expired for a 401 expired', async () => {
      const store = useMemberAttendanceStore()
      store.init('#t=abc')

      get.mockResolvedValue(problem(401, 'member-token.invalid'))
      await store.load()
      expect(store.status).toBe('invalid')

      get.mockResolvedValue(problem(401, 'member-token.expired'))
      await store.load()
      expect(store.status).toBe('expired')
    })

    it('is invalid without a token and sends nothing', async () => {
      const store = useMemberAttendanceStore()
      store.init('')

      await store.load()

      expect(store.status).toBe('invalid')
      expect(get).not.toHaveBeenCalled()
    })

    it('reports a failure as an error status', async () => {
      const store = useMemberAttendanceStore()
      store.init('#t=abc')
      get.mockResolvedValue(problem(500))

      await store.load()

      expect(store.status).toBe('error')
      expect(store.error?.status).toBe(500)
    })

    it('reports a network failure as an error status', async () => {
      const store = useMemberAttendanceStore()
      store.init('#t=abc')
      get.mockRejectedValue(new TypeError('Failed to fetch'))

      await store.load()

      expect(store.status).toBe('error')
      expect(store.error?.problem.title).toBe('Network error')
    })
  })

  describe('submit', () => {
    async function readyStore(hash = '#t=abc&r=going') {
      const store = useMemberAttendanceStore()
      store.init(hash)
      get.mockResolvedValue(ok(reply()))
      await store.load()
      return store
    }

    it('sends the choice and note, then marks the reply as saved', async () => {
      const store = await readyStore()
      store.note = 'Bringing snacks'
      put.mockResolvedValue(ok(reply({ response: 'GOING', note: 'Bringing snacks', respondedAt: '2026-11-01T10:00:00Z' })))

      await store.submit()

      expect(put).toHaveBeenCalledWith('/public/attendance', {
        params: { header: { 'X-Member-Token': 'abc' } },
        body: { response: 'GOING', note: 'Bringing snacks' },
      })
      expect(store.saved).toBe(true)
      expect(store.saving).toBe(false)
      expect(store.data?.response).toBe('GOING')
    })

    it('leaves the note out when it is blank', async () => {
      const store = await readyStore()
      store.note = '   '
      put.mockResolvedValue(ok(reply({ response: 'GOING' })))

      await store.submit()

      expect(put.mock.calls[0]![1].body).toEqual({ response: 'GOING' })
    })

    it('does nothing without a choice', async () => {
      const store = await readyStore('#t=abc')

      await store.submit()

      expect(put).not.toHaveBeenCalled()
      expect(store.saved).toBe(false)
    })

    it('shows the form again after edit()', async () => {
      const store = await readyStore()
      put.mockResolvedValue(ok(reply({ response: 'GOING' })))
      await store.submit()

      store.edit()

      expect(store.saved).toBe(false)
      expect(store.choice).toBe('GOING')
    })

    it('refetches on a closed conflict, which shows the closed state', async () => {
      const store = await readyStore()
      put.mockResolvedValue(problem(409, 'attendance.closed'))
      get.mockResolvedValue(ok(reply({ open: false, response: 'NOT_GOING' })))

      await store.submit()

      expect(get).toHaveBeenCalledTimes(2)
      expect(store.data?.open).toBe(false)
      expect(store.saved).toBe(false)
      expect(store.status).toBe('ready')
      expect(store.error).toBeNull()
    })

    it('keeps the form and reports the error when saving fails', async () => {
      const store = await readyStore()
      put.mockResolvedValue(problem(500))

      await store.submit()

      expect(store.saved).toBe(false)
      expect(store.saving).toBe(false)
      expect(store.status).toBe('ready')
      expect(store.error?.status).toBe(500)
      expect(store.choice).toBe('GOING')
    })

    it('clears an earlier error when the next try succeeds', async () => {
      const store = await readyStore()
      put.mockResolvedValueOnce(problem(500))
      await store.submit()
      put.mockResolvedValueOnce(ok(reply({ response: 'GOING' })))

      await store.submit()

      expect(store.error).toBeNull()
      expect(store.saved).toBe(true)
    })

    it('moves to expired when the token expires while the page is open', async () => {
      const store = await readyStore()
      put.mockResolvedValue(problem(401, 'member-token.expired'))

      await store.submit()

      expect(store.status).toBe('expired')
    })

    it('ignores a second tap while a request is in flight', async () => {
      const store = await readyStore()
      let finish!: (value: unknown) => void
      put.mockReturnValue(new Promise((r) => (finish = r)))

      const first = store.submit()
      const second = store.submit()
      finish(ok(reply({ response: 'GOING' })))
      await Promise.all([first, second])

      expect(put).toHaveBeenCalledTimes(1)
    })
  })
})
