import { i18n } from '@/i18n'

const MINUTE = 60_000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

// Japanese ICU data writes "3 日前" with a space; Japanese text sets the number flush against the unit.
const flushCjkUnit = (text: string) => text.replace(/(\d) (?=\p{Script=Han})/gu, '$1')

/** "just now", "5 min ago", "2 hr ago", "yesterday", "3 days ago", then a date after six days. */
export function relativeTime(iso: string, now: Date = new Date()): string {
  const { t, locale } = i18n.global
  const then = new Date(iso)
  const elapsed = now.getTime() - then.getTime()
  if (elapsed < MINUTE) return t('time.justNow')
  if (elapsed < HOUR) return t('time.minutesAgo', { n: Math.floor(elapsed / MINUTE) })
  if (elapsed < DAY) return t('time.hoursAgo', { n: Math.floor(elapsed / HOUR) })
  const elapsedDays = Math.floor(elapsed / DAY)
  if (elapsedDays <= 6) return flushCjkUnit(new Intl.RelativeTimeFormat(locale.value, { numeric: 'auto' }).format(-elapsedDays, 'day'))
  const sameYear = then.getFullYear() === now.getFullYear()
  return new Intl.DateTimeFormat(locale.value, {
    month: 'short',
    day: 'numeric',
    ...(sameYear ? {} : { year: 'numeric' }),
  }).format(then)
}
