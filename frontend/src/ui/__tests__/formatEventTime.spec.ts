import { describe, expect, it } from 'vitest'

import { formatClockTime, formatEventTime } from '../formatEventTime'

// 2 November 2026 is winter time in Madrid (UTC+1) and in New York (UTC-5 from the 1st).
const sameDay = { start: '2026-11-02T09:00:00Z', end: '2026-11-02T11:00:00Z', timeZone: 'Europe/Madrid' }
const multiDay = { start: '2026-11-02T21:00:00Z', end: '2026-11-03T01:00:00Z', timeZone: 'Europe/Madrid' }

describe('formatEventTime', () => {
  it('formats a same-day range in the event zone', () => {
    expect(formatEventTime(sameDay, 'Europe/Madrid')).toBe('Mon, Nov 2 · 10:00 – 12:00')
  })

  it('formats a multi-day range with both dates', () => {
    expect(formatEventTime(multiDay, 'Europe/Madrid')).toBe('Mon, Nov 2, 22:00 – Tue, Nov 3, 02:00')
  })

  it('uses the event zone for the wall-clock times, not the viewer zone', () => {
    const text = formatEventTime(sameDay, 'America/New_York')

    expect(text).toContain('10:00 – 12:00')
    expect(text).not.toContain('04:00')
  })

  it('adds the zone abbreviation only when the event zone differs from the viewer zone', () => {
    expect(formatEventTime(sameDay, 'Europe/Madrid')).not.toContain('CET')
    expect(formatEventTime(sameDay, 'America/New_York')).toMatch(/ CET$/)
  })

  it('adds the abbreviation to multi-day ranges too', () => {
    expect(formatEventTime(multiDay, 'Asia/Tokyo')).toMatch(/02:00 CET$/)
  })

  it('judges "same day" in the event zone even when the viewer zone would split it', () => {
    // 23:30-23:50 Madrid is 17:30-17:50 in New York: still one day either way, but 23:30 UTC-1 is not.
    const late = { start: '2026-11-02T22:30:00Z', end: '2026-11-02T22:50:00Z', timeZone: 'Europe/Madrid' }

    expect(formatEventTime(late, 'Pacific/Auckland')).toContain('Mon, Nov 2 · 23:30 – 23:50')
  })
})

describe('formatClockTime', () => {
  it('formats the wall-clock time in the given zone on a 24-hour clock', () => {
    expect(formatClockTime('2026-11-02T09:00:00Z', 'Europe/Madrid')).toBe('10:00')
    expect(formatClockTime('2026-11-03T00:05:00Z', 'UTC')).toBe('00:05')
    expect(formatClockTime('2026-11-02T22:30:00Z', 'Asia/Tokyo')).toBe('07:30')
  })
})
