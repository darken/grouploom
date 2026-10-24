import { describe, expect, it } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

import type { EventItem } from '@/stores/events'
import EventList from '../EventList.vue'

function event(id: string, overrides: Partial<EventItem> = {}): EventItem {
  return {
    id,
    title: `Event ${id}`,
    location: 'Gym',
    start: '2026-11-02T09:00:00Z',
    end: '2026-11-02T11:00:00Z',
    localStart: '2026-11-02T10:00:00',
    localEnd: '2026-11-02T12:00:00',
    timeZone: 'Europe/Madrid',
    eventType: { id: 't1', name: 'Practice', color: 'green', archived: false },
    version: 0,
    goingCount: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

// 08:00 in Madrid on Mon 2 Nov 2026.
const NOW = new Date('2026-11-02T07:00:00Z')

async function mountList(items: EventItem[], now = NOW) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<i/>' } },
      { path: '/events/:id', component: { template: '<i/>' } },
    ],
  })
  await router.push('/')
  await router.isReady()
  const wrapper = mount(EventList, { props: { items, now }, global: { plugins: [router] } })
  return { wrapper, router }
}

describe('EventList', () => {
  it('renders one section per day with a header', async () => {
    const { wrapper } = await mountList([
      event('1'),
      event('2', { localStart: '2026-11-02T18:00:00', start: '2026-11-02T17:00:00Z', end: '2026-11-02T18:00:00Z' }),
      event('3', { localStart: '2026-11-03T09:00:00', start: '2026-11-03T08:00:00Z', end: '2026-11-03T09:00:00Z' }),
    ])

    const headers = wrapper.findAll('h2')
    expect(headers.map((h) => h.text())).toEqual(['Today', 'Tomorrow'])
    expect(wrapper.findAll('section')[0]!.findAll('li')).toHaveLength(2)
    expect(wrapper.findAll('section')[1]!.findAll('li')).toHaveLength(1)
  })

  it('shows the start time over the end time in the event zone', async () => {
    const { wrapper } = await mountList([event('1')])

    const times = wrapper.find('[data-times]').findAll('span')
    expect(times.map((t) => t.text())).toEqual(['10:00', '12:00'])
  })

  it('shows the title, type name and location', async () => {
    const { wrapper } = await mountList([event('1', { title: 'Rehearsal' })])

    const row = wrapper.find('li')
    expect(row.text()).toContain('Rehearsal')
    expect(row.text()).toContain('Practice')
    expect(row.text()).toContain('Gym')
  })

  it('leaves out the location separator when there is no location', async () => {
    const { wrapper } = await mountList([event('1', { location: undefined })])

    expect(wrapper.find('li').text()).not.toContain('·')
  })

  it('draws a vertical bar in the event type color', async () => {
    const { wrapper } = await mountList([
      event('1'),
      event('2', { eventType: { id: 't2', name: 'Board', color: 'red', archived: false } }),
    ])

    const bars = wrapper.findAll('[data-color-bar]')
    expect(bars[0]!.classes().join(' ')).toContain('bg-green-500')
    expect(bars[1]!.classes().join(' ')).toContain('bg-red-500')
  })

  it('shows a Now pill only for an event in progress', async () => {
    const inProgress = event('1', { start: '2026-11-02T06:00:00Z', end: '2026-11-02T08:00:00Z' })
    const later = event('2')
    const ended = event('3', { start: '2026-11-02T04:00:00Z', end: '2026-11-02T06:00:00Z' })
    const { wrapper } = await mountList([inProgress, later, ended])

    const rows = wrapper.findAll('li')
    expect(rows[0]!.text()).toContain('Now')
    expect(rows[1]!.text()).not.toContain('Now')
    expect(rows[2]!.text()).not.toContain('Now')
  })

  it('makes each row a link to the detail page, focusable by keyboard', async () => {
    const { wrapper, router } = await mountList([event('abc')])

    const link = wrapper.find('li a')
    expect(link.attributes('href')).toBe('/events/abc')
    expect(link.attributes('tabindex')).toBeUndefined()

    await link.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/events/abc')
  })

  it('passes the list query along so the detail page can link back', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', component: { template: '<i/>' } }, { path: '/events/:id', component: { template: '<i/>' } }],
    })
    await router.push('/')
    const wrapper = mount(EventList, {
      props: { items: [event('abc')], now: NOW, linkQuery: { when: 'past', page: '2' } },
      global: { plugins: [router] },
    })

    expect(wrapper.find('li a').attributes('href')).toBe('/events/abc?when=past&page=2')
  })

  it('shows a cancelled event with a struck-through title, "Cancelled" in red and a dimmed color bar', async () => {
    const { wrapper } = await mountList([event('1', { cancelledAt: '2026-10-20T08:00:00Z' }), event('2')])

    const [cancelled, normal] = wrapper.findAll('li')
    expect(cancelled!.find('[data-title]').classes()).toContain('line-through')
    expect(cancelled!.find('[data-cancelled]').text()).toBe('Cancelled')
    expect(cancelled!.find('[data-cancelled]').classes().join(' ')).toContain('danger')
    expect(cancelled!.find('[data-color-bar]').classes().join(' ')).toContain('opacity-')
    expect(normal!.find('[data-title]').classes()).not.toContain('line-through')
    expect(normal!.find('[data-cancelled]').exists()).toBe(false)
    expect(normal!.find('[data-color-bar]').classes().join(' ')).not.toContain('opacity-')
  })

  it('does not mark a cancelled event as "Now" even while it would be in progress', async () => {
    const { wrapper } = await mountList([event('1', { cancelledAt: '2026-10-20T08:00:00Z' })], new Date('2026-11-02T09:30:00Z'))

    expect(wrapper.text()).not.toContain('Now')
  })

  it('shows how many are going, but only when someone is', async () => {
    const { wrapper } = await mountList([event('1', { goingCount: 12 }), event('2', { goingCount: 0 })])

    const rows = wrapper.findAll('li')
    expect(rows[0]!.get('[data-going]').text()).toBe('12 going')
    expect(rows[0]!.get('[data-going]').classes().join(' ')).not.toMatch(/text-green-/)
    expect(rows[1]!.find('[data-going]').exists()).toBe(false)
    expect(rows[1]!.text()).not.toContain('going')
  })
})
