import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'

import { api } from '@/api/client'
import { createAppRouter } from '@/router'
import App from '../App.vue'

vi.mock('@/api/client', () => ({
  api: { GET: vi.fn<() => Promise<unknown>>(), POST: vi.fn<() => Promise<unknown>>() },
}))

const get = vi.mocked(api.GET) as unknown as ReturnType<typeof vi.fn>
const post = vi.mocked(api.POST) as unknown as ReturnType<typeof vi.fn>

const signedIn = {
  data: {
    accessToken: 'jwt',
    expiresIn: 900,
    user: { id: 'u', email: 'ada@example.com', displayName: 'Ada', role: 'OWNER', group: { id: 'g', name: 'Chess Club' } },
  },
  response: new Response(),
}
const signedOut = { error: { status: 401 }, response: new Response(null, { status: 401 }) }

const info = { data: { name: 'backend', version: '1.2.3' }, response: new Response() }
const noEvents = {
  data: { content: [], page: { number: 0, size: 3, totalElements: 0, totalPages: 1 } },
  response: new Response(),
}

/** Answers GETs by path: system info for the footer, an empty event list for the home page. */
function serve(systemInfo: unknown = info) {
  get.mockImplementation(async (path: string) => (path === '/events' ? noEvents : systemInfo))
}

async function mountApp(path = '/') {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createAppRouter(createMemoryHistory())
  await router.push(path)
  await router.isReady()
  const wrapper = mount(App, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('App', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
    serve()
  })

  it('shows the welcome page, group and footer version to a signed-in user', async () => {
    post.mockResolvedValue(signedIn)

    const { wrapper } = await mountApp()

    expect(wrapper.find('h1').text()).toBe('Welcome, Ada')
    expect(wrapper.text()).toContain('Chess Club')
    expect(wrapper.find('footer').text()).toContain('GroupLoom v1.2.3')
    expect(document.title).toBe('Home · GroupLoom')
  })

  it('sends a guest to the sign-in page', async () => {
    post.mockResolvedValue(signedOut)

    const { wrapper, router } = await mountApp()

    expect(router.currentRoute.value.path).toBe('/login')
    expect(wrapper.find('h1').text()).toBe('Sign in to GroupLoom')
    expect(wrapper.find('header').exists()).toBe(false)
  })

  it('shows the member preferences page without the app shell or any session lookup', async () => {
    get.mockImplementation(async (path: string) =>
      path === '/public/preferences'
        ? {
            data: { memberName: 'Ana', maskedEmail: 'a••@example.com', groupName: 'Chess Club', invitationsPaused: false, eventTypes: [] },
            response: new Response(),
          }
        : info,
    )

    const { wrapper } = await mountApp('/m/preferences#t=abc')

    expect(wrapper.find('h1').text()).toBe('Email preferences')
    expect(wrapper.find('header').exists()).toBe(false)
    expect(wrapper.find('footer').text()).toBe('Sent by Chess Club via GroupLoom')
    expect(post).not.toHaveBeenCalled()
    expect(get).not.toHaveBeenCalledWith('/system/info')
  })

  it('hides the footer version quietly when the backend is down', async () => {
    post.mockResolvedValue(signedIn)
    serve({ error: { status: 502, title: 'Bad Gateway' }, response: new Response(null, { status: 502 }) })

    const { wrapper } = await mountApp()

    expect(wrapper.find('footer').text()).toBe('')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('shows the not-found page for unknown routes', async () => {
    post.mockResolvedValue(signedOut)

    const { wrapper } = await mountApp('/nope')

    expect(wrapper.text()).toContain('Page not found')
  })

  it('returns to the sign-in page when the session ends', async () => {
    post.mockResolvedValue(signedIn)
    const { router } = await mountApp()
    post.mockResolvedValue({ response: new Response(null, { status: 204 }) })

    const { useAuthStore } = await import('@/stores/auth')
    await useAuthStore().logout()
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/login')
  })
})
