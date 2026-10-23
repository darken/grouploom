import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'

import { api } from '@/api/client'
import MemberPreferencesView from '../MemberPreferencesView.vue'

vi.mock('@/api/client', () => ({
  api: { GET: vi.fn<() => Promise<unknown>>(), PUT: vi.fn<() => Promise<unknown>>() },
}))

const get = vi.mocked(api.GET) as unknown as ReturnType<typeof vi.fn>
const put = vi.mocked(api.PUT) as unknown as ReturnType<typeof vi.fn>

function prefs(overrides: object = {}) {
  return {
    memberName: 'Ana',
    maskedEmail: 'a••@example.com',
    groupName: 'Chess Club',
    invitationsPaused: false,
    eventTypes: [
      { id: 't1', name: 'Practice', description: 'Tuesdays at the club', color: 'blue', subscribed: true },
      { id: 't2', name: 'Game night', color: 'green', subscribed: false },
    ],
    ...overrides,
  }
}

const ok = (data: object) => ({ data, response: new Response() })
const problem = (status: number, code?: string) => ({
  error: { status, title: 'Err', ...(code ? { code } : {}) },
  response: new Response(null, { status }),
})

async function mountView(url = '/m/preferences#t=abc') {
  const pinia = createPinia()
  setActivePinia(pinia)
  window.history.replaceState(null, '', url)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/m/preferences', component: MemberPreferencesView }],
  })
  await router.push(url)
  await router.isReady()
  const wrapper = mount(MemberPreferencesView, { global: { plugins: [pinia, router] }, attachTo: document.body })
  await flushPromises()
  return wrapper
}

type Wrapper = Awaited<ReturnType<typeof mountView>>
const switches = (w: Wrapper) => w.findAll('[role=switch]')

