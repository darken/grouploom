import { describe, expect, it } from 'vitest'

import en from '../locales/en.json'
import ja from '../locales/ja.json'

function keysOf(value: unknown, prefix = ''): string[] {
  if (typeof value !== 'object' || value === null) return [prefix]
  return Object.entries(value).flatMap(([key, child]) => keysOf(child, prefix ? `${prefix}.${key}` : key))
}

function entriesOf(value: unknown, prefix = ''): [string, string][] {
  if (typeof value === 'string') return [[prefix, value]]
  if (typeof value !== 'object' || value === null) return []
  return Object.entries(value).flatMap(([key, child]) => entriesOf(child, prefix ? `${prefix}.${key}` : key))
}

const placeholders = (text: string) => [...new Set([...text.matchAll(/\{(\w+)\}/g)].map((m) => m[1]!))].sort()
const withoutPlaceholders = (text: string) => text.replace(/\{\w+\}/g, '')

describe('message catalogs', () => {
  it('ja.json has exactly the same keys as en.json', () => {
    expect(keysOf(ja).sort()).toEqual(keysOf(en).sort())
  })

  it('ja.json uses the same placeholders as en.json in every message', () => {
    const japanese = new Map(entriesOf(ja))
    for (const [key, english] of entriesOf(en)) {
      expect({ key, placeholders: placeholders(japanese.get(key) ?? '') }).toEqual({ key, placeholders: placeholders(english) })
    }
  })

  it('ja.json uses full-width punctuation: no half-width colon, exclamation or question mark outside placeholders', () => {
    const offenders = entriesOf(ja)
      .filter(([, text]) => /[:!?]/.test(withoutPlaceholders(text)))
      .map(([key, text]) => `${key}: ${text}`)
    expect(offenders).toEqual([])
  })
})
