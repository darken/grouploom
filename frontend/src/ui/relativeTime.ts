const MINUTE = 60_000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

const days = new Intl.RelativeTimeFormat('en', { numeric: 'auto' })

/** "just now", "5 min ago", "2 hr ago", "yesterday", "3 days ago", then a date after six days. */
export function relativeTime(iso: string, now: Date = new Date()): string {
  const then = new Date(iso)
  const elapsed = now.getTime() - then.getTime()
  if (elapsed < MINUTE) return 'just now'
  if (elapsed < HOUR) return `${Math.floor(elapsed / MINUTE)} min ago`
  if (elapsed < DAY) return `${Math.floor(elapsed / HOUR)} hr ago`
  const elapsedDays = Math.floor(elapsed / DAY)
  if (elapsedDays <= 6) return days.format(-elapsedDays, 'day')
  const sameYear = then.getFullYear() === now.getFullYear()
  return new Intl.DateTimeFormat('en', {
    month: 'short',
    day: 'numeric',
    ...(sameYear ? {} : { year: 'numeric' }),
  }).format(then)
}