describe('MemberPreferencesView', () => {
  beforeEach(() => {
    get.mockReset()
    put.mockReset()
    sessionStorage.clear()
    document.body.innerHTML = ''
  })

  it('greets the member and shows where emails go, with the masked address only', async () => {
    get.mockResolvedValue(ok(prefs()))

    const wrapper = await mountView()

    expect(wrapper.get('h1').text()).toBe('Email preferences')
    expect(wrapper.text()).toContain("Hi Ana — choose which Chess Club events you'd like to hear about.")
    expect(wrapper.text()).toContain('Sending to a••@example.com')
    expect(get).toHaveBeenCalledWith('/public/preferences', { params: { header: { 'X-Member-Token': 'abc' } } })
  })

  it('removes the token from the address bar', async () => {
    get.mockResolvedValue(ok(prefs()))

    await mountView('/m/preferences#t=abc')

    expect(window.location.hash).toBe('')
  })

  it('switches to another link opened in the same tab', async () => {
    get.mockResolvedValue(ok(prefs()))
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/m/preferences', component: MemberPreferencesView }],
    })
    await router.push('/m/preferences#t=first')
    const wrapper = mount(MemberPreferencesView, { global: { plugins: [pinia, router] }, attachTo: document.body })
    await flushPromises()
    get.mockResolvedValue(ok(prefs({ memberName: 'Bob' })))

    await router.push('/m/preferences#t=second')
    await flushPromises()

    expect(get).toHaveBeenLastCalledWith('/public/preferences', { params: { header: { 'X-Member-Token': 'second' } } })
    expect(wrapper.text()).toContain('Hi Bob')
  })

  it('lists one row per event type with its current choice, then the pause row', async () => {
    get.mockResolvedValue(ok(prefs()))

    const wrapper = await mountView()

    expect(wrapper.text()).toContain('Event types')
    expect(wrapper.text()).toContain('All invitations')
    const rows = wrapper.findAll('li')
    expect(rows.map((r) => r.get('[role=switch]').attributes('aria-checked'))).toEqual(['true', 'false', 'false'])
    expect(rows[0]!.text()).toContain('Practice')
    expect(rows[0]!.text()).toContain('Tuesdays at the club')
    expect(rows[1]!.text()).toContain('Game night')
    expect(rows[2]!.text()).toContain('Pause all invitations')
    expect(wrapper.text()).toContain("You won't get invitations from Chess Club until you turn this off.")
    expect(wrapper.text()).not.toContain('Invitations are paused')
  })

  it('shows skeleton rows while loading', async () => {
    get.mockReturnValue(new Promise(() => {}))

    const wrapper = await mountView()

    expect(wrapper.get('[aria-busy=true]').findAll('[data-skeleton]').length).toBeGreaterThanOrEqual(3)
    expect(switches(wrapper)).toHaveLength(0)
  })

  it('saves immediately when a row is toggled, showing Saving… and then Saved', async () => {
    get.mockResolvedValue(ok(prefs()))
    let finish!: (value: unknown) => void
    put.mockReturnValue(new Promise((r) => (finish = r)))
    const wrapper = await mountView()

    await wrapper.findAll('li')[0]!.get('[data-name]').trigger('click')

    expect(switches(wrapper)[0]!.attributes('aria-checked')).toBe('false')
    expect(wrapper.get('[aria-live=polite]').text()).toBe('Saving…')
    expect(put).toHaveBeenCalledWith('/public/preferences', {
      params: { header: { 'X-Member-Token': 'abc' } },
      body: {
        invitationsPaused: false,
        eventTypes: [
          { id: 't1', subscribed: false },
          { id: 't2', subscribed: false },
        ],
      },
    })
    finish(ok(prefs({ eventTypes: [{ id: 't1', name: 'Practice', color: 'blue', subscribed: false }, { id: 't2', name: 'Game night', color: 'green', subscribed: false }] })))
    await flushPromises()
    expect(wrapper.get('[aria-live=polite]').text()).toBe('Saved')
    expect(wrapper.find('button[type=submit]').exists()).toBe(false)
  })

  it('dims and disables the event type rows when invitations are paused, keeping the choices', async () => {
    get.mockResolvedValue(ok(prefs()))
    put.mockResolvedValue(ok(prefs({ invitationsPaused: true })))
    const wrapper = await mountView()

    await wrapper.findAll('li')[2]!.get('[role=switch]').trigger('click')
    await flushPromises()

    const rows = wrapper.findAll('li')
    expect(rows[2]!.get('[role=switch]').attributes('aria-checked')).toBe('true')
    for (const row of rows.slice(0, 2)) {
      expect(row.classes()).toContain('opacity-50')
      expect(row.get('[role=switch]').attributes('disabled')).toBeDefined()
    }
    expect(rows[0]!.get('[role=switch]').attributes('aria-checked')).toBe('true')
    expect(rows[2]!.get('[role=switch]').attributes('disabled')).toBeUndefined()
    expect(wrapper.text()).toContain('Invitations are paused. Your choices below are kept.')
  })

  it('shows a retry when saving fails and puts the switch back', async () => {
    get.mockResolvedValue(ok(prefs()))
    put.mockResolvedValue(problem(500))
    const wrapper = await mountView()

    await wrapper.findAll('li')[0]!.get('[role=switch]').trigger('click')
    await flushPromises()

    expect(switches(wrapper)[0]!.attributes('aria-checked')).toBe('true')
    expect(wrapper.get('[aria-live=polite]').text()).toBe("Couldn't save. Try again.")
    put.mockResolvedValue(ok(prefs({ eventTypes: [{ id: 't1', name: 'Practice', color: 'blue', subscribed: false }, { id: 't2', name: 'Game night', color: 'green', subscribed: false }] })))
    await wrapper.get('[aria-live=polite] button').trigger('click')
    await flushPromises()
    expect(switches(wrapper)[0]!.attributes('aria-checked')).toBe('false')
  })

  it('explains that the group has no event types yet, and still offers the pause switch', async () => {
    get.mockResolvedValue(ok(prefs({ eventTypes: [] })))

    const wrapper = await mountView()

    expect(wrapper.text()).toContain("Chess Club hasn't set up any event types yet.")
    expect(switches(wrapper)).toHaveLength(1)
    expect(wrapper.text()).toContain('Pause all invitations')
  })

  it('shows the invalid-link message and nothing else for a bad link', async () => {
    get.mockResolvedValue(problem(401, 'member-token.invalid'))

    const wrapper = await mountView()

    expect(wrapper.text()).toContain(
      "This link isn't valid. Use the latest link from your email, or ask your organizer for a new one.",
    )
    expect(switches(wrapper)).toHaveLength(0)
    expect(wrapper.text()).not.toContain('Sending to')
  })

  it('shows the invalid-link message when there is no token at all', async () => {
    const wrapper = await mountView('/m/preferences')

    expect(wrapper.text()).toContain("This link isn't valid.")
    expect(get).not.toHaveBeenCalled()
  })

  it('shows the expired-link message', async () => {
    get.mockResolvedValue(problem(401, 'member-token.expired'))

    const wrapper = await mountView()

    expect(wrapper.text()).toContain('This link has expired. Ask your organizer for a new one.')
    expect(wrapper.text()).not.toContain("isn't valid")
  })

  it('shows an error banner with Retry when loading fails', async () => {
    get.mockResolvedValueOnce(problem(500))
    const wrapper = await mountView()

    expect(wrapper.find('[role=alert]').exists()).toBe(true)
    get.mockResolvedValue(ok(prefs()))
    await wrapper.findAll('button').find((b) => b.text() === 'Retry')!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Hi Ana')
  })
})
