import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'

import { api } from '@/api/client'
import ToastHost from '@/components/ToastHost.vue'
import { useToast } from '@/composables/useToast'
import { installDialogPolyfill } from '@/components/__tests__/dialogPolyfill'
import EventTypesView from '../EventTypesView.vue'

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

function item(id: string, overrides: object = {}) {
  return {
    id, name: `Type ${id}`, description: undefined, color: 'blue', defaultDurationMinutes: undefined,
    archived: false, version: 0, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z', ...overrides,
  }
}

function pageOf(items: object[], total = items.length, number = 0) {
  return {
    data: { content: items, page: { number, size: 20, totalElements: total, totalPages: Math.max(1, Math.ceil(total / 20)) } },
    response: new Response(),
  }
}

async function mountView(url = '/event-types') {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/event-types', component: EventTypesView }] })
  await router.push(url)
  await router.isReady()
  const wrapper = mount({ components: { EventTypesView, ToastHost }, template: '<EventTypesView /><ToastHost />' }, {
    global: { plugins: [pinia, router] },
    attachTo: document.body,
  })
  await flushPromises()
  return { wrapper, router }
}

const dialogOpen = (w: Awaited<ReturnType<typeof mountView>>['wrapper']) => w.findAll('dialog').filter((d) => d.attributes('open') !== undefined)

