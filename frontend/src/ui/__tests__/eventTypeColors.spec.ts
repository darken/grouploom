import { describe, expect, it } from 'vitest'

import { EVENT_TYPE_COLORS, eventTypeColor } from '../eventTypeColors'

describe('eventTypeColors', () => {
  it('defines the eight backend color keys, each with a label and dot classes for light and dark', () => {
    expect(EVENT_TYPE_COLORS.map((c) => c.key)).toEqual([
      'blue', 'green', 'orange', 'red', 'purple', 'pink', 'teal', 'gray',
    ])
    for (const color of EVENT_TYPE_COLORS) {
      expect(color.label).toBeTruthy()
      expect(color.dot).toMatch(/bg-/)
      expect(color.dot).toMatch(/dark:bg-/)
    }
  })

  it('eventTypeColor looks a color up by key', () => {
    expect(eventTypeColor('teal').label).toBe('Teal')
  })
})
