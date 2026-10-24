import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'

import { api } from '@/api/client'
import MemberAttendanceView from '../MemberAttendanceView.vue'

vi.mock('@/api/client', () => ({
  api: { GET: vi.fn<() => Promise<unknown>>(), PUT: vi.fn<() => Promise<unknown>>() },
}))

const get = vi.mocked(api.GET) as unknown as ReturnType<typeof vi.fn>
const put = vi.mocked(api.PUT) as unknown as ReturnType<typeof vi.fn>

function reply(overrides: object = {}, event: object = {}) {
  return {
    groupName: 'Chess Club',
    memberName: 'Ana',
    event: {
      title: 'Practice night',
      location: 'Main hall',
      start: '2026-11-07T09:00:00Z',
      end: '2026-11-07T11:00:00Z',
      localStart: '2026-11-07T10:00:00',
      localEnd: '2026-11-07T12:00:00',
      timeZone: 'Europe/Madrid',
      typeName: 'Practice',
      typeColor: 'green',
      cancelled: false,
      ...event,
    },
    open: true,
    ...overrides,
  }
}

const ok = (data: object) => ({ data, response: new Response() })
const problem = (status: number, code?: string) => ({
  error: { status, title: 'Err', ...(code ? { code } : {}) },
  response: new Response(null, { status }),
})

async function mountView(url = '/m/attend#t=abc&r=going') {
  const pinia = createPinia()
  setActivePinia(pinia)
  window.history.replaceState(null, '', url)
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/m/attend', component: MemberAttendanceView }] })
  await router.push(url)
  await router.isReady()
  const wrapper = mount(MemberAttendanceView, { global: { plugins: [pinia, router] }, attachTo: document.body })
  await flushPromises()
  return { wrapper, router }
}

type Wrapper = Awaited<ReturnType<typeof mountView>>['wrapper']
const radios = (w: Wrapper) => w.findAll('[role=radio]')
const button = (w: Wrapper, label: string | RegExp) =>
  w.findAll('button').find((b) => (typeof label === 'string' ? b.text() === label : label.test(b.text())))!
const sendButton = (w: Wrapper) => button(w, /^(Send|Update) reply$/)

