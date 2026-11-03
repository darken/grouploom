import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

import { api } from '@/api/client'
import { applyLocale } from '@/i18n'
import { useAuthStore } from '@/stores/auth'
import UserMenu from '../UserMenu.vue'

vi.mock('@/api/client', () => ({
  api: {
    GET: vi.fn<() => Promise<unknown>>(),
    POST: vi.fn<() => Promise<unknown>>(),
    PATCH: vi.fn<() => Promise<unknown>>(),
  },
}))

const post = vi.mocked(api.POST) as unknown as ReturnType<typeof vi.fn>
const patch = vi.mocked(api.PATCH) as unknown as ReturnType<typeof vi.fn>

// Organizers see no group settings, which keeps the focus and Tab specs about the menu itself.
async function mountMenu(role: 'OWNER' | 'ORGANIZER' = 'ORGANIZER', language: 'en' | 'ja' = 'en') {
  setActivePinia(createPinia())
  post.mockReset()
  patch.mockReset()
  post.mockResolvedValueOnce({
    data: {
      accessToken: 'jwt',
      expiresIn: 900,
      user: { id: 'u', email: 'ada@example.com', displayName: 'Ada Lovelace', role, group: { id: 'g', name: 'Chess Club', language } },
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

describe('UserMenu email language', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  const select = (wrapper: Awaited<ReturnType<typeof mountMenu>>['wrapper']) => wrapper.find('select[name=language]')

  it('shows the owner the group language in its own script, with help text', async () => {
    const { wrapper } = await mountMenu('OWNER', 'ja')
    await wrapper.find('button[aria-label=Account]').trigger('click')

    expect((select(wrapper).element as HTMLSelectElement).value).toBe('ja')
    expect(select(wrapper).findAll('option').map((o) => o.text())).toEqual(['English', '日本語'])
    expect(wrapper.find('[role=dialog]').text()).toContain('Email language')
    expect(wrapper.find('[role=dialog]').text()).toContain('Emails to members are sent in this language.')
  })

  it('saves a new language through the API', async () => {
    const { wrapper, auth } = await mountMenu('OWNER', 'en')
    patch.mockResolvedValueOnce({ data: { id: 'g', name: 'Chess Club', language: 'ja' }, response: new Response() })
    await wrapper.find('button[aria-label=Account]').trigger('click')

    await select(wrapper).setValue('ja')
    await vi.waitFor(() => expect(auth.user?.group.language).toBe('ja'))

    expect(patch).toHaveBeenCalledWith('/group', { body: { language: 'ja' } })
  })

  it('is hidden from an organizer', async () => {
    const { wrapper } = await mountMenu('ORGANIZER')
    await wrapper.find('button[aria-label=Account]').trigger('click')

    expect(select(wrapper).exists()).toBe(false)
    expect(wrapper.find('[role=dialog]').text()).not.toContain('Email language')
  })

  it('labels the control in Japanese', async () => {
    applyLocale('ja')
    const { wrapper } = await mountMenu('OWNER')
    await wrapper.find('button[aria-label=アカウント]').trigger('click')

    expect(wrapper.find('[role=dialog]').text()).toContain('メールの言語')
  })
})
