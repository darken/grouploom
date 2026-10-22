import { describe, expect, it } from 'vitest'

import { formatDuration } from '../formatDuration'

describe('formatDuration', () => {
  it.each([
    [5, '5 min'],
    [30, '30 min'],
    [60, '1 hr'],
    [90, '1.5 hr'],
    [120, '2 hr'],
    [135, '2 hr 15 min'],
    [1440, 'All day'],
  ])('formats %i minutes as %s', (minutes, expected) => {
    expect(formatDuration(minutes)).toBe(expected)
  })
})
