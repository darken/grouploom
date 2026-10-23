import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'

import { api } from '@/api/client'
import ToastHost from '@/components/ToastHost.vue'
import { useToast } from '@/composables/useToast'
import { installDialogPolyfill } from '@/components/__tests__/dialogPolyfill'
import MembersView from '../MembersView.vue'

vi.mock('@/api/client', () => ({
  api: {
    GET: vi.fn<() => Promise<unknown>>(),
    POST: vi.fn<() => Promise<unknown>>(),
    PUT: vi.fn<() => Promise<unknown>>(),
    DELETE: vi.fn<() => Promise<unknown>>(),
  },
}))

const get = vi.mocked(api.GET) as unknown as ReturnType<typeof vi.fn>
const post = vi.mocked(api.POST) as unknown as ReturnType<typeof vi.fn>
const put = vi.mocked(api.PUT) as unknown as ReturnType<typeof vi.fn>
const del = vi.mocked(api.DELETE) as unknown as ReturnType<typeof vi.fn>

beforeAll(installDialogPolyfill)

function member(id: string, overrides: object = {}) {
  return {
    id, name: `Member ${id}`, email: `m${id}@example.com`, version: 0,
    createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z', ...overrides,
  }
}

function pageOf(items: object[], total = items.length, number = 0) {
  return {
    data: { content: items, page: { number, size: 20, totalElements: total, totalPages: Math.max(1, Math.ceil(total / 20)) } },
    response: new Response(),
  }
}

// Views listen on window, so each test unmounts what it mounted.
const mounted: { unmount: () => void }[] = []

async function mountView(url = '/members') {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/members', component: MembersView }] })
  await router.push(url)
  await router.isReady()
  const wrapper = mount({ components: { MembersView, ToastHost }, template: '<MembersView /><ToastHost />' }, {
    global: { plugins: [pinia, router] },
    attachTo: document.body,
  })
  mounted.push(wrapper)
  await flushPromises()
  return { wrapper, router }
}

type Wrapper = Awaited<ReturnType<typeof mountView>>['wrapper']
const dialogOpen = (w: Wrapper) => w.findAll('dialog').filter((d) => d.attributes('open') !== undefined)
const button = (w: Wrapper, label: string) => w.findAll('button').find((b) => b.text() === label)

async function fillAndSubmit(wrapper: Wrapper, name: string, email: string) {
  await wrapper.find('dialog input[name=name]').setValue(name)
  await wrapper.find('dialog input[name=email]').setValue(email)
}

