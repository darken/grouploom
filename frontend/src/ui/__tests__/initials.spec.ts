import { describe, expect, it } from 'vitest'

import { initials } from '../initials'

describe('initials', () => {
  it('uses the single initial of a one-word name', () => {
    expect(initials('ada')).toBe('A')
  })

  it('uses the first and last initial of a multi-word name', () => {
    expect(initials('Ada Lovelace')).toBe('AL')
    expect(initials('Ada Augusta King Lovelace')).toBe('AL')
  })

  it('ignores extra whitespace', () => {
    expect(initials('  Ada \t  Lovelace  ')).toBe('AL')
  })

  it('uppercases accented letters', () => {
    expect(initials('álvaro núñez')).toBe('ÁN')
  })

  it('keeps characters outside the basic plane whole', () => {
    expect(initials('𝒜da Lovelace')).toBe('𝒜L')
  })

  it('returns an empty string for a blank name', () => {
    expect(initials('   ')).toBe('')
  })
})
