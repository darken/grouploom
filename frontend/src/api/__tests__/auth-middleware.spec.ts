import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { MiddlewareCallbackParams } from 'openapi-fetch'

import { authMiddleware, configureAuth } from '../auth-middleware'

const fetchMock = vi.fn<(request: Request) => Promise<Response>>()
const refresh = vi.fn<() => Promise<string | null>>()
const onSessionExpired = vi.fn<() => void>()
let token: string | null

function params(id: string, schemaPath: string, request = new Request('http://x/api' + schemaPath, { method: 'POST', body: '{"a":1}' })) {
  return { request, schemaPath, id, params: {}, options: { fetch: fetchMock } } as unknown as MiddlewareCallbackParams
}

async function send(id: string, schemaPath: string, response: Response) {
  const p = params(id, schemaPath)
  const request = (await authMiddleware.onRequest!(p)) as Request
  return authMiddleware.onResponse!({ ...p, request, response })
}

describe('authMiddleware', () => {
  beforeEach(() => {
    token = 'old'
    fetchMock.mockReset()
    refresh.mockReset()
    onSessionExpired.mockReset()
    configureAuth({ getToken: () => token, refresh, onSessionExpired })
  })

  it('attaches the bearer token to API requests', async () => {
    const request = (await authMiddleware.onRequest!(params('a', '/me'))) as Request

    expect(request.headers.get('Authorization')).toBe('Bearer old')
  })

  it('does not attach the token to /auth/ requests', async () => {
    const request = (await authMiddleware.onRequest!(params('a', '/auth/login'))) as Request | undefined

    expect(request?.headers.get('Authorization') ?? null).toBeNull()
  })

  it('does not attach the token to /public/ requests, even when signed in', async () => {
    const request = (await authMiddleware.onRequest!(params('a', '/public/preferences'))) as Request | undefined

    expect(request?.headers.get('Authorization') ?? null).toBeNull()
  })

  it('does not refresh, retry or end the session when a /public/ request returns 401', async () => {
    const p = params('1', '/public/preferences')
    await authMiddleware.onRequest!(p)
    const result = await authMiddleware.onResponse!({ ...p, response: new Response(null, { status: 401 }) })

    expect(result).toBeUndefined()
    expect(refresh).not.toHaveBeenCalled()
    expect(fetchMock).not.toHaveBeenCalled()
    expect(onSessionExpired).not.toHaveBeenCalled()
  })

  it('refreshes once and retries the request with the new token on 401', async () => {
    refresh.mockResolvedValue('new')
    fetchMock.mockResolvedValue(new Response('ok', { status: 200 }))

    const result = await send('1', '/me', new Response(null, { status: 401 }))

    expect(refresh).toHaveBeenCalledOnce()
    const retried = fetchMock.mock.calls[0]![0]
    expect(retried.headers.get('Authorization')).toBe('Bearer new')
    expect(await retried.text()).toBe('{"a":1}')
    expect(result?.status).toBe(200)
    expect(onSessionExpired).not.toHaveBeenCalled()
  })

  it('shares one refresh between concurrent 401s', async () => {
    let resolve!: (value: string) => void
    refresh.mockReturnValue(new Promise<string>((r) => (resolve = r)))
    fetchMock.mockResolvedValue(new Response('ok'))

    const first = send('1', '/me', new Response(null, { status: 401 }))
    const second = send('2', '/groups', new Response(null, { status: 401 }))
    await Promise.resolve()
    resolve('new')
    await Promise.all([first, second])

    expect(refresh).toHaveBeenCalledOnce()
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('calls onSessionExpired when the refresh fails', async () => {
    refresh.mockResolvedValue(null)

    const result = await send('1', '/me', new Response(null, { status: 401 }))

    expect(result).toBeUndefined()
    expect(onSessionExpired).toHaveBeenCalledOnce()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('does not try to refresh when an /auth/ request returns 401', async () => {
    const p = params('1', '/auth/login')
    await authMiddleware.onRequest!(p)
    await authMiddleware.onResponse!({ ...p, response: new Response(null, { status: 401 }) })

    expect(refresh).not.toHaveBeenCalled()
    expect(onSessionExpired).not.toHaveBeenCalled()
  })

  it('leaves non-401 responses alone', async () => {
    const result = await send('1', '/me', new Response('x', { status: 500 }))

    expect(result).toBeUndefined()
    expect(refresh).not.toHaveBeenCalled()
  })
})
