import { describe, expect, it } from 'vitest'

import en from '../locales/en.json'
import ja from '../locales/ja.json'

function keysOf(value: unknown, prefix = ''): string[] {
  if (typeof value !== 'object' || value === null) return [prefix]
  return Object.entries(value).flatMap(([key, child]) => keysOf(child, prefix ? `${prefix}.${key}` : key))
}

describe('message catalogs', () => {
  it('ja.json has exactly the same keys as en.json', () => {
    expect(keysOf(ja).sort()).toEqual(keysOf(en).sort())
  })
})
