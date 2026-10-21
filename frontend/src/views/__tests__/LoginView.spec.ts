import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'

import { api } from '@/api/client'
import { createAppRouter } from '@/router'
import LoginView from '../LoginView.vue'

vi.mock('@/api/client', () => ({
  api: { GET: vi.fn<() => Promise<unknown>>(), POST: vi.fn<() => Promise<unknown>>() },
}))

const post = vi.mocked(api.POST) as unknown as ReturnType<typeof vi.fn>

const me = {
  accessToken: 'jwt',
  expiresIn: 900,
  user: { id: 'u', email: 'a@b.c', displayName: 'Ada', role: 'OWNER', group: { id: 'g', name: 'Club' } },
}
const unauthorized = { error: { status: 401 }, response: new Response(null, { status: 401 }) }

async function mountLogin(path = '/login') {
  const pinia = createPinia()
  setActivePinia(pinia)
  post.mockResolvedValueOnce(unauthorized) // session restore on first navigation
  const router = createAppRouter(createMemoryHistory())
  await router.push(path)
  const wrapper = mount(LoginView, { global: { plugins: [pinia, router] } })
  return { wrapper, router }
}

describe('LoginView', () => {
  beforeEach(() => post.mockReset())

  it('shows required-field errors after a blank submit and does not call the API', async () => {
    const { wrapper } = await mountLogin()

    await wrapper.find('form').trigger('submit')

    expect(wrapper.text()).toContain('Enter your email address.')
    expect(wrapper.text()).toContain('Enter your password.')
    expect(post).toHaveBeenCalledTimes(1) // only the session restore
  })

  it('shows field errors from a 400 problem detail', async () => {
    const { wrapper } = await mountLogin()
    post.mockResolvedValueOnce({
      error: { status: 400, title: 'Bad Request', errors: [{ field: 'email', message: 'must be a well-formed email address' }] },
      response: new Response(null, { status: 400 }),
    })

    await wrapper.find('input[name=email]').setValue('nope')
    await wrapper.find('input[name=password]').setValue('pw')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('must be a well-formed email address')
    expect(wrapper.find('[role=alert]').exists()).toBe(false)
  })

  it('shows the generic message on 401', async () => {
    const { wrapper } = await mountLogin()
    post.mockResolvedValueOnce({
      error: { status: 401, title: 'Unauthorized', detail: 'Email or password is incorrect.' },
      response: new Response(null, { status: 401 }),
    })

    await wrapper.find('input[name=email]').setValue('a@b.c')
    await wrapper.find('input[name=password]').setValue('wrong')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[role=alert]').text()).toContain('Email or password is incorrect.')
  })

  it('navigates to the redirect target on success', async () => {
    const { wrapper, router } = await mountLogin('/login?redirect=/nope')
    post.mockResolvedValueOnce({ data: me, response: new Response() })

    await wrapper.find('input[name=email]').setValue('a@b.c')
    await wrapper.find('input[name=password]').setValue('pw')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/nope'))
  })
})
