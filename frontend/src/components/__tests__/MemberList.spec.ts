import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import type { Member } from '@/stores/members'
import MemberList from '../MemberList.vue'

function member(overrides: Partial<Member> = {}): Member {
  return {
    id: '1',
    name: 'Ana Pérez',
    email: 'ana@example.com',
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
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

  it('opens an actions menu with Edit and Remove', async () => {
    const first = member()
    const wrapper = mount(MemberList, { props: { items: [first] } })
    expect(wrapper.find('[role=menu]').exists()).toBe(false)

    await wrapper.find('button[aria-label="Actions for Ana Pérez"]').trigger('click')
    expect(wrapper.findAll('[role=menuitem]').map((m) => m.text())).toEqual(['Edit', 'Remove'])

    await wrapper.findAll('[role=menuitem]')[1]!.trigger('click')
    expect(wrapper.emitted('remove')?.[0]).toEqual([first])
    expect(wrapper.find('[role=menu]').exists()).toBe(false)
  })

  it('emits edit from the Edit menu item', async () => {
    const first = member()
    const wrapper = mount(MemberList, { props: { items: [first] } })

    await wrapper.find('button[aria-label="Actions for Ana Pérez"]').trigger('click')
    await wrapper.findAll('[role=menuitem]')[0]!.trigger('click')

    expect(wrapper.emitted('edit')?.[0]).toEqual([first])
  })

  it('closes the menu with Escape', async () => {
    const wrapper = mount(MemberList, { props: { items: [member()] }, attachTo: document.body })

    await wrapper.find('button[aria-label="Actions for Ana Pérez"]').trigger('click')
    await wrapper.find('[role=menu]').trigger('keydown', { key: 'Escape' })

    expect(wrapper.find('[role=menu]').exists()).toBe(false)
    wrapper.unmount()
  })
})
