import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { api } from '@/api/client'
import { useEventNotificationsStore, sortNotifications, type EventNotification } from '../eventNotifications'

vi.mock('@/api/client', () => ({
  api: {
    GET: vi.fn<() => Promise<unknown>>(),
    POST: vi.fn<() => Promise<unknown>>(),
    DELETE: vi.fn<() => Promise<unknown>>(),
  },
}))

const get = vi.mocked(api.GET) as unknown as ReturnType<typeof vi.fn>
const post = vi.mocked(api.POST) as unknown as ReturnType<typeof vi.fn>
const del = vi.mocked(api.DELETE) as unknown as ReturnType<typeof vi.fn>

function notification(id: string, overrides: Partial<EventNotification> = {}): EventNotification {
  return {
    id,
    kind: 'INVITATION',
    status: 'SCHEDULED',
    sendAt: '2026-11-01T09:00:00Z',
    localSendAt: '2026-11-01T10:00:00',
    timeZone: 'Europe/Madrid',
    delivery: { pending: 0, sent: 0, failed: 0 },
    createdAt: '2026-10-01T00:00:00Z',
    ...overrides,
  }
}

const ok = (data: unknown) => ({ data, response: new Response() })
const problem = (status: number, extra: object = {}) => ({
  error: { status, title: 'Err', ...extra },
  response: new Response(null, { status }),
})

describe('sortNotifications', () => {
  it('puts scheduled ones first by send time, then sent and cancelled ones newest first', () => {
    const items = [
      notification('old-sent', { status: 'SENT', sentAt: '2026-10-02T10:00:00Z' }),
      notification('late', { sendAt: '2026-11-02T09:00:00Z' }),
      notification('cancelled', { status: 'CANCELLED', sendAt: '2026-10-20T09:00:00Z' }),
      notification('early', { sendAt: '2026-11-01T09:00:00Z' }),
      notification('new-sent', { status: 'SENT', sentAt: '2026-10-10T10:00:00Z' }),
    ]

    expect(sortNotifications(items).map((n) => n.id)).toEqual(['early', 'late', 'cancelled', 'new-sent', 'old-sent'])
  })
})

