import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

import { api } from '@/api/client'
import { useToast } from '@/composables/useToast'
import { installDialogPolyfill } from './dialogPolyfill'

import type { Member } from '@/stores/members'
import MemberList from '../MemberList.vue'

vi.mock('@/api/client', () => ({ api: { POST: vi.fn<() => Promise<unknown>>() } }))
const post = vi.mocked(api.POST) as unknown as ReturnType<typeof vi.fn>

beforeAll(installDialogPolyfill)

function member(overrides: Partial<Member> = {}): Member {
  return {
    id: '1',
    name: 'Ana Pérez',
    email: 'ana@example.com',
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    invitationsPaused: false,
    preferencesEmail: null,
    ...overrides,
  }
}

describe('MemberList', () => {
  it('renders avatar initials, name and email for each row', () => {
    const wrapper = mount(MemberList, {
      props: { items: [member(), member({ id: '2', name: 'Bob', email: 'bob@example.com' })] },
    })

    const rows = wrapper.findAll('li')
    expect(rows).toHaveLength(2)
    expect(rows[0]!.find('[data-avatar]').text()).toBe('AP')
    expect(rows[0]!.text()).toContain('Ana Pérez')
    expect(rows[0]!.text()).toContain('ana@example.com')
    expect(rows[1]!.find('[data-avatar]').text()).toBe('B')
  })

  it('gives the same member the same avatar tint every time', () => {
    const tint = () =>
      mount(MemberList, { props: { items: [member({ id: '0190-abc' })] } }).find('[data-avatar]').classes().join(' ')

    expect(tint()).toBe(tint())
  })

  it('highlights matches in name and email as text nodes, never as HTML', () => {
    const wrapper = mount(MemberList, {
      props: { items: [member({ name: 'Ana <b>Pérez</b>', email: 'ana@example.com' })], query: 'ana' },
    })

    const marks = wrapper.findAll('[data-match]')
    expect(marks.map((m) => m.text())).toEqual(['Ana', 'ana'])
    expect(wrapper.find('[data-row]').element.querySelector('b')).toBeNull()
    expect(wrapper.find('[data-row]').text()).toContain('Ana <b>Pérez</b>')
  })

  it('does not highlight anything without a query', () => {
    const wrapper = mount(MemberList, { props: { items: [member()] } })

    expect(wrapper.findAll('[data-match]')).toHaveLength(0)
  })

  it('makes each row a focusable button that emits edit on click', async () => {
    const first = member()
    const wrapper = mount(MemberList, { props: { items: [first] } })
    const row = wrapper.find('[data-row]')

    expect(row.element.tagName).toBe('BUTTON')
    await row.trigger('click')

    expect(wrapper.emitted('edit')?.[0]).toEqual([first])
  })

  it('emits edit once when Enter is pressed on a row', async () => {
    const first = member()
    const wrapper = mount(MemberList, { props: { items: [first] } })

    await wrapper.find('[data-row]').trigger('keydown', { key: 'Enter' })

    expect(wrapper.emitted('edit')).toEqual([[first]])
  })

  it('opens an actions menu with Email preferences link, Copy preferences link, Edit and Remove', async () => {
    const first = member()
    const wrapper = mount(MemberList, { props: { items: [first] } })
    expect(wrapper.find('[role=menu]').exists()).toBe(false)

    await wrapper.find('button[aria-label="Actions for Ana Pérez"]').trigger('click')
    expect(wrapper.findAll('[role=menuitem]').map((m) => m.text())).toEqual([
      'Email preferences link',
      'Copy preferences link',
      'Edit',
      'Remove',
    ])

    await wrapper.findAll('[role=menuitem]')[3]!.trigger('click')
    expect(wrapper.emitted('remove')?.[0]).toEqual([first])
    expect(wrapper.find('[role=menu]').exists()).toBe(false)
  })

  it('emits edit from the Edit menu item', async () => {
    const first = member()
    const wrapper = mount(MemberList, { props: { items: [first] } })

    await wrapper.find('button[aria-label="Actions for Ana Pérez"]').trigger('click')
    await wrapper.findAll('[role=menuitem]')[2]!.trigger('click')

    expect(wrapper.emitted('edit')?.[0]).toEqual([first])
  })

  it('closes the menu with Escape', async () => {
    const wrapper = mount(MemberList, { props: { items: [member()] }, attachTo: document.body })

    await wrapper.find('button[aria-label="Actions for Ana Pérez"]').trigger('click')
    await wrapper.find('[role=menu]').trigger('keydown', { key: 'Escape' })

    expect(wrapper.find('[role=menu]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('emits send-email from the Email preferences link menu item', async () => {
    const first = member()
    const wrapper = mount(MemberList, { props: { items: [first] } })

    await wrapper.find('button[aria-label="Actions for Ana Pérez"]').trigger('click')
    await wrapper.findAll('[role=menuitem]')[0]!.trigger('click')

    expect(wrapper.emitted('send-email')).toEqual([[first]])
    expect(wrapper.find('[role=menu]').exists()).toBe(false)
  })

  describe('email status line', () => {
    beforeEach(() => {
      vi.useFakeTimers()
      vi.setSystemTime(new Date('2026-01-10T12:00:00Z'))
    })
    afterEach(() => vi.useRealTimers())

    const status = (wrapper: ReturnType<typeof mount>) => wrapper.find('[data-email-status]')

    it('shows nothing for a member who was never emailed', () => {
      const wrapper = mount(MemberList, { props: { items: [member({ preferencesEmail: null })] } })

      expect(status(wrapper).exists()).toBe(false)
    })

    it.each(['PENDING', 'SENDING'] as const)('says "Sending link…" while the email is %s', (value) => {
      const wrapper = mount(MemberList, {
        props: { items: [member({ preferencesEmail: { status: value, at: '2026-01-10T11:59:50Z' } })] },
      })

      expect(status(wrapper).text()).toBe('Sending link…')
    })

    it('says when the link was sent', () => {
      const wrapper = mount(MemberList, {
        props: { items: [member({ preferencesEmail: { status: 'SENT', at: '2026-01-10T10:00:00Z' } })] },
      })

      expect(status(wrapper).text()).toBe('Link sent · 2 hr ago')
    })

    it('says "Couldn\'t deliver link" in red with an inline Try again when it failed', () => {
      const wrapper = mount(MemberList, {
        props: { items: [member({ preferencesEmail: { status: 'FAILED', at: '2026-01-10T11:00:00Z' } })] },
      })

      expect(status(wrapper).text()).toContain("Couldn't deliver link")
      expect(status(wrapper).classes().join(' ')).toContain('app-danger')
      expect(status(wrapper).find('button').text()).toBe('Try again')
    })

    it('does not use red for anything but a failure', () => {
      const wrapper = mount(MemberList, {
        props: { items: [member({ preferencesEmail: { status: 'SENT', at: '2026-01-10T11:00:00Z' } })] },
      })

      expect(status(wrapper).classes().join(' ')).not.toContain('app-danger')
    })

    it('emits send-email for that member when Try again is clicked, without opening the editor', async () => {
      const failed = member({ preferencesEmail: { status: 'FAILED', at: '2026-01-10T11:00:00Z' } })
      const wrapper = mount(MemberList, { props: { items: [failed] } })

      await status(wrapper).get('button').trigger('click')

      expect(wrapper.emitted('send-email')).toEqual([[failed]])
      expect(wrapper.emitted('edit')).toBeUndefined()
    })
  })

  describe('paused pill', () => {
    it('marks members who paused all invitations', () => {
      const wrapper = mount(MemberList, { props: { items: [member({ invitationsPaused: true })] } })

      const pill = wrapper.get('[data-paused]')
      expect(pill.text()).toBe('Paused')
      expect(pill.classes()).toContain('rounded-full')
    })

    it('shows nothing for members who have not paused', () => {
      const wrapper = mount(MemberList, { props: { items: [member()] } })

      expect(wrapper.find('[data-paused]').exists()).toBe(false)
    })
  })

  describe('copy preferences link', () => {
    const URL = 'https://app.example.com/m/preferences#t=abc'
    const writeText = vi.fn<(text: string) => Promise<void>>()

    function link(days = 60) {
      return {
        data: { url: URL, expiresAt: new Date(Date.now() + days * 86_400_000).toISOString() },
        response: new Response(),
      }
    }

    async function chooseCopy(wrapper: ReturnType<typeof mount>) {
      await wrapper.get('button[aria-label="Actions for Ana Pérez"]').trigger('click')
      await wrapper.findAll('[role=menuitem]').find((m) => m.text() === 'Copy preferences link')!.trigger('click')
      await flushPromises()
    }

    beforeEach(() => {
      setActivePinia(createPinia())
      post.mockReset()
      post.mockResolvedValue(link())
      writeText.mockReset()
      writeText.mockResolvedValue()
      Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
      useToast().dismiss()
      document.body.innerHTML = ''
    })

    afterEach(() => {
      Reflect.deleteProperty(navigator, 'clipboard')
    })

    it('creates a link for that member, copies it and confirms with a toast', async () => {
      const wrapper = mount(MemberList, { props: { items: [member({ id: 'm1' })] }, attachTo: document.body })

      await chooseCopy(wrapper)

      expect(post).toHaveBeenCalledWith('/members/{id}/preferences-link', { params: { path: { id: 'm1' } } })
      expect(writeText).toHaveBeenCalledWith(URL)
      expect(useToast().toast.value?.message).toBe('Link copied. It works for 60 days.')
      expect(wrapper.find('[role=menu]').exists()).toBe(false)
      expect(document.querySelector('dialog[open]')).toBeNull()
      wrapper.unmount()
    })

    it('shows the link in a read-only, pre-selected field when the clipboard is unavailable', async () => {
      Reflect.deleteProperty(navigator, 'clipboard')
      const wrapper = mount(MemberList, { props: { items: [member()] }, attachTo: document.body })

      await chooseCopy(wrapper)
      await flushPromises()

      const dialog = wrapper.get('dialog[open]')
      const field = dialog.get('input[readonly]').element as HTMLInputElement
      expect(field.value).toBe(URL)
      expect(document.activeElement).toBe(field)
      expect([field.selectionStart, field.selectionEnd]).toEqual([0, URL.length])
      expect(dialog.findAll('button').map((b) => b.text())).toContain('Copy')
      expect(useToast().toast.value).toBeNull()
      wrapper.unmount()
    })

    it('falls back to the dialog when the browser refuses to write to the clipboard', async () => {
      writeText.mockRejectedValue(new DOMException('denied', 'NotAllowedError'))
      const wrapper = mount(MemberList, { props: { items: [member()] }, attachTo: document.body })

      await chooseCopy(wrapper)

      expect(wrapper.get('dialog[open] input[readonly]').element).toHaveProperty('value', URL)
      expect(useToast().toast.value).toBeNull()
      wrapper.unmount()
    })

    it('closes the fallback dialog with Done', async () => {
      Reflect.deleteProperty(navigator, 'clipboard')
      const wrapper = mount(MemberList, { props: { items: [member()] }, attachTo: document.body })
      await chooseCopy(wrapper)

      await wrapper.findAll('dialog[open] button').find((b) => b.text() === 'Done')!.trigger('click')

      expect(wrapper.find('dialog[open]').exists()).toBe(false)
      wrapper.unmount()
    })

    it('tells the organizer when no link could be created', async () => {
      post.mockResolvedValue({ error: { status: 404, title: 'Not found' }, response: new Response(null, { status: 404 }) })
      const wrapper = mount(MemberList, { props: { items: [member()] }, attachTo: document.body })

      await chooseCopy(wrapper)

      expect(writeText).not.toHaveBeenCalled()
      expect(useToast().toast.value?.message).toBe("Couldn't create the link. Try again.")
      wrapper.unmount()
    })

    it('words the toast with the real lifetime of the link', async () => {
      post.mockResolvedValue(link(14))
      const wrapper = mount(MemberList, { props: { items: [member()] }, attachTo: document.body })

      await chooseCopy(wrapper)

      expect(useToast().toast.value?.message).toBe('Link copied. It works for 14 days.')
      wrapper.unmount()
    })
  })
})
