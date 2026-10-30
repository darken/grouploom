import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import type { AttendanceRow } from '@/stores/attendance'
import AttendanceList from '../AttendanceList.vue'

const NOW = new Date('2026-11-01T12:00:00Z')

const rows: AttendanceRow[] = [
  { member: { id: 'm1', name: 'Ana Pérez', email: 'ana@example.com' }, response: 'GOING', source: 'MEMBER', respondedAt: '2026-11-01T11:00:00Z' },
  { member: { id: 'm2', name: 'Bob', email: 'bob@example.com' }, response: 'NOT_GOING', note: 'Away that week', source: 'MEMBER', respondedAt: '2026-10-31T12:00:00Z' },
  { member: { id: 'm3', name: 'Cy', email: 'cy@example.com' }, response: 'GOING', source: 'ORGANIZER', respondedAt: '2026-11-01T11:55:00Z' },
  { member: { id: 'm4', name: 'Di', email: 'di@example.com' } },
]

function mountList(props: { items?: AttendanceRow[]; readOnly?: boolean } = {}) {
  return mount(AttendanceList, { props: { items: rows, now: NOW, ...props } })
}

const rowOf = (w: ReturnType<typeof mountList>, name: string) => w.findAll('li').find((li) => li.text().includes(name))!

describe('AttendanceList', () => {
  it('shows an avatar, the name and the answer for each member', () => {
    const wrapper = mountList()

    expect(wrapper.findAll('li')).toHaveLength(4)
    expect(rowOf(wrapper, 'Ana Pérez').get('[data-avatar]').text()).toBe('AP')
    expect(rowOf(wrapper, 'Ana Pérez').get('[data-answer]').text()).toBe('Going')
    expect(rowOf(wrapper, 'Bob').get('[data-answer]').text()).toBe("Can't go")
    expect(rowOf(wrapper, 'Di').get('[data-answer]').text()).toBe('No reply')
  })

  it('colors only the Going answer green', () => {
    const wrapper = mountList()

    expect(rowOf(wrapper, 'Ana Pérez').get('[data-answer]').classes().join(' ')).toMatch(/text-green-/)
    expect(rowOf(wrapper, 'Bob').get('[data-answer]').classes().join(' ')).not.toMatch(/text-green-/)
    expect(rowOf(wrapper, 'Di').get('[data-answer]').classes().join(' ')).not.toMatch(/text-green-/)
  })

  it('quotes the note and labels who set the answer when an organizer did', () => {
    const wrapper = mountList()

    expect(rowOf(wrapper, 'Bob').get('[data-note]').text()).toBe('“Away that week”')
    expect(rowOf(wrapper, 'Bob').text()).not.toContain('by organizer')
    expect(rowOf(wrapper, 'Cy').text()).toContain('· by organizer')
  })

  it('shows how long ago the answer was given, and nothing for members who have not replied', () => {
    const wrapper = mountList()

    expect(rowOf(wrapper, 'Ana Pérez').get('time').text()).toBe('1 hr ago')
    expect(rowOf(wrapper, 'Cy').get('time').text()).toBe('5 min ago')
    expect(rowOf(wrapper, 'Di').find('time').exists()).toBe(false)
  })

  it('renders names and notes as text, never as markup', () => {
    const wrapper = mountList({
      items: [{ member: { id: 'x', name: '<b>Eve</b>', email: 'e@example.com' }, response: 'GOING', note: '<i>hi</i>', source: 'MEMBER', respondedAt: NOW.toISOString() }],
    })

    expect(wrapper.find('b, i').exists()).toBe(false)
    expect(wrapper.text()).toContain('<b>Eve</b>')
    expect(wrapper.text()).toContain('<i>hi</i>')
  })

  describe('row menu', () => {
    const menuLabels = async (name: string) => {
      const wrapper = mountList()
      await wrapper.get(`button[aria-label="Actions for ${name}"]`).trigger('click')
      return wrapper.findAll('[role=menuitem]').map((i) => i.text())
    }

    it('offers marking either answer, and clearing when there is an answer', async () => {
      expect(await menuLabels('Ana Pérez')).toEqual(['Mark as going', "Mark as can't go", 'Clear answer'])
    })

    it('leaves out Clear answer for a member who has not replied', async () => {
      expect(await menuLabels('Di')).toEqual(['Mark as going', "Mark as can't go"])
    })

    it('disables marking the answer a member already has', async () => {
      const wrapper = mountList()
      await wrapper.get('button[aria-label="Actions for Ana Pérez"]').trigger('click')

      const items = wrapper.findAll('[role=menuitem]')
      expect(items[0]!.attributes('disabled')).toBeDefined()
      expect(items[1]!.attributes('disabled')).toBeUndefined()
    })

    it('emits the member and the answer that was chosen', async () => {
      const wrapper = mountList()

      await wrapper.get('button[aria-label="Actions for Di"]').trigger('click')
      await wrapper.findAll('[role=menuitem]')[0]!.trigger('click')
      await wrapper.get('button[aria-label="Actions for Bob"]').trigger('click')
      const items = wrapper.findAll('[role=menuitem]')
      await items[items.length - 1]!.trigger('click')

      expect(wrapper.emitted('set')).toEqual([['m4', 'GOING']])
      expect(wrapper.emitted('clear')).toEqual([['m2']])
    })

    it('emits NOT_GOING for "Mark as can\'t go"', async () => {
      const wrapper = mountList()

      await wrapper.get('button[aria-label="Actions for Ana Pérez"]').trigger('click')
      await wrapper.findAll('[role=menuitem]')[1]!.trigger('click')

      expect(wrapper.emitted('set')).toEqual([['m1', 'NOT_GOING']])
    })

    it('is hidden when the list is read-only', () => {
      const wrapper = mountList({ readOnly: true })

      expect(wrapper.find('button').exists()).toBe(false)
      expect(wrapper.text()).toContain('Ana Pérez')
    })
  })
})
