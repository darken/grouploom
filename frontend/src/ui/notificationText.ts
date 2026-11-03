import { i18n } from '@/i18n'
import type { EventNotification } from '@/stores/eventNotifications'
import { formatClockTime } from '@/ui/formatEventTime'

type Kind = EventNotification['kind']

export const kindLabel = (kind: Kind): string => i18n.global.t(`notification.kind.${kind}`)

/** A notification created less than this long before it was due was sent "now". */
const NOW_WINDOW_MS = 60_000

/** "15 minutes before", "2 hours before", "1 day before", "1 week before". */
export function describeOffset(minutes: number): string {
  const { t } = i18n.global
  if (minutes % 10_080 === 0) return t('notification.offset.weeks', minutes / 10_080)
  if (minutes % 1_440 === 0) return t('notification.offset.days', minutes / 1_440)
  if (minutes % 60 === 0) return t('notification.offset.hours', minutes / 60)
  return t('notification.offset.minutes', minutes)
}

const dateTime = (timeZone: string) =>
  new Intl.DateTimeFormat(i18n.global.locale.value, { timeZone, weekday: 'short', month: 'short', day: 'numeric' })

const monthDay = (timeZone: string) => new Intl.DateTimeFormat(i18n.global.locale.value, { timeZone, month: 'short', day: 'numeric' })

/** "Fri, Nov 1, 10:00": an instant in the event's own zone. */
function formatSendTime(instant: string, timeZone: string): string {
  return i18n.global.t('time.dateTime', {
    date: dateTime(timeZone).format(new Date(instant)),
    time: formatClockTime(instant, timeZone),
  })
}

/** "Oct 20": a date in the event's own zone. */
export function formatMonthDay(instant: string, timeZone: string): string {
  return monthDay(timeZone).format(new Date(instant))
}

/** "Fri, Nov 1, 10:00": when a notification goes out, in the event's own zone. */
export function describeSendTime(n: EventNotification): string {
  return formatSendTime(n.sendAt, n.timeZone)
}

/** "Now", "1 day before" or the formatted custom time. */
export function describeTiming(n: EventNotification): string {
  if (n.offsetMinutes !== undefined) return describeOffset(n.offsetMinutes)
  if (Date.parse(n.sendAt) - Date.parse(n.createdAt) <= NOW_WINDOW_MS) return i18n.global.t('notification.now')
  return formatSendTime(n.sendAt, n.timeZone)
}

/** "3 failed", or null when nothing failed. */
export function describeFailures(n: EventNotification): string | null {
  return n.delivery.failed > 0 ? i18n.global.t('notification.failed', n.delivery.failed) : null
}

/** The status without the failure count, so the list can color that part red. */
export function describeStatusBase(n: EventNotification): string {
  const { t } = i18n.global
  switch (n.status) {
    case 'SCHEDULED':
      return t('notification.status.scheduled', { when: formatSendTime(n.sendAt, n.timeZone) })
    case 'SENDING':
      return t('notification.status.sending')
    case 'CANCELLED':
      return t('notification.status.cancelled')
    case 'SENT': {
      if (n.recipientCount === 0) return t('notification.status.noRecipients')
      const date = monthDay(n.timeZone).format(new Date(n.sentAt ?? n.sendAt))
      if (n.recipientCount === undefined) return t('notification.status.sent', { date })
      return t('notification.status.sentWithCount', { date, count: t('notification.recipients', n.recipientCount) })
    }
  }
}

/** "Scheduled · Fri, Nov 1, 10:00", "Sending…", "Sent Nov 1 · 18 recipients · 1 failed", "Cancelled". */
export function describeStatus(n: EventNotification): string {
  const failures = describeFailures(n)
  return failures ? `${describeStatusBase(n)} · ${failures}` : describeStatusBase(n)
}
