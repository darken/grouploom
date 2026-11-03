import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { MiddlewareCallbackParams } from 'openapi-fetch'

import { createWake } from '../wake-middleware'

const HEALTH = 'http://x/api/system/info'

const fetchMock = vi.fn<(input: Request | string, init?: RequestInit) => Promise<Response>>()
let wake: ReturnType<typeof createWake>

/** A GET by default; passing a body makes it a POST. */
function params(id: string, schemaPath = '/events', body?: string) {
  const url = 'http://x/api' + schemaPath
  const request = body === undefined ? new Request(url) : new Request(url, { method: 'POST', body })
  return { request, schemaPath, id, params: {}, options: { fetch: fetchMock } } as unknown as MiddlewareCallbackParams
}

const healthCalls = () => fetchMock.mock.calls.filter(([input]) => String(input) === HEALTH).length
const ok = () => new Response('{}', { status: 200 })
const gateway = () => new Response(null, { status: 502 })

/** Health answers fail for the first `failures` checks, then succeed; other calls answer `other`. */
function healthAfter(failures: number, other: () => Response = ok) {
  let checks = 0
  fetchMock.mockImplementation(async (input) => {
    if (String(input) !== HEALTH) return other()
    checks++
    if (checks <= failures) throw new TypeError('Failed to fetch')
    return ok()
  })
}

describe('wake middleware', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    fetchMock.mockReset()
    wake = createWake({ pollMs: 2000, giveUpMs: 10_000, showAfterMs: 1000 })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('waits for the server to answer before sending the first request', async () => {
    healthAfter(2)
    let sent = false
    const p = params('1')
    const request = Promise.resolve(wake.middleware.onRequest!(p)).then(() => (sent = true))

    await vi.advanceTimersByTimeAsync(3000)
    expect(sent).toBe(false)
    expect(wake.status.value).toBe('waking')

    await vi.advanceTimersByTimeAsync(2000)
    await request
    expect(sent).toBe(true)
    expect(healthCalls()).toBe(3)
    expect(wake.status.value).toBe('awake')
  })

  it('does not show waking when the server answers quickly', async () => {
    healthAfter(0)

    await wake.middleware.onRequest!(params('1'))

    expect(wake.status.value).toBe('awake')
    expect(healthCalls()).toBe(1)
  })

  it('skips the check when the server answered recently', async () => {
    healthAfter(0)
    const first = params('1')
    await wake.middleware.onRequest!(first)
    await wake.middleware.onResponse!({ ...first, response: ok() })

    await vi.advanceTimersByTimeAsync(60_000)
    await wake.middleware.onRequest!(params('2'))

    expect(healthCalls()).toBe(1)
  })

  it('checks again after the server has been idle long enough to fall asleep', async () => {
    healthAfter(0)
    const first = params('1')
    await wake.middleware.onRequest!(first)
    await wake.middleware.onResponse!({ ...first, response: ok() })

    await vi.advanceTimersByTimeAsync(11 * 60_000)
    await wake.middleware.onRequest!(params('2'))

    expect(healthCalls()).toBe(2)
  })

  it('shares one check between concurrent requests', async () => {
    healthAfter(1)

    const both = Promise.all([wake.middleware.onRequest!(params('1')), wake.middleware.onRequest!(params('2'))])
    await vi.advanceTimersByTimeAsync(2000)
    await both

    expect(healthCalls()).toBe(2)
  })

  it('retries a GET that hit a sleeping server once it is awake', async () => {
    healthAfter(0)
    const p = params('1')
    await wake.middleware.onRequest!(p)
    healthAfter(1, () => new Response('events', { status: 200 }))

    const result = wake.middleware.onResponse!({ ...p, response: gateway() })
    await vi.advanceTimersByTimeAsync(2000)
    const response = (await result) as Response

    expect(response.status).toBe(200)
    expect(await response.text()).toBe('events')
  })

  it('retries a GET that failed with a network error once the server is awake', async () => {
    healthAfter(0)
    const p = params('1')
    await wake.middleware.onRequest!(p)
    healthAfter(1, () => new Response('events', { status: 200 }))

    const result = wake.middleware.onError!({ ...p, error: new TypeError('Failed to fetch') })
    await vi.advanceTimersByTimeAsync(2000)
    const response = (await result) as Response

    expect(response.status).toBe(200)
  })

  it('does not retry a POST that hit a sleeping server', async () => {
    healthAfter(0)
    const p = params('1', '/members', '{"a":1}')
    await wake.middleware.onRequest!(p)
    fetchMock.mockClear()
    healthAfter(1)

    const result = await wake.middleware.onResponse!({ ...p, response: gateway() })

    expect(result).toBeUndefined()
    expect(fetchMock.mock.calls.filter(([input]) => String(input) !== HEALTH)).toHaveLength(0)
    await vi.advanceTimersByTimeAsync(2000)
    expect(wake.status.value).toBe('awake')
  })

  it('leaves other errors alone', async () => {
    healthAfter(0)
    const p = params('1')
    await wake.middleware.onRequest!(p)
    fetchMock.mockClear()

    const result = await wake.middleware.onResponse!({ ...p, response: new Response(null, { status: 500 }) })

    expect(result).toBeUndefined()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('reports unreachable after giving up, and resumes the waiting request on retry', async () => {
    healthAfter(Infinity)
    let sent = false
    const request = Promise.resolve(wake.middleware.onRequest!(params('1'))).then(() => (sent = true))

    await vi.advanceTimersByTimeAsync(12_000)
    expect(wake.status.value).toBe('unreachable')
    expect(sent).toBe(false)

    healthAfter(0)
    wake.retry()
    await vi.advanceTimersByTimeAsync(0)
    await request

    expect(sent).toBe(true)
    expect(wake.status.value).toBe('awake')
  })
})
