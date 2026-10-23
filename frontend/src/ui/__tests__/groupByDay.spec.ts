import { describe, expect, it } from 'vitest'

import { groupByDay } from '../groupByDay'

function event(id: string, localStart: string, timeZone = 'Europe/Madrid') {
  return { id, localStart, timeZone }
}

// 10:00 in Madrid on Mon 2 Nov 2026.
const NOW = new Date('2026-11-02T09:00:00Z')

describe('groupByDay', () => {
  it('labels the current day Today and the next one Tomorrow', () => {
    const sections = groupByDay(
      [event('a', '2026-11-02T18:00:00'), event('b', '2026-11-03T09:00:00'), event('c', '2026-11-05T09:00:00')],
      NOW,
    )

    expect(sections.map((s) => s.label)).toEqual(['Today', 'Tomorrow', 'Thu, Nov 5'])
  })

  it('keeps events of the same local day together, in order', () => {
    const sections = groupByDay([event('a', '2026-11-04T08:00:00'), event('b', '2026-11-04T19:00:00')], NOW)

    expect(sections).toHaveLength(1)
    expect(sections[0]!.key).toBe('2026-11-04')
    expect(sections[0]!.events.map((e) => e.id)).toEqual(['a', 'b'])
  })

  it('starts a new section when the day changes', () => {
    const sections = groupByDay([event('a', '2026-11-04T08:00:00'), event('b', '2026-11-06T19:00:00')], NOW)

    expect(sections.map((s) => s.key)).toEqual(['2026-11-04', '2026-11-06'])
  })

  it('adds the year when it is not the current year', () => {
    const sections = groupByDay([event('a', '2027-02-14T10:00:00')], NOW)

    expect(sections[0]!.label).toBe('Sun, Feb 14, 2027')
  })

  it('labels a past day with its date', () => {
    expect(groupByDay([event('a', '2026-11-01T10:00:00')], NOW)[0]!.label).toBe('Sun, Nov 1')
  })

  it("groups by the event's own date, not the viewer's", () => {
    // 00:30 on 3 Nov in Madrid is still 2 Nov in New York, but it must land under Nov 3.
    const sections = groupByDay([event('a', '2026-11-03T00:30:00')], NOW)

    expect(sections[0]!.key).toBe('2026-11-03')
    expect(sections[0]!.label).toBe('Tomorrow')
  })

  it("works out Today in the event's zone", () => {
    // It is 23:30 on 2 Nov in Madrid but already 3 Nov in Tokyo.
    const now = new Date('2026-11-02T22:30:00Z')

    expect(groupByDay([event('a', '2026-11-02T23:45:00', 'Europe/Madrid')], now)[0]!.label).toBe('Today')
    expect(groupByDay([event('b', '2026-11-03T01:00:00', 'Asia/Tokyo')], now)[0]!.label).toBe('Today')
  })

  it('returns no sections for no events', () => {
    expect(groupByDay([], NOW)).toEqual([])
  })
})
