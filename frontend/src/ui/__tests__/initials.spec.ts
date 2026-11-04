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

  it('uses one character for a Japanese name, the family name coming first', () => {
    expect(initials('山田 太郎')).toBe('山')
    expect(initials('山田　太郎')).toBe('山')
    expect(initials('たなか ゆき')).toBe('た')
    expect(initials('タナカ ユキ')).toBe('タ')
  })

  it('uses one character for a Japanese name written without a space', () => {
    expect(initials('山田太郎')).toBe('山')
  })

  it('follows the first word when a name mixes scripts', () => {
    expect(initials('Ken 山田')).toBe('K')
    expect(initials('山田 Ken')).toBe('山')
  })

  it('returns an empty string for a blank name', () => {
    expect(initials('   ')).toBe('')
  })
})
