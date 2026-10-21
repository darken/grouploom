import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { api } from '@/api/client'
import { ApiError } from '@/api/errors'
import { useAuthStore } from '../auth'

vi.mock('@/api/client', () => ({
  api: { GET: vi.fn<() => Promise<unknown>>(), POST: vi.fn<() => Promise<unknown>>() },
}))

const post = vi.mocked(api.POST) as unknown as ReturnType<typeof vi.fn>

const authResponse = {
  accessToken: 'jwt',
  expiresIn: 900,
  user: {
    id: 'u1',
    email: 'ada@example.com',
    displayName: 'Ada',
    role: 'OWNER',
    group: { id: 'g1', name: 'Chess Club' },
  },
}

function problem(status: number, extra: object = {}) {
  return { error: { status, title: 'Err', ...extra }, response: new Response(null, { status }) }
}

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    post.mockReset()
  })

  it('login sets the user, token and status on success', async () => {
    post.mockResolvedValue({ data: authResponse, response: new Response() })
    const store = useAuthStore()

    const ok = await store.login({ email: 'ada@example.com', password: 'pw' })

    expect(ok).toBe(true)
    expect(store.user?.displayName).toBe('Ada')
    expect(store.accessToken).toBe('jwt')
    expect(store.status).toBe('authenticated')
    expect(store.isAuthenticated).toBe(true)
  })

  it('login exposes an ApiError on failure', async () => {
    post.mockResolvedValue(problem(401, { detail: 'Email or password is incorrect.' }))
    const store = useAuthStore()

    const ok = await store.login({ email: 'a@b.c', password: 'x' })

    expect(ok).toBe(false)
    expect(store.error).toBeInstanceOf(ApiError)
    expect(store.error?.status).toBe(401)
    expect(store.isAuthenticated).toBe(false)
    expect(store.loading).toBe(false)
  })

  it('signUp sets the user on success', async () => {
    post.mockResolvedValue({ data: authResponse, response: new Response() })
    const store = useAuthStore()

    const ok = await store.signUp({ groupName: 'Chess Club', displayName: 'Ada', email: 'ada@example.com', password: 'x'.repeat(12) })

    expect(ok).toBe(true)
    expect(store.user?.group.name).toBe('Chess Club')
  })

  it('logout clears the state even if the request fails', async () => {
    post.mockResolvedValueOnce({ data: authResponse, response: new Response() })
    const store = useAuthStore()
    await store.login({ email: 'a', password: 'b' })
    post.mockRejectedValueOnce(new TypeError('Failed to fetch'))

    await store.logout()

    expect(store.user).toBeNull()
    expect(store.accessToken).toBeNull()
    expect(store.status).toBe('anonymous')
  })

  it('restore becomes authenticated when the refresh cookie is valid', async () => {
    post.mockResolvedValue({ data: authResponse, response: new Response() })
    const store = useAuthStore()

    await store.restore()

    expect(store.status).toBe('authenticated')
    expect(post).toHaveBeenCalledWith('/auth/refresh')
  })

  it('restore becomes anonymous on 401 and only calls the API once', async () => {
    post.mockResolvedValue(problem(401))
    const store = useAuthStore()

    await Promise.all([store.restore(), store.restore()])
    await store.restore()

    expect(store.status).toBe('anonymous')
    expect(post).toHaveBeenCalledTimes(1)
  })
})
