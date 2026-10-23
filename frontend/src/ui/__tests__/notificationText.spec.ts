import { describe, expect, it } from 'vitest'

import type { EventNotification } from '@/stores/eventNotifications'
import { describeFailures, describeOffset, describeStatus, describeTiming, kindLabel } from '../notificationText'

function notification(overrides: Partial<EventNotification> = {}): EventNotification {
  return {
    id: 'n1',
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

describe('kindLabel', () => {
  it('names each kind', () => {
    expect(kindLabel('INVITATION')).toBe('Invitation')
    expect(kindLabel('REMINDER')).toBe('Reminder')
    expect(kindLabel('CANCELLATION')).toBe('Cancellation')
  })
})

describe('describeOffset', () => {
  it.each([
    [15, '15 minutes before'],
    [90, '90 minutes before'],
    [60, '1 hour before'],
    [120, '2 hours before'],
    [1440, '1 day before'],
    [2880, '2 days before'],
    [10080, '1 week before'],
    [20160, '2 weeks before'],
    [1500, '25 hours before'],
  ])('%i minutes reads "%s"', (minutes, text) => {
    expect(describeOffset(minutes)).toBe(text)
  })
})

describe('describeTiming', () => {
  it('says "Now" for a notification that was due when it was created', () => {
    const n = notification({ sendAt: '2026-10-01T12:00:10Z', localSendAt: '2026-10-01T14:00:10' })

    expect(describeTiming(n)).toBe('Now')
  })

  it('describes an offset notification relative to the start', () => {
    expect(describeTiming(notification({ offsetMinutes: 1440 }))).toBe('1 day before')
    expect(describeTiming(notification({ offsetMinutes: 120 }))).toBe('2 hours before')
  })

  it('formats a custom time in the event zone', () => {
    expect(describeTiming(notification())).toBe('Sat, Oct 31, 10:00')
  })
})

describe('describeStatus', () => {
  it('shows when a scheduled notification will go out, in the event zone', () => {
    expect(describeStatus(notification())).toBe('Scheduled · Sat, Oct 31, 10:00')
  })

  it('shows "Sending…" while the recipients are being queued', () => {
    expect(describeStatus(notification({ status: 'SENDING' }))).toBe('Sending…')
  })

  it('shows the sent date and the recipient count', () => {
    const sent = notification({ status: 'SENT', sentAt: '2026-11-01T17:30:00Z', recipientCount: 18 })

    expect(describeStatus(sent)).toBe('Sent Nov 1 · 18 recipients')
  })

  it('uses the singular for one recipient', () => {
    const sent = notification({ status: 'SENT', sentAt: '2026-11-01T17:30:00Z', recipientCount: 1 })

    expect(describeStatus(sent)).toBe('Sent Nov 1 · 1 recipient')
  })

  it('adds the failed count when some emails failed', () => {
    const sent = notification({
      status: 'SENT',
      sentAt: '2026-11-01T17:30:00Z',
      recipientCount: 18,
      delivery: { pending: 0, sent: 15, failed: 3 },
    })

    expect(describeStatus(sent)).toBe('Sent Nov 1 · 18 recipients · 3 failed')
    expect(describeFailures(sent)).toBe('3 failed')
  })

  it('says nobody was notified when there were no recipients', () => {
    const sent = notification({ status: 'SENT', sentAt: '2026-11-01T17:30:00Z', recipientCount: 0 })

    expect(describeStatus(sent)).toBe('No one to notify')
  })

  it('says "Cancelled" for a cancelled notification', () => {
    expect(describeStatus(notification({ status: 'CANCELLED' }))).toBe('Cancelled')
  })

  it('has no failures to report when none failed', () => {
    expect(describeFailures(notification())).toBeNull()
  })
})
