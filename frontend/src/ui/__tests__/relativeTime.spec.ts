import { describe, expect, it } from 'vitest'

import { relativeTime } from '../relativeTime'

const NOW = new Date('2026-01-10T12:00:00Z')
const ago = (seconds: number) => new Date(NOW.getTime() - seconds * 1000).toISOString()

describe('relativeTime', () => {
  it.each([
    [0, 'just now'],
    [20, 'just now'],
    [59, 'just now'],
    [60, '1 min ago'],
    [5 * 60, '5 min ago'],
    [59 * 60, '59 min ago'],
    [60 * 60, '1 hr ago'],
    [2 * 3600, '2 hr ago'],
    [23 * 3600, '23 hr ago'],
    [24 * 3600, 'yesterday'],
    [47 * 3600, 'yesterday'],
    [2 * 86400, '2 days ago'],
    [6 * 86400, '6 days ago'],
  ])('shows %i seconds ago as "%s"', (seconds, expected) => {
    expect(relativeTime(ago(seconds), NOW)).toBe(expected)
  })

  it('treats a time slightly in the future (clock skew) as just now', () => {
    expect(relativeTime(ago(-30), NOW)).toBe('just now')
  })

  it('shows a short date once it is older than six days', () => {
    expect(relativeTime(ago(7 * 86400), NOW)).toBe('Jan 3')
  })

  it('adds the year when the date is in an earlier year', () => {
    expect(relativeTime('2025-12-20T12:00:00Z', NOW)).toBe('Dec 20, 2025')
  })

  it('uses the current time when no clock is injected', () => {
    expect(relativeTime(new Date().toISOString())).toBe('just now')
  })
})
