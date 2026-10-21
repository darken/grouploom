import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'

import { api } from '@/api/client'
import { createAppRouter } from '@/router'
import SignupView from '../SignupView.vue'

vi.mock('@/api/client', () => ({
  api: { GET: vi.fn<() => Promise<unknown>>(), POST: vi.fn<() => Promise<unknown>>() },
}))

const post = vi.mocked(api.POST) as unknown as ReturnType<typeof vi.fn>

const me = {
  accessToken: 'jwt',
  expiresIn: 900,
  user: { id: 'u', email: 'a@b.c', displayName: 'Ada', role: 'OWNER', group: { id: 'g', name: 'Club' } },
}

async function mountSignup() {
  const pinia = createPinia()
  setActivePinia(pinia)
  post.mockResolvedValueOnce({ error: { status: 401 }, response: new Response(null, { status: 401 }) })
  const router = createAppRouter(createMemoryHistory())
  await router.push('/signup')
  return { wrapper: mount(SignupView, { global: { plugins: [pinia, router] } }), router }
}

async function fill(wrapper: Awaited<ReturnType<typeof mountSignup>>['wrapper']) {
  await wrapper.find('input[name=groupName]').setValue('Chess Club')
  await wrapper.find('input[name=displayName]').setValue('Ada')
  await wrapper.find('input[name=email]').setValue('ada@example.com')
  await wrapper.find('input[name=password]').setValue('correct horse battery')
}

describe('SignupView', () => {
  beforeEach(() => post.mockReset())

  it('creates the group and lands on home', async () => {
    const { wrapper, router } = await mountSignup()
    post.mockResolvedValueOnce({ data: me, response: new Response() })

    await fill(wrapper)
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(post).toHaveBeenLastCalledWith('/auth/signup', {
      body: { groupName: 'Chess Club', displayName: 'Ada', email: 'ada@example.com', password: 'correct horse battery' },
    })
    // Lazy-loaded route components resolve asynchronously.
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/'))
  })

  it('shows the duplicate email message on 409', async () => {
    const { wrapper, router } = await mountSignup()
    post.mockResolvedValueOnce({ error: { status: 409, title: 'Conflict' }, response: new Response(null, { status: 409 }) })

    await fill(wrapper)
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('An account with this email already exists.')
    expect(router.currentRoute.value.path).toBe('/signup')
  })

  it('requires a password of at least 12 characters', async () => {
    const { wrapper } = await mountSignup()

    await fill(wrapper)
    await wrapper.find('input[name=password]').setValue('short')
    await wrapper.find('form').trigger('submit')

    expect(wrapper.text()).toContain('Use at least 12 characters.')
    expect(post).toHaveBeenCalledTimes(1)
  })
})
