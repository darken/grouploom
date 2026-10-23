import { describe, expect, it } from 'vitest'

import { browserTimeZone, timeZoneOptions } from '../timeZones'

describe('time zones', () => {
  it('browserTimeZone returns an IANA id', () => {
    expect(browserTimeZone()).toMatch(/^[A-Za-z_]+(\/[A-Za-z_+-]+)*$/)
  })

  it('timeZoneOptions labels each zone with its UTC offset at the given moment', () => {
    const winter = timeZoneOptions(new Date('2026-01-15T12:00:00Z'))
    const summer = timeZoneOptions(new Date('2026-07-15T12:00:00Z'))

    expect(winter.find((o) => o.value === 'Europe/Madrid')!.label).toBe('Europe/Madrid (UTC+1)')
    expect(summer.find((o) => o.value === 'Europe/Madrid')!.label).toBe('Europe/Madrid (UTC+2)')
    expect(winter.find((o) => o.value === 'Australia/Adelaide')!.label).toBe('Australia/Adelaide (UTC+10:30)')
  })

  it('timeZoneOptions always includes the browser zone and UTC', () => {
    const values = timeZoneOptions().map((o) => o.value)

    expect(values).toContain(browserTimeZone())
    expect(values).toContain('UTC')
  })

  it('timeZoneOptions has no duplicates', () => {
    const values = timeZoneOptions().map((o) => o.value)

    expect(new Set(values).size).toBe(values.length)
  })
})