describe('EventTypesView', () => {
  beforeEach(() => {
    for (const m of [get, post, put, del]) m.mockReset()
    useToast().dismiss()
    document.body.innerHTML = ''
  })

  it('renders the title, count and one row per event type', async () => {
    get.mockResolvedValue(pageOf([item('1'), item('2')], 2))

    const { wrapper } = await mountView()

    expect(wrapper.find('h1').text()).toBe('Event types')
    expect(wrapper.text()).toContain('2')
    expect(wrapper.findAll('li')).toHaveLength(2)
    expect(get).toHaveBeenCalledWith('/event-types', expect.objectContaining({ params: { query: expect.objectContaining({ archived: false, page: 0 }) } }))
  })

  it('shows skeleton rows during the first load', async () => {
    let resolve!: (v: unknown) => void
    get.mockReturnValue(new Promise((r) => (resolve = r)))
    const { wrapper } = await mountView()

    expect(wrapper.findAll('[data-skeleton]')).toHaveLength(3)

    resolve(pageOf([item('1')]))
    await flushPromises()
    expect(wrapper.findAll('[data-skeleton]')).toHaveLength(0)
  })

  it('shows the empty state with a create call to action for active types', async () => {
    get.mockResolvedValue(pageOf([]))

    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('No event types yet')
    const cta = wrapper.findAll('button').find((b) => b.text() === 'Create event type')
    expect(cta).toBeDefined()
    await cta!.trigger('click')
    expect(dialogOpen(wrapper)).toHaveLength(1)
  })

  it('shows a plain empty message in the archived view', async () => {
    get.mockResolvedValue(pageOf([]))

    const { wrapper } = await mountView('/event-types?archived=1')

    expect(wrapper.text()).toContain('No archived event types.')
    expect(get).toHaveBeenCalledWith('/event-types', expect.objectContaining({ params: { query: expect.objectContaining({ archived: true }) } }))
  })

  it('opens the create dialog from "New event type"', async () => {
    get.mockResolvedValue(pageOf([item('1')]))
    const { wrapper } = await mountView()

    await wrapper.findAll('button').find((b) => b.text() === 'New event type')!.trigger('click')

    expect(dialogOpen(wrapper)).toHaveLength(1)
    expect(dialogOpen(wrapper)[0]!.find('h2').text()).toBe('New event type')
  })

  it('creates an event type, closes the dialog and confirms with a toast', async () => {
    get.mockResolvedValue(pageOf([item('1')]))
    post.mockResolvedValue({ data: item('2'), response: new Response() })
    const { wrapper } = await mountView()
    await wrapper.findAll('button').find((b) => b.text() === 'New event type')!.trigger('click')

    await wrapper.find('dialog input[name=name]').setValue('Practice')
    await wrapper.find('dialog form').trigger('submit')
    await flushPromises()

    expect(post).toHaveBeenCalledWith('/event-types', { body: { name: 'Practice', color: 'blue' } })
    expect(dialogOpen(wrapper)).toHaveLength(0)
    expect(wrapper.find('[aria-live=polite]').text()).toContain('Event type created')
  })

  it('shows a name-taken conflict under the name field and keeps the dialog open', async () => {
    get.mockResolvedValue(pageOf([item('1')]))
    post.mockResolvedValue({
      error: { status: 409, code: 'event-type.name-taken', field: 'name', detail: 'An event type with this name already exists.' },
      response: new Response(null, { status: 409 }),
    })
    const { wrapper } = await mountView()
    await wrapper.findAll('button').find((b) => b.text() === 'New event type')!.trigger('click')

    await wrapper.find('dialog input[name=name]').setValue('Type 1')
    await wrapper.find('dialog form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('dialog').text()).toContain('An event type with this name already exists.')
    expect(dialogOpen(wrapper)).toHaveLength(1)
  })

  it('opens the edit dialog prefilled when a row is clicked and saves with the version', async () => {
    get.mockResolvedValue(pageOf([item('1', { name: 'Practice', version: 5 })]))
    put.mockResolvedValue({ data: item('1', { name: 'Rehearsal', version: 6 }), response: new Response() })
    const { wrapper } = await mountView()

    await wrapper.find('button[data-row]').trigger('click')
    expect(dialogOpen(wrapper)[0]!.find('h2').text()).toBe('Edit event type')
    await wrapper.find('dialog input[name=name]').setValue('Rehearsal')
    await wrapper.find('dialog form').trigger('submit')
    await flushPromises()

    expect(put).toHaveBeenCalledWith('/event-types/{id}', {
      params: { path: { id: '1' } },
      body: expect.objectContaining({ name: 'Rehearsal', archived: false, version: 5 }),
    })
  })

  it('tells the user when someone else changed the type and offers to reload', async () => {
    get.mockResolvedValue(pageOf([item('1')]))
    put.mockResolvedValue({ error: { status: 409, code: 'event-type.stale' }, response: new Response(null, { status: 409 }) })
    const { wrapper } = await mountView()
    await wrapper.find('button[data-row]').trigger('click')
    await wrapper.find('dialog input[name=name]').setValue('Other')
    await wrapper.find('dialog form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('dialog').text()).toContain('Changed by someone else')
    get.mockClear()
    await wrapper.find('dialog').findAll('button').find((b) => b.text() === 'Reload')!.trigger('click')
    await flushPromises()

    expect(get).toHaveBeenCalledOnce()
    expect(dialogOpen(wrapper)).toHaveLength(0)
  })

  it('archives from the menu without confirmation and offers Undo', async () => {
    get.mockResolvedValue(pageOf([item('1', { version: 2 })]))
    put.mockResolvedValueOnce({ data: item('1', { archived: true, version: 3 }), response: new Response() })
    const { wrapper } = await mountView()

    await wrapper.find('button[aria-label^="Actions for"]').trigger('click')
    await wrapper.findAll('[role=menuitem]').find((m) => m.text() === 'Archive')!.trigger('click')
    await flushPromises()

    expect(put).toHaveBeenCalledWith('/event-types/{id}', expect.objectContaining({ body: expect.objectContaining({ archived: true, version: 2 }) }))
    expect(wrapper.findAll('li')).toHaveLength(0)
    expect(wrapper.find('[aria-live=polite]').text()).toContain('Archived')

    put.mockResolvedValueOnce({ data: item('1', { archived: false, version: 4 }), response: new Response() })
    await wrapper.find('[aria-live=polite] button').trigger('click')
    await flushPromises()

    expect(put).toHaveBeenLastCalledWith('/event-types/{id}', expect.objectContaining({ body: expect.objectContaining({ archived: false, version: 3 }) }))
  })

  it('asks for confirmation before deleting', async () => {
    get.mockResolvedValue(pageOf([item('1', { name: 'Practice' })]))
    del.mockResolvedValue({ response: new Response(null, { status: 204 }) })
    const { wrapper } = await mountView()

    await wrapper.find('button[aria-label="Actions for Practice"]').trigger('click')
    await wrapper.findAll('[role=menuitem]').find((m) => m.text() === 'Delete')!.trigger('click')

    expect(del).not.toHaveBeenCalled()
    expect(dialogOpen(wrapper)[0]!.text()).toContain("Delete 'Practice'?")

    get.mockResolvedValue(pageOf([]))
    await dialogOpen(wrapper)[0]!.findAll('button').find((b) => b.text() === 'Delete')!.trigger('click')
    await flushPromises()

    expect(del).toHaveBeenCalledWith('/event-types/{id}', { params: { path: { id: '1' } } })
    expect(wrapper.text()).toContain('No event types yet')
  })

  it('does not delete when the confirmation is cancelled', async () => {
    get.mockResolvedValue(pageOf([item('1', { name: 'Practice' })]))
    const { wrapper } = await mountView()
    await wrapper.find('button[aria-label="Actions for Practice"]').trigger('click')
    await wrapper.findAll('[role=menuitem]').find((m) => m.text() === 'Delete')!.trigger('click')

    await dialogOpen(wrapper)[0]!.findAll('button').find((b) => b.text() === 'Cancel')!.trigger('click')

    expect(del).not.toHaveBeenCalled()
    expect(dialogOpen(wrapper)).toHaveLength(0)
  })

  it('switching the segmented control updates the URL query and refetches', async () => {
    get.mockResolvedValue(pageOf([item('1')]))
    const { wrapper, router } = await mountView('/event-types?page=3')
    get.mockClear()

    await wrapper.findAll('[role=radio]').find((r) => r.text() === 'Archived')!.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query).toEqual({ archived: '1' })
    expect(get).toHaveBeenCalledWith('/event-types', expect.objectContaining({ params: { query: expect.objectContaining({ archived: true, page: 0 }) } }))
  })

  it('reads the page from the URL (1-based) and paginates through it', async () => {
    get.mockResolvedValue(pageOf([item('21')], 43, 1))
    const { wrapper, router } = await mountView('/event-types?page=2')

    expect(get).toHaveBeenCalledWith('/event-types', expect.objectContaining({ params: { query: expect.objectContaining({ page: 1 }) } }))
    get.mockClear()
    await wrapper.findAll('button').find((b) => b.text() === 'Next')!.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query.page).toBe('3')
  })

  it('shows an error banner with Retry when loading fails', async () => {
    get.mockResolvedValueOnce({ error: { status: 500, title: 'Internal Server Error' }, response: new Response(null, { status: 500 }) })
    const { wrapper } = await mountView()
    expect(wrapper.find('[role=alert]').text()).toContain('Internal Server Error')

    get.mockResolvedValueOnce(pageOf([item('1')]))
    await wrapper.find('[role=alert]').findAll('button').find((b) => b.text() === 'Retry')!.trigger('click')
    await flushPromises()

    expect(wrapper.find('[role=alert]').exists()).toBe(false)
    expect(wrapper.findAll('li')).toHaveLength(1)
  })

  it('switches the delete dialog to "Archive instead" when the type is in use, and archives on confirm', async () => {
    get.mockResolvedValue(pageOf([item('1', { name: 'Practice', version: 2 })]))
    del.mockResolvedValue({
      error: { status: 409, code: 'event-type.in-use', detail: 'This event type is used by events. Archive it instead.' },
      response: new Response(null, { status: 409 }),
    })
    put.mockResolvedValue({ data: item('1', { name: 'Practice', archived: true, version: 3 }), response: new Response() })
    const { wrapper } = await mountView()
    await wrapper.find('button[aria-label="Actions for Practice"]').trigger('click')
    await wrapper.findAll('[role=menuitem]').find((m) => m.text() === 'Delete')!.trigger('click')

    await dialogOpen(wrapper)[0]!.findAll('button').find((b) => b.text() === 'Delete')!.trigger('click')
    await flushPromises()

    const archiveDialog = dialogOpen(wrapper)[0]!
    expect(archiveDialog.text()).toContain('This event type is used by events. Archive it instead?')
    expect(wrapper.find('[role=alert]').exists()).toBe(false)
    expect(put).not.toHaveBeenCalled()

    await archiveDialog.findAll('button').find((b) => b.text() === 'Archive')!.trigger('click')
    await flushPromises()

    expect(put).toHaveBeenCalledWith('/event-types/{id}', expect.objectContaining({ body: expect.objectContaining({ archived: true, version: 2 }) }))
    expect(dialogOpen(wrapper)).toHaveLength(0)
    expect(wrapper.find('[aria-live=polite]').text()).toContain('Archived')
  })

  it('lets the user cancel the "Archive instead" offer without archiving', async () => {
    get.mockResolvedValue(pageOf([item('1', { name: 'Practice' })]))
    del.mockResolvedValue({ error: { status: 409, code: 'event-type.in-use' }, response: new Response(null, { status: 409 }) })
    const { wrapper } = await mountView()
    await wrapper.find('button[aria-label="Actions for Practice"]').trigger('click')
    await wrapper.findAll('[role=menuitem]').find((m) => m.text() === 'Delete')!.trigger('click')
    await dialogOpen(wrapper)[0]!.findAll('button').find((b) => b.text() === 'Delete')!.trigger('click')
    await flushPromises()

    await dialogOpen(wrapper)[0]!.findAll('button').find((b) => b.text() === 'Cancel')!.trigger('click')

    expect(put).not.toHaveBeenCalled()
    expect(dialogOpen(wrapper)).toHaveLength(0)
  })

  it('still shows the error banner for other delete failures', async () => {
    get.mockResolvedValue(pageOf([item('1', { name: 'Practice' })]))
    del.mockResolvedValue({ error: { status: 500, title: 'Server error' }, response: new Response(null, { status: 500 }) })
    const { wrapper } = await mountView()
    await wrapper.find('button[aria-label="Actions for Practice"]').trigger('click')
    await wrapper.findAll('[role=menuitem]').find((m) => m.text() === 'Delete')!.trigger('click')

    await dialogOpen(wrapper)[0]!.findAll('button').find((b) => b.text() === 'Delete')!.trigger('click')
    await flushPromises()

    expect(wrapper.find('[role=alert]').exists()).toBe(true)
  })
})
