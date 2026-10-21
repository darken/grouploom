import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

import { api } from '@/api/client'
import router from '@/router'
import App from '../App.vue'

vi.mock('@/api/client', () => ({ api: { GET: vi.fn<() => Promise<unknown>>() } }))

const get = vi.mocked(api.GET) as unknown as ReturnType<typeof vi.fn>

async function mountApp(path = '/') {
  const pinia = createPinia()
  setActivePinia(pinia)
  await router.push(path)
  await router.isReady()
  const wrapper = mount(App, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return wrapper
}

describe('App', () => {
  beforeEach(() => get.mockReset())

  it('shows the welcome headline and the backend version in the footer', async () => {
    get.mockResolvedValue({ data: { name: 'backend', version: '1.2.3' }, response: new Response() })

    const wrapper = await mountApp()

    expect(wrapper.find('h1').text()).toBe('Welcome to GroupLoom')
    expect(wrapper.find('footer').text()).toContain('GroupLoom v1.2.3')
    expect(document.title).toBe('Home · GroupLoom')
  })

  it('hides the footer version quietly when the backend is down', async () => {
    get.mockResolvedValue({ error: { status: 502, title: 'Bad Gateway' }, response: new Response(null, { status: 502 }) })

    const wrapper = await mountApp()

    expect(wrapper.find('h1').text()).toBe('Welcome to GroupLoom')
    expect(wrapper.find('footer').text()).toBe('')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('shows the not-found page for unknown routes', async () => {
    get.mockResolvedValue({ error: { status: 502, title: 'Bad Gateway' }, response: new Response(null, { status: 502 }) })

    const wrapper = await mountApp('/nope')

    expect(wrapper.text()).toContain('Page not found')
  })
})