describe('MemberAttendanceView', () => {
  beforeEach(() => {
    get.mockReset()
    put.mockReset()
    sessionStorage.clear()
    document.body.innerHTML = ''
  })

  describe('the form', () => {
    it('greets the member, shows the event and asks one question', async () => {
      get.mockResolvedValue(ok(reply()))

      const { wrapper } = await mountView()

      expect(wrapper.get('h1').text()).toBe('Hi Ana')
      expect(wrapper.text()).toContain('Practice night')
      expect(wrapper.text()).toContain('Main hall')
      expect(wrapper.text()).toContain('Will you come?')
      expect(get).toHaveBeenCalledWith('/public/attendance', { params: { header: { 'X-Member-Token': 'abc' } } })
    })

    it('preselects the choice from the link but sends nothing until Send reply is tapped', async () => {
      get.mockResolvedValue(ok(reply()))

      const { wrapper } = await mountView('/m/attend#t=abc&r=not-going')

      expect(radios(wrapper).map((r) => r.attributes('aria-checked'))).toEqual(['false', 'true'])
      expect(put).not.toHaveBeenCalled()
    })

    it('removes the token from the address bar', async () => {
      get.mockResolvedValue(ok(reply()))

      await mountView('/m/attend#t=abc&r=going')

      expect(window.location.hash).toBe('')
    })

    it('keeps Send reply disabled until a choice is made', async () => {
      get.mockResolvedValue(ok(reply()))

      const { wrapper } = await mountView('/m/attend#t=abc')
      expect(sendButton(wrapper).attributes('disabled')).toBeDefined()

      await radios(wrapper)[0]!.trigger('click')
      expect(sendButton(wrapper).attributes('disabled')).toBeUndefined()
    })

    it('says "Update reply" for a member who already answered, and preselects that answer', async () => {
      get.mockResolvedValue(ok(reply({ response: 'NOT_GOING', respondedAt: '2026-11-01T10:00:00Z' })))

      const { wrapper } = await mountView('/m/attend#t=abc')

      expect(sendButton(wrapper).text()).toBe('Update reply')
      expect(radios(wrapper)[1]!.attributes('aria-checked')).toBe('true')
    })

    it('explains that the answer can be changed until the event starts', async () => {
      get.mockResolvedValue(ok(reply()))

      const { wrapper } = await mountView()

      expect(wrapper.text()).toContain('You can change your answer until the event starts.')
    })

    it('keeps the note collapsed behind "+ Add a note" until it is tapped', async () => {
      get.mockResolvedValue(ok(reply()))
      const { wrapper } = await mountView()
      expect(wrapper.find('textarea').exists()).toBe(false)

      await button(wrapper, '+ Add a note').trigger('click')

      expect(wrapper.get('textarea').attributes('maxlength')).toBe('280')
      expect(wrapper.text()).toContain('Add a note for the organizer (optional)')
      expect(wrapper.find('textarea').element).toBe(document.activeElement)
    })

    it('shows the note already, and its counter near the limit, for a member who left one', async () => {
      get.mockResolvedValue(ok(reply({ response: 'GOING', note: 'x'.repeat(250) })))

      const { wrapper } = await mountView('/m/attend#t=abc')

      expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toHaveLength(250)
      expect(wrapper.text()).toContain('250/280')
    })

    it('sends the choice and the note when Send reply is tapped', async () => {
      get.mockResolvedValue(ok(reply()))
      put.mockResolvedValue(ok(reply({ response: 'GOING', note: 'Bringing snacks', respondedAt: '2026-11-01T10:00:00Z' })))
      const { wrapper } = await mountView()
      await button(wrapper, '+ Add a note').trigger('click')
      await wrapper.get('textarea').setValue('Bringing snacks')

      await sendButton(wrapper).trigger('click')
      await flushPromises()

      expect(put).toHaveBeenCalledTimes(1)
      expect(put).toHaveBeenCalledWith('/public/attendance', {
        params: { header: { 'X-Member-Token': 'abc' } },
        body: { response: 'GOING', note: 'Bringing snacks' },
      })
    })

    it('keeps the form and says so when sending fails', async () => {
      get.mockResolvedValue(ok(reply()))
      put.mockResolvedValue(problem(500))
      const { wrapper } = await mountView()

      await sendButton(wrapper).trigger('click')
      await flushPromises()

      expect(wrapper.get('[role=alert]').text()).toContain("Couldn't send your reply")
      expect(sendButton(wrapper).exists()).toBe(true)
    })
  })

  describe('after sending', () => {
    async function sent(response: 'GOING' | 'NOT_GOING', hash: string) {
      get.mockResolvedValue(ok(reply()))
      put.mockResolvedValue(ok(reply({ response, respondedAt: '2026-11-01T10:00:00Z' })))
      const view = await mountView(hash)
      await sendButton(view.wrapper).trigger('click')
      await flushPromises()
      return view
    }

    it("confirms a Going reply by name in place of the form", async () => {
      const { wrapper } = await sent('GOING', '/m/attend#t=abc&r=going')

      expect(wrapper.get('[role=status]').text()).toBe("Thanks, Ana. You're going.")
      expect(wrapper.find('[role=radiogroup]').exists()).toBe(false)
      expect(wrapper.find('textarea').exists()).toBe(false)
    })

    it('confirms a Can\'t go reply calmly', async () => {
      const { wrapper } = await sent('NOT_GOING', '/m/attend#t=abc&r=not-going')

      expect(wrapper.get('[role=status]').text()).toBe('Thanks for letting us know.')
    })

    it('brings the form back with "Change answer"', async () => {
      const { wrapper } = await sent('GOING', '/m/attend#t=abc&r=going')

      await button(wrapper, 'Change answer').trigger('click')

      expect(wrapper.find('[role=radiogroup]').exists()).toBe(true)
      expect(radios(wrapper)[0]!.attributes('aria-checked')).toBe('true')
      expect(sendButton(wrapper).text()).toBe('Update reply')
    })
  })

  describe('when replies are closed', () => {
    it('says so for a cancelled event, with the organizer message, and offers no form', async () => {
      get.mockResolvedValue(ok(reply(
        { open: false },
        { cancelled: true, cancellationMessage: 'The gym is closed' },
      )))

      const { wrapper } = await mountView()

      expect(wrapper.get('[data-closed]').text()).toContain('This event was cancelled.')
      expect(wrapper.get('[data-closed]').text()).toContain('The gym is closed')
      expect(wrapper.find('[role=radiogroup]').exists()).toBe(false)
      expect(wrapper.find('[data-event-card] h2').classes()).toContain('line-through')
    })

    it('says that replies are closed once the event has started and shows the last answer read-only', async () => {
      get.mockResolvedValue(ok(reply({ open: false, response: 'GOING' })))

      const { wrapper } = await mountView()

      expect(wrapper.get('[data-closed]').text()).toContain('This event has started. Replies are closed.')
      expect(wrapper.get('[data-current-answer]').text()).toBe('Your reply: Going')
      expect(wrapper.find('[role=radiogroup]').exists()).toBe(false)
      expect(wrapper.find('button').exists()).toBe(false)
    })

    it('shows no answer line for a member who never replied', async () => {
      get.mockResolvedValue(ok(reply({ open: false })))

      const { wrapper } = await mountView()

      expect(wrapper.find('[data-current-answer]').exists()).toBe(false)
    })

    it('switches to the closed view when sending is refused because the event just started', async () => {
      get.mockResolvedValueOnce(ok(reply()))
      put.mockResolvedValue(problem(409, 'attendance.closed'))
      const { wrapper } = await mountView()
      get.mockResolvedValue(ok(reply({ open: false })))

      await sendButton(wrapper).trigger('click')
      await flushPromises()

      expect(wrapper.get('[data-closed]').text()).toContain('Replies are closed')
      expect(wrapper.find('[role=radiogroup]').exists()).toBe(false)
    })
  })

  describe('links that do not work', () => {
    it('explains an invalid link', async () => {
      get.mockResolvedValue(problem(401, 'member-token.invalid'))

      const { wrapper } = await mountView()

      expect(wrapper.text()).toContain("This link isn't valid.")
    })

    it('explains an expired link', async () => {
      get.mockResolvedValue(problem(401, 'member-token.expired'))

      const { wrapper } = await mountView()

      expect(wrapper.text()).toContain('This link has expired.')
    })

    it('treats a page opened without any token as an invalid link', async () => {
      const { wrapper } = await mountView('/m/attend')

      expect(wrapper.text()).toContain("This link isn't valid.")
      expect(get).not.toHaveBeenCalled()
    })

    it('offers a retry when the page could not be loaded', async () => {
      get.mockResolvedValueOnce(problem(500))
      const { wrapper } = await mountView()
      get.mockResolvedValue(ok(reply()))

      await button(wrapper, 'Retry').trigger('click')
      await flushPromises()

      expect(wrapper.get('h1').text()).toBe('Hi Ana')
    })
  })

  it('shows a skeleton of the card while loading', async () => {
    get.mockReturnValue(new Promise(() => undefined))

    const { wrapper } = await mountView()

    expect(wrapper.find('[aria-busy=true]').exists()).toBe(true)
    expect(wrapper.findAll('[data-skeleton]').length).toBeGreaterThan(0)
  })

  it('switches to another link opened in the same tab', async () => {
    get.mockResolvedValue(ok(reply()))
    const { wrapper, router } = await mountView('/m/attend#t=first&r=going')
    get.mockResolvedValue(ok(reply({ memberName: 'Bob' })))

    await router.push('/m/attend#t=second&r=not-going')
    await flushPromises()

    expect(get).toHaveBeenLastCalledWith('/public/attendance', { params: { header: { 'X-Member-Token': 'second' } } })
    expect(wrapper.get('h1').text()).toBe('Hi Bob')
    expect(radios(wrapper)[1]!.attributes('aria-checked')).toBe('true')
  })
})