describe('eventNotifications store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    for (const m of [get, post, del]) m.mockReset()
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('fetch loads the event\'s notifications in display order', async () => {
    get.mockResolvedValue(ok([notification('b', { sendAt: '2026-11-02T09:00:00Z' }), notification('a')]))
    const store = useEventNotificationsStore()

    await store.fetch('e1')

    expect(get).toHaveBeenCalledWith('/events/{eventId}/notifications', { params: { path: { eventId: 'e1' } } })
    expect(store.items.map((n) => n.id)).toEqual(['a', 'b'])
    expect(store.eventId).toBe('e1')
    expect(store.loading).toBe(false)
    expect(store.loaded).toBe(true)
  })

  it('fetch keeps the error and clears items of a previous event', async () => {
    get.mockResolvedValueOnce(ok([notification('a')]))
    const store = useEventNotificationsStore()
    await store.fetch('e1')

    get.mockResolvedValueOnce(problem(500))
    await store.fetch('e2')

    expect(store.items).toEqual([])
    expect(store.error?.status).toBe(500)
  })

  it('fetch of the same event keeps showing the list while it reloads', async () => {
    get.mockResolvedValueOnce(ok([notification('a')]))
    const store = useEventNotificationsStore()
    await store.fetch('e1')

    get.mockResolvedValueOnce(problem(500))
    await store.fetch('e1')

    expect(store.items).toHaveLength(1)
    expect(store.error).not.toBeNull()
  })

  it('fetchAudience loads the eligible and total counts', async () => {
    get.mockResolvedValue(ok({ eligible: 18, total: 24 }))
    const store = useEventNotificationsStore()

    await store.fetchAudience('e1', 'INVITATION')

    expect(get).toHaveBeenCalledWith('/events/{eventId}/notifications/audience', {
      params: { path: { eventId: 'e1' }, query: { kind: 'INVITATION' } },
    })
    expect(store.audience).toEqual({ eligible: 18, total: 24 })
  })

  it('fetchAudience asks for the reminder audience, which leaves out members who cannot go', async () => {
    get.mockResolvedValue(ok({ eligible: 15, total: 24 }))
    const store = useEventNotificationsStore()

    await store.fetchAudience('e1', 'REMINDER')

    expect(get).toHaveBeenCalledWith('/events/{eventId}/notifications/audience', {
      params: { path: { eventId: 'e1' }, query: { kind: 'REMINDER' } },
    })
    expect(store.audience).toEqual({ eligible: 15, total: 24 })
  })

  it('fetchAudience ignores an answer for a kind that is no longer selected', async () => {
    let finishInvitation!: (value: unknown) => void
    get.mockReturnValueOnce(new Promise((r) => (finishInvitation = r)))
    get.mockResolvedValueOnce(ok({ eligible: 15, total: 24 }))
    const store = useEventNotificationsStore()

    const first = store.fetchAudience('e1', 'INVITATION')
    await store.fetchAudience('e1', 'REMINDER')
    finishInvitation(ok({ eligible: 18, total: 24 }))
    await first

    expect(store.audience).toEqual({ eligible: 15, total: 24 })
  })

  it('schedule posts the input, inserts the result and keeps the list sorted', async () => {
    get.mockResolvedValue(ok([notification('late', { sendAt: '2026-11-02T09:00:00Z' })]))
    const store = useEventNotificationsStore()
    await store.fetch('e1')
    post.mockResolvedValue(ok(notification('early', { kind: 'REMINDER', sendAt: '2026-11-01T09:00:00Z' })))

    const result = await store.schedule({ kind: 'REMINDER', timing: 'BEFORE_START', offsetMinutes: 1440 })

    expect(post).toHaveBeenCalledWith('/events/{eventId}/notifications', {
      params: { path: { eventId: 'e1' } },
      body: { kind: 'REMINDER', timing: 'BEFORE_START', offsetMinutes: 1440 },
    })
    expect(result).toEqual({ status: 'ok', notification: expect.objectContaining({ id: 'early' }) })
    expect(store.items.map((n) => n.id)).toEqual(['early', 'late'])
    expect(store.saving).toBe(false)
  })

  it('schedule with NOW refetches once after 10 seconds to pick up delivery counts', async () => {
    get.mockResolvedValue(ok([]))
    const store = useEventNotificationsStore()
    await store.fetch('e1')
    post.mockResolvedValue(ok(notification('n1', { sendAt: '2026-10-01T00:00:00Z' })))
    const delivered = vi.fn<(n: EventNotification | undefined) => void>()

    await store.schedule({ kind: 'INVITATION', timing: 'NOW' }, delivered)
    expect(get).toHaveBeenCalledTimes(1)
    get.mockResolvedValue(ok([notification('n1', { status: 'SENT', recipientCount: 18, delivery: { pending: 0, sent: 18, failed: 0 } })]))

    await vi.advanceTimersByTimeAsync(9_999)
    expect(get).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(1)
    expect(get).toHaveBeenCalledTimes(2)
    expect(delivered).toHaveBeenCalledWith(expect.objectContaining({ id: 'n1', recipientCount: 18 }))

    await vi.advanceTimersByTimeAsync(60_000)
    expect(get).toHaveBeenCalledTimes(2)
  })

  it('schedule with a later timing does not refetch', async () => {
    get.mockResolvedValue(ok([]))
    const store = useEventNotificationsStore()
    await store.fetch('e1')
    post.mockResolvedValue(ok(notification('n1')))

    await store.schedule({ kind: 'REMINDER', timing: 'BEFORE_START', offsetMinutes: 60 })
    await vi.advanceTimersByTimeAsync(30_000)

    expect(get).toHaveBeenCalledTimes(1)
  })

  it.each([
    ['notification.limit', 'limit'],
    ['event.cancelled', 'cancelled'],
    ['event.started', 'started'],
  ])('schedule maps the 409 %s to the typed result %s', async (code, status) => {
    get.mockResolvedValue(ok([]))
    const store = useEventNotificationsStore()
    await store.fetch('e1')
    post.mockResolvedValue(problem(409, { code }))

    expect(await store.schedule({ kind: 'INVITATION', timing: 'NOW' })).toEqual({ status })
    expect(store.items).toEqual([])
  })

  it('schedule maps 400 field errors for the dialog', async () => {
    get.mockResolvedValue(ok([]))
    const store = useEventNotificationsStore()
    await store.fetch('e1')
    post.mockResolvedValue(
      problem(400, { code: 'notification.send-at-invalid', errors: [{ field: 'localSendAt', message: 'Too late' }] }),
    )

    expect(await store.schedule({ kind: 'REMINDER', timing: 'AT', localSendAt: '2026-11-01T10:00:00' })).toEqual({
      status: 'invalid',
    })
    expect(store.fieldErrors).toEqual({ localSendAt: 'Too late' })
  })

  it('schedule keeps other failures as an error', async () => {
    get.mockResolvedValue(ok([]))
    const store = useEventNotificationsStore()
    await store.fetch('e1')
    post.mockResolvedValue(problem(500))

    expect(await store.schedule({ kind: 'INVITATION', timing: 'NOW' })).toEqual({ status: 'error' })
    expect(store.saveError?.status).toBe(500)
  })

  it('unschedule removes the item', async () => {
    get.mockResolvedValue(ok([notification('a'), notification('b', { sendAt: '2026-11-02T09:00:00Z' })]))
    const store = useEventNotificationsStore()
    await store.fetch('e1')
    del.mockResolvedValue({ response: new Response(null, { status: 204 }) })

    expect(await store.unschedule('a')).toEqual({ status: 'ok' })

    expect(del).toHaveBeenCalledWith('/events/{eventId}/notifications/{notificationId}', {
      params: { path: { eventId: 'e1', notificationId: 'a' } },
    })
    expect(store.items.map((n) => n.id)).toEqual(['b'])
  })

  it('unschedule of an already sent notification returns already-sent and reloads the list', async () => {
    get.mockResolvedValueOnce(ok([notification('a')]))
    const store = useEventNotificationsStore()
    await store.fetch('e1')
    del.mockResolvedValue(problem(409, { code: 'notification.already-sent' }))
    get.mockResolvedValueOnce(ok([notification('a', { status: 'SENT', recipientCount: 3 })]))

    expect(await store.unschedule('a')).toEqual({ status: 'already-sent' })

    expect(store.items[0]?.status).toBe('SENT')
  })

  it('refetches when the window regains focus while started, and stops after stop()', async () => {
    get.mockResolvedValue(ok([]))
    const store = useEventNotificationsStore()
    await store.fetch('e1')
    store.start('e1')

    window.dispatchEvent(new Event('focus'))
    await vi.advanceTimersByTimeAsync(0)
    expect(get).toHaveBeenCalledTimes(2)

    store.stop()
    window.dispatchEvent(new Event('focus'))
    await vi.advanceTimersByTimeAsync(0)
    expect(get).toHaveBeenCalledTimes(2)
  })

  it('stop cancels a pending delayed refetch', async () => {
    get.mockResolvedValue(ok([]))
    const store = useEventNotificationsStore()
    await store.fetch('e1')
    post.mockResolvedValue(ok(notification('n1')))
    await store.schedule({ kind: 'INVITATION', timing: 'NOW' })

    store.stop()
    await vi.advanceTimersByTimeAsync(20_000)

    expect(get).toHaveBeenCalledTimes(1)
  })
})
