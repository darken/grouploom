import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import type { EventType } from '@/stores/eventTypes'
import EventTypeList from '../EventTypeList.vue'

function item(overrides: Partial<EventType> = {}): EventType {
  return {
    id: '1',
    name: 'Practice',
    description: 'Every Tuesday',
    color: 'green',
    defaultDurationMinutes: 90,
    archived: false,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

describe('EventTypeList', () => {
  it('renders each row with name, description, duration and a color dot', () => {
    const wrapper = mount(EventTypeList, { props: { items: [item(), item({ id: '2', name: 'Board', description: undefined, defaultDurationMinutes: undefined, color: 'red' })] } })

    const rows = wrapper.findAll('li')
    expect(rows).toHaveLength(2)
    expect(rows[0]!.text()).toContain('Practice')
    expect(rows[0]!.text()).toContain('Every Tuesday')
    expect(rows[0]!.text()).toContain('1.5 hr')
    expect(rows[0]!.find('[data-color-dot]').classes().join(' ')).toContain('bg-green-500')
    expect(rows[1]!.text()).toContain('Board')
    expect(rows[1]!.find('[data-color-dot]').classes().join(' ')).toContain('bg-red-500')
  })

  it('emits edit when a row is clicked', async () => {
    const first = item()
    const wrapper = mount(EventTypeList, { props: { items: [first] } })

    await wrapper.find('button[data-row]').trigger('click')

    expect(wrapper.emitted('edit')?.[0]).toEqual([first])
  })

  it('opens an actions menu with Edit, Archive and Delete for an active type', async () => {
    const first = item()
    const wrapper = mount(EventTypeList, { props: { items: [first] } })
    expect(wrapper.find('[role=menu]').exists()).toBe(false)

    await wrapper.find('button[aria-label="Actions for Practice"]').trigger('click')

    const labels = wrapper.findAll('[role=menuitem]').map((m) => m.text())
    expect(labels).toEqual(['Edit', 'Archive', 'Delete'])

    await wrapper.findAll('[role=menuitem]')[1]!.trigger('click')
    expect(wrapper.emitted('archive')?.[0]).toEqual([first])
    expect(wrapper.find('[role=menu]').exists()).toBe(false)
  })

  it('offers Unarchive instead of Archive for an archived type', async () => {
    const archived = item({ archived: true })
    const wrapper = mount(EventTypeList, { props: { items: [archived] } })

    await wrapper.find('button[aria-label="Actions for Practice"]').trigger('click')

    expect(wrapper.findAll('[role=menuitem]').map((m) => m.text())).toEqual(['Edit', 'Unarchive', 'Delete'])
    await wrapper.findAll('[role=menuitem]')[1]!.trigger('click')
    expect(wrapper.emitted('unarchive')?.[0]).toEqual([archived])
  })

  it('emits delete from the menu and closes the menu with Escape', async () => {
    const first = item()
    const wrapper = mount(EventTypeList, { props: { items: [first] }, attachTo: document.body })

    await wrapper.find('button[aria-label="Actions for Practice"]').trigger('click')
    await wrapper.find('[role=menu]').trigger('keydown', { key: 'Escape' })
    expect(wrapper.find('[role=menu]').exists()).toBe(false)

    await wrapper.find('button[aria-label="Actions for Practice"]').trigger('click')
    await wrapper.findAll('[role=menuitem]')[2]!.trigger('click')
    expect(wrapper.emitted('delete')?.[0]).toEqual([first])
    wrapper.unmount()
  })
})
