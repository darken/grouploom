import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'

import { api } from '@/api/client'
import { createAppRouter, safeRedirect } from '..'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/api/client', () => ({
  api: { GET: vi.fn<() => Promise<unknown>>(), POST: vi.fn<() => Promise<unknown>>() },
}))

const post = vi.mocked(api.POST) as unknown as ReturnType<typeof vi.fn>

const me = {
  accessToken: 'jwt',
  expiresIn: 900,
  user: { id: 'u', email: 'a@b.c', displayName: 'Ada', role: 'OWNER', group: { id: 'g', name: 'Club' } },
}

describe('router guard', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    post.mockReset()
  })

  it('redirects a guest from a protected page to /login with the redirect', async () => {
    post.mockResolvedValue({ error: { status: 401 }, response: new Response(null, { status: 401 }) })
    const router = createAppRouter(createMemoryHistory())

    await router.push('/')

    expect(router.currentRoute.value.fullPath).toBe('/login')
    await router.push('/nope')
    expect(router.currentRoute.value.name).toBe('not-found')
  })

  it('keeps the requested path in the redirect query', async () => {
    post.mockResolvedValue({ error: { status: 401 }, response: new Response(null, { status: 401 }) })
    const router = createAppRouter(createMemoryHistory())
    router.addRoute({ path: '/secret', component: { template: '<i/>' }, meta: { requiresAuth: true } })

    await router.push('/secret?x=1')

    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/secret?x=1')
  })

  it('sends a signed-in user away from /login', async () => {
    post.mockResolvedValue({ data: me, response: new Response() })
    const router = createAppRouter(createMemoryHistory())

    await router.push('/login')

    expect(router.currentRoute.value.path).toBe('/')
  })

  it('sends a signed-in user to the internal redirect target', async () => {
    post.mockResolvedValue({ data: me, response: new Response() })
    useAuthStore()
    const router = createAppRouter(createMemoryHistory())

    await router.push('/login?redirect=/nope')

    expect(router.currentRoute.value.path).toBe('/nope')
    expect(useAuthStore().isAuthenticated).toBe(true)
  })
})

describe('router guard for public routes', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    post.mockReset()
  })

  it('opens the member preferences page when signed out, without restoring a session', async () => {
    const router = createAppRouter(createMemoryHistory())

    await router.push('/m/preferences')

    expect(router.currentRoute.value.name).toBe('member-preferences')
    expect(router.currentRoute.value.meta).toMatchObject({ public: true, layout: 'member', title: 'Email preferences' })
    expect(post).not.toHaveBeenCalled()
    expect(useAuthStore().status).toBe('unknown')
  })

  it('opens the member reply page when signed out, keeping the link fragment', async () => {
    const router = createAppRouter(createMemoryHistory())

    await router.push('/m/attend#t=abc&r=going')

    expect(router.currentRoute.value.name).toBe('member-attendance')
    expect(router.currentRoute.value.hash).toBe('#t=abc&r=going')
    expect(router.currentRoute.value.meta).toMatchObject({ public: true, layout: 'member', title: 'Reply' })
    expect(post).not.toHaveBeenCalled()
    expect(useAuthStore().status).toBe('unknown')
  })

  it('does not use the session of a signed-in organizer on a public route', async () => {
    post.mockResolvedValue({ data: me, response: new Response() })
    await useAuthStore().restore()
    post.mockClear()
    const router = createAppRouter(createMemoryHistory())

    await router.push('/m/preferences')

    expect(router.currentRoute.value.path).toBe('/m/preferences')
    expect(post).not.toHaveBeenCalled()
  })

  it('keeps the fragment of the member link when navigating to the page', async () => {
    const router = createAppRouter(createMemoryHistory())

    await router.push('/m/preferences#t=abc')

    expect(router.currentRoute.value.hash).toBe('#t=abc')
  })

  it('sets the document title', async () => {
    const router = createAppRouter(createMemoryHistory())

    await router.push('/m/preferences')

    expect(document.title).toBe('Email preferences · GroupLoom')
  })
})

describe('safeRedirect', () => {
  it.each([
    ['https://evil.example', '/'],
    ['//evil.example', '/'],
    ['/\\evil.example', '/'],
    ['javascript:alert(1)', '/'],
    [undefined, '/'],
    ['/events?x=1', '/events?x=1'],
  ])('maps %s to %s', (input, expected) => {
    expect(safeRedirect(input)).toBe(expected)
  })
})
