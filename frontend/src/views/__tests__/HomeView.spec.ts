import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'

import { api } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import HomeView from '../HomeView.vue'

vi.mock('@/api/client', () => ({
  api: { GET: vi.fn<() => Promise<unknown>>(), POST: vi.fn<() => Promise<unknown>>() },
}))

const get = vi.mocked(api.GET) as unknown as ReturnType<typeof vi.fn>
const post = vi.mocked(api.POST) as unknown as ReturnType<typeof vi.fn>

function event(id: string) {
  return {
    id,
    title: `Event ${id}`,
    start: '2099-11-02T09:00:00Z',
    end: '2099-11-02T11:00:00Z',
    localStart: '2099-11-02T10:00:00',
    localEnd: '2099-11-02T12:00:00',
    timeZone: 'Europe/Madrid',
    eventType: { id: 't1', name: 'Practice', color: 'green', archived: false },
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  }
}

function pageOf(items: object[]) {
  return { data: { content: items, page: { number: 0, size: 3, totalElements: items.length, totalPages: 1 } }, response: new Response() }
}

async function mountHome() {
  const pinia = createPinia()
  setActivePinia(pinia)
  post.mockResolvedValueOnce({
    data: {
      accessToken: 'jwt',
      expiresIn: 900,
      user: { id: 'u', email: 'ada@example.com', displayName: 'Ada Lovelace', role: 'OWNER', group: { id: 'g', name: 'Chess Club' } },
    },
    response: new Response(),
  })
  await useAuthStore().login({ email: 'ada@example.com', password: 'pw' })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: HomeView },
      { path: '/events', component: { template: '<i/>' } },
      { path: '/events/:id', component: { template: '<i/>' } },
    ],
  })
  await router.push('/')
  await router.isReady()
  const wrapper = mount(HomeView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return wrapper
}

describe('HomeView', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
  })

  it('greets the user and shows their group', async () => {
    get.mockResolvedValue(pageOf([]))

    const wrapper = await mountHome()

    expect(wrapper.find('h1').text()).toBe('Welcome, Ada Lovelace')
    expect(wrapper.text()).toContain('Chess Club')
  })

  it('shows the three nearest upcoming events under "Next up" with a link to all events', async () => {
    get.mockResolvedValue(pageOf([event('1'), event('2'), event('3')]))

    const wrapper = await mountHome()

    expect(get).toHaveBeenCalledWith('/events', { params: { query: { when: 'upcoming', page: 0, size: 3 } } })
    expect(wrapper.text()).toContain('Next up')
    expect(wrapper.findAll('li a')).toHaveLength(3)
    const all = wrapper.findAll('a').find((a) => a.text() === 'See all events')
    expect(all!.attributes('href')).toBe('/events')
  })

  it('shows an empty state with a Schedule an event link when nothing is coming up', async () => {
    get.mockResolvedValue(pageOf([]))

    const wrapper = await mountHome()

    expect(wrapper.text()).toContain('Nothing scheduled')
    const cta = wrapper.findAll('a').find((a) => a.text() === 'Schedule an event')
    expect(cta!.attributes('href')).toBe('/events')
    expect(wrapper.text()).not.toContain('See all events')
  })

  it('shows an error banner with Retry when the events fail to load', async () => {
    get.mockResolvedValueOnce({ error: { status: 500, title: 'Server error' }, response: new Response(null, { status: 500 }) })

    const wrapper = await mountHome()
    expect(wrapper.find('[role=alert]').exists()).toBe(true)

    get.mockResolvedValue(pageOf([event('1')]))
    await wrapper.findAll('button').find((b) => b.text() === 'Retry')!.trigger('click')
    await flushPromises()

    expect(wrapper.findAll('li a')).toHaveLength(1)
  })
})
