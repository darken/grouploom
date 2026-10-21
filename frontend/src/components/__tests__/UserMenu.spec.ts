import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

import { api } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import UserMenu from '../UserMenu.vue'

vi.mock('@/api/client', () => ({
  api: { GET: vi.fn<() => Promise<unknown>>(), POST: vi.fn<() => Promise<unknown>>() },
}))

const post = vi.mocked(api.POST) as unknown as ReturnType<typeof vi.fn>

async function mountMenu() {
  setActivePinia(createPinia())
  post.mockReset()
  post.mockResolvedValueOnce({
    data: {
      accessToken: 'jwt',
      expiresIn: 900,
      user: { id: 'u', email: 'ada@example.com', displayName: 'Ada Lovelace', role: 'OWNER', group: { id: 'g', name: 'Chess Club' } },
    },
    response: new Response(),
  })
  const auth = useAuthStore()
  await auth.login({ email: 'ada@example.com', password: 'pw' })
  const wrapper = mount(UserMenu, { attachTo: document.body })
  return { wrapper, auth }
}

describe('UserMenu', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  it('shows initials and opens with details', async () => {
    const { wrapper } = await mountMenu()
    const trigger = wrapper.find('button[aria-label=Account]')

    expect(trigger.text()).toBe('AL')
    expect(wrapper.find('[role=dialog]').exists()).toBe(false)

    await trigger.trigger('click')

    expect(wrapper.find('[role=dialog]').text()).toContain('ada@example.com')
    expect(wrapper.find('[role=dialog]').text()).toContain('Chess Club')
    expect(trigger.attributes('aria-expanded')).toBe('true')
  })

  it('moves focus into the popover and closes with Escape, restoring focus', async () => {
    const { wrapper } = await mountMenu()
    const trigger = wrapper.find('button[aria-label=Account]')

    await trigger.trigger('click')
    expect(document.activeElement?.textContent).toContain('Sign out')

    await wrapper.find('[role=dialog]').trigger('keydown', { key: 'Escape' })

    expect(wrapper.find('[role=dialog]').exists()).toBe(false)
    expect(document.activeElement).toBe(trigger.element)
  })

  it('traps Tab inside the popover', async () => {
    const { wrapper } = await mountMenu()
    await wrapper.find('button[aria-label=Account]').trigger('click')
    const signOut = wrapper.find('[role=dialog] button')
    ;(signOut.element as HTMLElement).focus()

    await signOut.trigger('keydown', { key: 'Tab' })

    expect(document.activeElement).toBe(signOut.element)
  })

  it('Sign out calls logout', async () => {
    const { wrapper, auth } = await mountMenu()
    post.mockResolvedValueOnce({ response: new Response(null, { status: 204 }) })
    await wrapper.find('button[aria-label=Account]').trigger('click')

    await wrapper.find('[role=dialog] button').trigger('click')
    await vi.waitFor(() => expect(auth.status).toBe('anonymous'))

    expect(post).toHaveBeenLastCalledWith('/auth/logout')
  })
})
