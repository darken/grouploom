import { describe, expect, it } from 'vitest'

import { detectLocale } from '../index'

describe('detectLocale', () => {
  it('maps a regional tag to its supported base language', () => {
    expect(detectLocale(['ja-JP', 'en'])).toBe('ja')
  })

  it('skips unsupported languages until it finds a supported one', () => {
    expect(detectLocale(['fr', 'ja'])).toBe('ja')
  })

  it('falls back to English when nothing is supported', () => {
    expect(detectLocale(['fr'])).toBe('en')
  })

  it('falls back to English for an empty list', () => {
    expect(detectLocale([])).toBe('en')
  })
})
