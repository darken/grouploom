import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { api } from '@/api/client'
import { ApiError } from '@/api/errors'
import { useSystemStore } from '../system'

vi.mock('@/api/client', () => ({ api: { GET: vi.fn<() => Promise<unknown>>() } }))

const get = vi.mocked(api.GET) as unknown as ReturnType<typeof vi.fn>

describe('system store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    get.mockReset()
  })

  it('load stores the info on success', async () => {
    get.mockResolvedValue({ data: { name: 'backend', version: '1.0.0' }, response: new Response() })
    const store = useSystemStore()

    await store.load()

    expect(store.info).toEqual({ name: 'backend', version: '1.0.0' })
    expect(store.error).toBeNull()
    expect(store.loading).toBe(false)
  })

  it('load exposes an ApiError when the API returns a problem', async () => {
    get.mockResolvedValue({
      error: { status: 500, title: 'Internal Server Error' },
      response: new Response(null, { status: 500 }),
    })
    const store = useSystemStore()

    await store.load()

    expect(store.info).toBeNull()
    expect(store.error).toBeInstanceOf(ApiError)
    expect(store.error?.status).toBe(500)
  })

  it('load exposes an ApiError when the network fails', async () => {
    get.mockRejectedValue(new TypeError('Failed to fetch'))
    const store = useSystemStore()

    await store.load()

    expect(store.error).toBeInstanceOf(ApiError)
  })
})
