import type { components } from '@/api/schema'

export type EventTypeColorKey = components['schemas']['EventTypeResponse']['color']

interface EventTypeColor {
  key: EventTypeColorKey
  /** Classes for a filled dot/swatch; full class names so Tailwind can see them. */
  dot: string
}

// The one place where color keys become CSS. The API only ever stores the key.
export const EVENT_TYPE_COLORS: readonly EventTypeColor[] = [
  { key: 'blue', dot: 'bg-blue-500 dark:bg-blue-400' },
  { key: 'green', dot: 'bg-green-500 dark:bg-green-400' },
  { key: 'orange', dot: 'bg-orange-500 dark:bg-orange-400' },
  { key: 'red', dot: 'bg-red-500 dark:bg-red-400' },
  { key: 'purple', dot: 'bg-purple-500 dark:bg-purple-400' },
  { key: 'pink', dot: 'bg-pink-500 dark:bg-pink-400' },
  { key: 'teal', dot: 'bg-teal-500 dark:bg-teal-400' },
  { key: 'gray', dot: 'bg-gray-400 dark:bg-gray-500' },
]

export function eventTypeColor(key: EventTypeColorKey): EventTypeColor {
  return EVENT_TYPE_COLORS.find((c) => c.key === key) ?? EVENT_TYPE_COLORS[0]!
}
