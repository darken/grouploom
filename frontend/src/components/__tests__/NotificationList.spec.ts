import { afterEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import type { EventNotification } from '@/stores/eventNotifications'
import NotificationList from '../NotificationList.vue'

function notification(id: string, overrides: Partial<EventNotification> = {}): EventNotification {
  return {
    id,
    kind: 'REMINDER',
    status: 'SCHEDULED',
    sendAt: '2026-10-31T09:00:00Z',
    localSendAt: '2026-10-31T10:00:00',
    timeZone: 'Europe/Madrid',
    delivery: { pending: 0, sent: 0, failed: 0 },
    createdAt: '2026-10-01T12:00:00Z',
    ...overrides,
  }
}

const mountList = (items: EventNotification[]) => mount(NotificationList, { props: { items }, attachTo: document.body })
const rows = (w: ReturnType<typeof mountList>) => w.findAll('li')

describe('NotificationList', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('shows the kind label, the status and the note in quotes', () => {
    const wrapper = mountList([notification('a', { kind: 'INVITATION', message: 'Bring a board' })])

    const row = rows(wrapper)[0]!
    expect(row.find('[data-kind]').text()).toBe('Invitation')
    expect(row.find('[data-status]').text()).toBe('Scheduled · Sat, Oct 31, 10:00')
    expect(row.find('[data-note]').text()).toBe('“Bring a board”')
    expect(row.find('[data-note]').classes()).toContain('truncate')
  })

  it('omits the note when there is none', () => {
    expect(mountList([notification('a')]).find('[data-note]').exists()).toBe(false)
  })

  it('orders scheduled ones first by send time, then the others newest first', () => {
    const wrapper = mountList([
      notification('old', { kind: 'INVITATION', status: 'SENT', sentAt: '2026-10-02T10:00:00Z', recipientCount: 2 }),
      notification('late', { sendAt: '2026-11-02T09:00:00Z', localSendAt: '2026-11-02T10:00:00' }),
      notification('cancelled', { status: 'CANCELLED', sendAt: '2026-10-20T09:00:00Z' }),
      notification('early'),
      notification('new', { kind: 'INVITATION', status: 'SENT', sentAt: '2026-10-10T10:00:00Z', recipientCount: 5 }),
    ])

    expect(rows(wrapper).map((r) => r.attributes('data-id'))).toEqual(['early', 'late', 'cancelled', 'new', 'old'])
  })

  it('offers "Unschedule" only on scheduled rows', async () => {
    const wrapper = mountList([
      notification('scheduled'),
      notification('sent', { status: 'SENT', sentAt: '2026-10-10T10:00:00Z', recipientCount: 5 }),
      notification('cancelled', { status: 'CANCELLED' }),
      notification('sending', { status: 'SENDING' }),
    ])

    const [scheduled, ...others] = rows(wrapper).filter((r) => r.attributes('data-id') === 'scheduled').concat(
      rows(wrapper).filter((r) => r.attributes('data-id') !== 'scheduled'),
    )
    expect(scheduled!.find('button[aria-haspopup=menu]').exists()).toBe(true)
    for (const row of others) expect(row.find('button[aria-haspopup=menu]').exists()).toBe(false)

    await scheduled!.get('button[aria-haspopup=menu]').trigger('click')
    expect(wrapper.findAll('[role=menuitem]').map((i) => i.text())).toEqual(['Unschedule'])
  })

  it('emits unschedule with the notification id', async () => {
    const wrapper = mountList([notification('a')])

    await wrapper.get('button[aria-haspopup=menu]').trigger('click')
    await wrapper.get('[role=menuitem]').trigger('click')

    expect(wrapper.emitted('unschedule')).toEqual([['a']])
  })

  it('shows the failed count in red', () => {
    const wrapper = mountList([
      notification('a', {
        kind: 'INVITATION',
        status: 'SENT',
        sentAt: '2026-11-01T17:30:00Z',
        recipientCount: 18,
        delivery: { pending: 0, sent: 15, failed: 3 },
      }),
    ])

    expect(wrapper.find('[data-status]').text()).toBe('Sent Nov 1 · 18 recipients · 3 failed')
    expect(wrapper.find('[data-failed]').text()).toBe('3 failed')
    expect(wrapper.find('[data-failed]').classes().join(' ')).toContain('danger')
  })

  it('has no failed marker when everything was delivered', () => {
    const wrapper = mountList([notification('a', { status: 'SENT', sentAt: '2026-11-01T17:30:00Z', recipientCount: 2 })])

    expect(wrapper.find('[data-failed]').exists()).toBe(false)
  })
})