describe('MembersView', () => {
  beforeEach(() => {
    for (const m of [get, post, put, del]) m.mockReset()
    useToast().dismiss()
    document.body.innerHTML = ''
  })
  afterEach(() => {
    vi.useRealTimers()
    mounted.splice(0).forEach((w) => w.unmount())
  })

  it('renders the title, a count and one row per member', async () => {
    get.mockResolvedValue(pageOf([member('1'), member('2')], 24))

    const { wrapper } = await mountView()

    expect(wrapper.find('h1').text()).toBe('Members')
    expect(wrapper.text()).toContain('24 members')
    expect(wrapper.findAll('li')).toHaveLength(2)
    expect(get).toHaveBeenCalledWith('/members', expect.objectContaining({ params: { query: expect.objectContaining({ page: 0 }) } }))
  })

  it('uses the singular for a single member', async () => {
    get.mockResolvedValue(pageOf([member('1')]))

    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('1 member')
    expect(wrapper.text()).not.toContain('1 members')
  })

  it('reads q and page from the URL (page is 1-based)', async () => {
    get.mockResolvedValue(pageOf([member('1')], 45, 1))

    const { wrapper } = await mountView('/members?q=ana&page=2')

    expect(get).toHaveBeenCalledWith('/members', expect.objectContaining({ params: { query: expect.objectContaining({ q: 'ana', page: 1 }) } }))
    expect((wrapper.find('input[type=search]').element as HTMLInputElement).value).toBe('ana')
  })

  it('shows skeleton rows during the first load', async () => {
    let resolve!: (v: unknown) => void
    get.mockReturnValue(new Promise((r) => (resolve = r)))
    const { wrapper } = await mountView()

    expect(wrapper.findAll('[data-skeleton]')).toHaveLength(3)

    resolve(pageOf([member('1')]))
    await flushPromises()
    expect(wrapper.findAll('[data-skeleton]')).toHaveLength(0)
  })

  it('keeps the old rows and shows a progress bar while refetching', async () => {
    get.mockResolvedValueOnce(pageOf([member('1')]))
    const { wrapper, router } = await mountView()
    let resolve!: (v: unknown) => void
    get.mockReturnValue(new Promise((r) => (resolve = r)))

    await router.push('/members?q=x')
    await flushPromises()

    expect(wrapper.findAll('li')).toHaveLength(1)
    expect(wrapper.find('[role=progressbar]').exists()).toBe(true)
    resolve(pageOf([]))
  })

  it('shows the empty state with an "Add member" call to action', async () => {
    get.mockResolvedValue(pageOf([]))

    const { wrapper } = await mountView()

    expect(wrapper.find('h2').text()).toBe('No members yet.')
    expect(wrapper.text()).toContain("Members get event invitations by email — they don't need an account.")
    const cta = wrapper.findAll('button').filter((b) => b.text() === 'Add member').pop()!
    await cta.trigger('click')
    expect(dialogOpen(wrapper)).toHaveLength(1)
  })

  it('shows the no-results state with Clear search, which resets the query', async () => {
    get.mockResolvedValue(pageOf([]))
    const { wrapper, router } = await mountView('/members?q=zzz')
    expect(wrapper.text()).toContain('No members match "zzz".')
    expect(wrapper.text()).not.toContain('No members yet')

    await button(wrapper, 'Clear search')!.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query.q).toBeUndefined()
  })

  it('puts the debounced search text in the URL and resets the page', async () => {
    get.mockResolvedValue(pageOf([member('1')], 45, 1))
    const { wrapper, router } = await mountView('/members?page=2')
    vi.useFakeTimers()

    await wrapper.find('input[type=search]').setValue('ana')
    expect(router.currentRoute.value.query.q).toBeUndefined()
    vi.advanceTimersByTime(250)
    vi.useRealTimers()
    await flushPromises()

    expect(router.currentRoute.value.query).toEqual({ q: 'ana' })
    expect(get).toHaveBeenLastCalledWith('/members', expect.objectContaining({ params: { query: expect.objectContaining({ q: 'ana', page: 0 }) } }))
  })

  it('goes to another page through the URL while keeping the search', async () => {
    get.mockResolvedValue(pageOf([member('1')], 45))
    const { wrapper, router } = await mountView('/members?q=m')

    await button(wrapper, 'Next')!.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query).toEqual({ q: 'm', page: '2' })
  })

  it('opens the create dialog from "Add member"', async () => {
    get.mockResolvedValue(pageOf([member('1')]))
    const { wrapper } = await mountView()

    await button(wrapper, 'Add member')!.trigger('click')

    expect(dialogOpen(wrapper)).toHaveLength(1)
    expect(dialogOpen(wrapper)[0]!.find('h2').text()).toBe('Add member')
  })

  it('creates a member, closes the dialog and confirms with a toast', async () => {
    get.mockResolvedValue(pageOf([member('1')]))
    post.mockResolvedValue({ data: member('2'), response: new Response() })
    const { wrapper } = await mountView()
    await button(wrapper, 'Add member')!.trigger('click')

    await fillAndSubmit(wrapper, 'Ana', 'ana@example.com')
    await wrapper.find('dialog form').trigger('submit')
    await flushPromises()

    expect(post).toHaveBeenCalledWith('/members', { body: { name: 'Ana', email: 'ana@example.com' } })
    expect(dialogOpen(wrapper)).toHaveLength(0)
    expect(wrapper.find('[aria-live=polite]').text()).toContain('Member added')
  })

  it('"Save and add another" keeps the dialog open with an empty, focused form and a toast', async () => {
    get.mockResolvedValue(pageOf([member('1')]))
    post.mockResolvedValue({ data: member('2'), response: new Response() })
    const { wrapper } = await mountView()
    await button(wrapper, 'Add member')!.trigger('click')
    await fillAndSubmit(wrapper, 'Ana', 'ana@example.com')

    await button(wrapper, 'Save and add another')!.trigger('click')
    await flushPromises()

    expect(post).toHaveBeenCalledWith('/members', { body: { name: 'Ana', email: 'ana@example.com' } })
    expect(dialogOpen(wrapper)).toHaveLength(1)
    const name = wrapper.find('dialog input[name=name]')
    expect((name.element as HTMLInputElement).value).toBe('')
    expect((wrapper.find('dialog input[name=email]').element as HTMLInputElement).value).toBe('')
    expect(document.activeElement).toBe(name.element)
    expect(wrapper.find('[aria-live=polite]').text()).toContain('Ana added')
  })

  it('shows an email-taken conflict under the email field and keeps the dialog open', async () => {
    get.mockResolvedValue(pageOf([member('1')]))
    post.mockResolvedValue({
      error: { status: 409, code: 'member.email-taken', field: 'email', detail: 'A member with this email already exists.' },
      response: new Response(null, { status: 409 }),
    })
    const { wrapper } = await mountView()
    await button(wrapper, 'Add member')!.trigger('click')

    await fillAndSubmit(wrapper, 'Ana', 'm1@example.com')
    await wrapper.find('dialog form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('dialog').text()).toContain('A member with this email already exists.')
    expect(dialogOpen(wrapper)).toHaveLength(1)
  })

  it('edits a member with the version it was loaded with', async () => {
    get.mockResolvedValue(pageOf([member('1', { name: 'Ana', version: 4 })]))
    put.mockResolvedValue({ data: member('1', { name: 'Ana María', version: 5 }), response: new Response() })
    const { wrapper } = await mountView()

    await wrapper.find('[data-row]').trigger('click')
    expect(dialogOpen(wrapper)[0]!.find('h2').text()).toBe('Edit member')
    await wrapper.find('dialog input[name=name]').setValue('Ana María')
    await wrapper.find('dialog form').trigger('submit')
    await flushPromises()

    expect(put).toHaveBeenCalledWith('/members/{id}', {
      params: { path: { id: '1' } },
      body: { name: 'Ana María', email: 'm1@example.com', version: 4 },
    })
    expect(dialogOpen(wrapper)).toHaveLength(0)
    expect(wrapper.find('[data-row]').text()).toContain('Ana María')
  })

  it('offers Reload when the member was changed by someone else', async () => {
    get.mockResolvedValue(pageOf([member('1')]))
    put.mockResolvedValue({ error: { status: 409, code: 'member.stale' }, response: new Response(null, { status: 409 }) })
    const { wrapper } = await mountView()
    await wrapper.find('[data-row]').trigger('click')
    await wrapper.find('dialog input[name=name]').setValue('Changed')
    await wrapper.find('dialog form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('dialog [role=alert]').text()).toContain('Changed by someone else')
    get.mockClear()
    await button(wrapper, 'Reload')!.trigger('click')
    await flushPromises()

    expect(get).toHaveBeenCalled()
    expect(dialogOpen(wrapper)).toHaveLength(0)
  })

  it('asks for confirmation before removing and only then calls the store', async () => {
    get.mockResolvedValue(pageOf([member('1', { name: 'Ana' }), member('2')], 2))
    del.mockResolvedValue({ response: new Response(null, { status: 204 }) })
    const { wrapper } = await mountView()

    await wrapper.find('button[aria-label="Actions for Ana"]').trigger('click')
    await wrapper.findAll('[role=menuitem]').find((m) => m.text() === 'Remove')!.trigger('click')

    expect(del).not.toHaveBeenCalled()
    const confirm = dialogOpen(wrapper)[0]!
    expect(confirm.find('h2').text()).toBe('Remove Ana?')
    expect(confirm.text()).toContain("They'll stop receiving invitations. This can't be undone.")

    get.mockResolvedValue(pageOf([member('2')], 1))
    await confirm.findAll('button').find((b) => b.text() === 'Remove')!.trigger('click')
    await flushPromises()

    expect(del).toHaveBeenCalledWith('/members/{id}', { params: { path: { id: '1' } } })
    expect(wrapper.findAll('li')).toHaveLength(1)
    expect(wrapper.find('[aria-live=polite]').text()).toContain('Removed')
  })

  it('does not remove when the confirmation is cancelled', async () => {
    get.mockResolvedValue(pageOf([member('1', { name: 'Ana' })]))
    const { wrapper } = await mountView()
    await wrapper.find('button[aria-label="Actions for Ana"]').trigger('click')
    await wrapper.findAll('[role=menuitem]').find((m) => m.text() === 'Remove')!.trigger('click')

    await dialogOpen(wrapper)[0]!.findAll('button').find((b) => b.text() === 'Cancel')!.trigger('click')

    expect(del).not.toHaveBeenCalled()
    expect(dialogOpen(wrapper)).toHaveLength(0)
  })

  it('shows an error banner with Retry when loading fails', async () => {
    get.mockResolvedValueOnce({ error: { status: 500, title: 'Server error' }, response: new Response(null, { status: 500 }) })
    const { wrapper } = await mountView()
    expect(wrapper.find('[role=alert]').exists()).toBe(true)

    get.mockResolvedValue(pageOf([member('1')]))
    await button(wrapper, 'Retry')!.trigger('click')
    await flushPromises()

    expect(wrapper.findAll('li')).toHaveLength(1)
  })

  describe('emailing preferences links', () => {
    const bulkMenu = (w: Wrapper) => w.get('button[aria-label="More member actions"]')
    const bulkItem = (w: Wrapper) => w.findAll('[role=menuitem]').find((m) => m.text() === 'Email preferences links to everyone')!
    const toastText = () => useToast().toast.value?.message

    async function openBulkConfirm(wrapper: Wrapper) {
      await bulkMenu(wrapper).trigger('click')
      await bulkItem(wrapper).trigger('click')
    }

    async function chooseRowAction(wrapper: Wrapper, name: string, action: string) {
      await wrapper.get(`button[aria-label="Actions for ${name}"]`).trigger('click')
      await wrapper.findAll('[role=menuitem]').find((m) => m.text() === action)!.trigger('click')
      await flushPromises()
    }

    it('keeps "Add member" as the one primary action and puts bulk send in an overflow menu', async () => {
      get.mockResolvedValue(pageOf([member('1')]))

      const { wrapper } = await mountView()

      expect(wrapper.find('[role=menu]').exists()).toBe(false)
      await bulkMenu(wrapper).trigger('click')
      expect(wrapper.findAll('[role=menuitem]').map((m) => m.text())).toEqual(['Email preferences links to everyone'])
    })

    it('asks for confirmation with the member count before sending to everyone', async () => {
      get.mockResolvedValue(pageOf([member('1'), member('2')], 24))
      const { wrapper } = await mountView()

      await openBulkConfirm(wrapper)

      const dialog = dialogOpen(wrapper)[0]!
      expect(dialog.text()).toContain(
        'Send preferences links to 24 members? Anyone who got a link in the last 5 minutes is skipped.',
      )
      expect(dialog.findAll('button').map((b) => b.text())).toEqual(['Cancel', 'Send links'])
      expect(dialog.findAll('button')[1]!.classes().join(' ')).not.toContain('danger')
      expect(post).not.toHaveBeenCalled()
    })

    it('sends, refetches and says how many links are going out', async () => {
      get.mockResolvedValue(pageOf([member('1')], 24))
      post.mockResolvedValue({ data: { queued: 22, skipped: 2 }, response: new Response() })
      const { wrapper } = await mountView()
      await openBulkConfirm(wrapper)
      get.mockClear()

      await button(wrapper, 'Send links')!.trigger('click')
      await flushPromises()

      expect(post).toHaveBeenCalledWith('/members/preferences-email')
      expect(toastText()).toBe('Sending links to 22 members · 2 skipped')
      expect(get).toHaveBeenCalledTimes(1)
      expect(dialogOpen(wrapper)).toHaveLength(0)
    })

    it('leaves out "skipped" when nobody was skipped and uses the singular for one member', async () => {
      get.mockResolvedValue(pageOf([member('1')]))
      post.mockResolvedValue({ data: { queued: 1, skipped: 0 }, response: new Response() })
      const { wrapper } = await mountView()
      await openBulkConfirm(wrapper)

      await button(wrapper, 'Send links')!.trigger('click')
      await flushPromises()

      expect(toastText()).toBe('Sending link to 1 member')
    })

    it('says so when everyone was skipped', async () => {
      get.mockResolvedValue(pageOf([member('1')], 3))
      post.mockResolvedValue({ data: { queued: 0, skipped: 3 }, response: new Response() })
      const { wrapper } = await mountView()
      await openBulkConfirm(wrapper)

      await button(wrapper, 'Send links')!.trigger('click')
      await flushPromises()

      expect(toastText()).toBe('No links sent · 3 skipped')
    })

    it('sends nothing when the confirmation is cancelled', async () => {
      get.mockResolvedValue(pageOf([member('1')]))
      const { wrapper } = await mountView()
      await openBulkConfirm(wrapper)

      await button(wrapper, 'Cancel')!.trigger('click')

      expect(post).not.toHaveBeenCalled()
      expect(dialogOpen(wrapper)).toHaveLength(0)
    })

    it('reports a failed bulk send', async () => {
      get.mockResolvedValue(pageOf([member('1')]))
      post.mockResolvedValue({ error: { status: 500, title: 'Err' }, response: new Response(null, { status: 500 }) })
      const { wrapper } = await mountView()
      await openBulkConfirm(wrapper)

      await button(wrapper, 'Send links')!.trigger('click')
      await flushPromises()

      expect(toastText()).toBe("Couldn't send the links. Try again.")
    })

    it('disables the bulk action when the group has no members', async () => {
      get.mockResolvedValue(pageOf([]))
      const { wrapper } = await mountView()

      await bulkMenu(wrapper).trigger('click')

      expect(bulkItem(wrapper).attributes('disabled')).toBeDefined()
      await bulkItem(wrapper).trigger('click')
      expect(dialogOpen(wrapper)).toHaveLength(0)
    })

    it('does not claim a count while a search narrows the list, because the send goes to everyone', async () => {
      get.mockResolvedValue(pageOf([member('1')], 1))
      const { wrapper } = await mountView('/members?q=ana')

      await openBulkConfirm(wrapper)

      const text = dialogOpen(wrapper)[0]!.text()
      expect(text).toContain('every member')
      expect(text).toContain('not just the search results')
      expect(text).not.toContain('1 member')
    })

    it('keeps the bulk action enabled when a search finds nobody but the group has members', async () => {
      get.mockResolvedValue(pageOf([]))
      const { wrapper } = await mountView('/members?q=zzz')

      await bulkMenu(wrapper).trigger('click')

      expect(bulkItem(wrapper).attributes('disabled')).toBeUndefined()
    })

    it('emails one member from the row menu and confirms with their name', async () => {
      get.mockResolvedValue(pageOf([member('1', { name: 'Ana' })]))
      post.mockResolvedValue({ data: { queuedAt: '2026-01-10T12:00:00Z' }, response: new Response() })
      const { wrapper } = await mountView()

      await chooseRowAction(wrapper, 'Ana', 'Email preferences link')

      expect(post).toHaveBeenCalledWith('/members/{id}/preferences-email', { params: { path: { id: '1' } } })
      expect(toastText()).toBe('Sending link to Ana')
      expect(wrapper.get('[data-email-status]').text()).toBe('Sending link…')
    })

    it('explains the cooldown when a link went out moments ago', async () => {
      get.mockResolvedValue(pageOf([member('1', { name: 'Ana' })]))
      post.mockResolvedValue({
        error: { status: 429, title: 'Too many', code: 'email.cooldown' },
        response: new Response(null, { status: 429 }),
      })
      const { wrapper } = await mountView()

      await chooseRowAction(wrapper, 'Ana', 'Email preferences link')

      expect(toastText()).toBe('A link was sent to Ana a moment ago. Try again in a few minutes.')
      expect(wrapper.find('[role=alert]').exists()).toBe(false)
    })

    it('reports a failed single send', async () => {
      get.mockResolvedValue(pageOf([member('1', { name: 'Ana' })]))
      post.mockResolvedValue({ error: { status: 500, title: 'Err' }, response: new Response(null, { status: 500 }) })
      const { wrapper } = await mountView()

      await chooseRowAction(wrapper, 'Ana', 'Email preferences link')

      expect(toastText()).toBe("Couldn't send the link to Ana. Try again.")
    })

    it('retries a failed delivery from the status line', async () => {
      get.mockResolvedValue(
        pageOf([member('1', { name: 'Ana', preferencesEmail: { status: 'FAILED', at: '2026-01-10T11:00:00Z' } })]),
      )
      post.mockResolvedValue({ data: { queuedAt: '2026-01-10T12:00:00Z' }, response: new Response() })
      const { wrapper } = await mountView()

      await wrapper.get('[data-email-status] button').trigger('click')
      await flushPromises()

      expect(post).toHaveBeenCalledWith('/members/{id}/preferences-email', { params: { path: { id: '1' } } })
      expect(toastText()).toBe('Sending link to Ana')
    })

    it('looks again when the window regains focus while a link is still sending', async () => {
      get.mockResolvedValue(
        pageOf([member('1', { preferencesEmail: { status: 'SENDING', at: '2026-01-10T11:59:00Z' } })]),
      )
      await mountView()
      get.mockClear()

      window.dispatchEvent(new Event('focus'))
      await flushPromises()

      expect(get).toHaveBeenCalledTimes(1)
    })

    it('does not refetch on focus when nothing is waiting on the mail server', async () => {
      get.mockResolvedValue(
        pageOf([member('1', { preferencesEmail: { status: 'SENT', at: '2026-01-10T11:59:00Z' } })]),
      )
      await mountView()
      get.mockClear()

      window.dispatchEvent(new Event('focus'))
      await flushPromises()

      expect(get).not.toHaveBeenCalled()
    })

    it('stops listening for focus once the view is gone', async () => {
      get.mockResolvedValue(
        pageOf([member('1', { preferencesEmail: { status: 'SENDING', at: '2026-01-10T11:59:00Z' } })]),
      )
      const { wrapper } = await mountView()
      wrapper.unmount()
      get.mockClear()

      window.dispatchEvent(new Event('focus'))
      await flushPromises()

      expect(get).not.toHaveBeenCalled()
    })
  })
})
