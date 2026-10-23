import { describe, expect, it } from 'vitest'

import { highlight } from '../highlight'

describe('highlight', () => {
  it('returns the whole text unmatched when the query is blank', () => {
    expect(highlight('Ana Pérez', '  ')).toEqual([{ text: 'Ana Pérez', match: false }])
  })

  it('splits around a case-insensitive match and keeps the original casing', () => {
    expect(highlight('Ana Pérez', 'ANA')).toEqual([
      { text: 'Ana', match: true },
      { text: ' Pérez', match: false },
    ])
  })

  it('marks every occurrence', () => {
    expect(highlight('banana', 'an')).toEqual([
      { text: 'b', match: false },
      { text: 'an', match: true },
      { text: 'an', match: true },
      { text: 'a', match: false },
    ])
  })

  it('treats regex and HTML characters literally', () => {
    expect(highlight('a.b <b>', '.')).toEqual([
      { text: 'a', match: false },
      { text: '.', match: true },
      { text: 'b <b>', match: false },
    ])
    expect(highlight('x <b>y</b>', '<b>')).toContainEqual({ text: '<b>', match: true })
  })

  it('returns the text unmatched when nothing matches', () => {
    expect(highlight('Bob', 'ana')).toEqual([{ text: 'Bob', match: false }])
  })
})
