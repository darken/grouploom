import type { EventNotification } from '@/stores/eventNotifications'
import { formatClockTime } from '@/ui/formatEventTime'

type Kind = EventNotification['kind']

const KIND_LABELS: Record<Kind, string> = {
  INVITATION: 'Invitation',
  REMINDER: 'Reminder',
  CANCELLATION: 'Cancellation',
}

export const kindLabel = (kind: Kind): string => KIND_LABELS[kind]

/** A notification created less than this long before it was due was sent "now". */
const NOW_WINDOW_MS = 60_000

const plural = (n: number, unit: string) => `${n} ${unit}${n === 1 ? '' : 's'}`

/** "15 minutes before", "2 hours before", "1 day before", "1 week before". */
export function describeOffset(minutes: number): string {
  if (minutes % 10_080 === 0) return `${plural(minutes / 10_080, 'week')} before`
  if (minutes % 1_440 === 0) return `${plural(minutes / 1_440, 'day')} before`
  if (minutes % 60 === 0) return `${plural(minutes / 60, 'hour')} before`
  return `${plural(minutes, 'minute')} before`
}

const dateTime = (timeZone: string) =>
  new Intl.DateTimeFormat('en-US', { timeZone, weekday: 'short', month: 'short', day: 'numeric' })

const monthDay = (timeZone: string) => new Intl.DateTimeFormat('en-US', { timeZone, month: 'short', day: 'numeric' })

/** "Fri, Nov 1, 10:00": an instant in the event's own zone. */
function formatSendTime(instant: string, timeZone: string): string {
  return `${dateTime(timeZone).format(new Date(instant))}, ${formatClockTime(instant, timeZone)}`
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
  if (Date.parse(n.sendAt) - Date.parse(n.createdAt) <= NOW_WINDOW_MS) return 'Now'
  return formatSendTime(n.sendAt, n.timeZone)
}

/** "3 failed", or null when nothing failed. */
export function describeFailures(n: EventNotification): string | null {
  return n.delivery.failed > 0 ? `${n.delivery.failed} failed` : null
}

/** The status without the failure count, so the list can color that part red. */
export function describeStatusBase(n: EventNotification): string {
  switch (n.status) {
    case 'SCHEDULED':
      return `Scheduled · ${formatSendTime(n.sendAt, n.timeZone)}`
    case 'SENDING':
      return 'Sending…'
    case 'CANCELLED':
      return 'Cancelled'
    case 'SENT': {
      if (n.recipientCount === 0) return 'No one to notify'
      const when = monthDay(n.timeZone).format(new Date(n.sentAt ?? n.sendAt))
      const count = n.recipientCount === undefined ? '' : ` · ${plural(n.recipientCount, 'recipient')}`
      return `Sent ${when}${count}`
    }
  }
}

/** "Scheduled · Fri, Nov 1, 10:00", "Sending…", "Sent Nov 1 · 18 recipients · 1 failed", "Cancelled". */
export function describeStatus(n: EventNotification): string {
  const failures = describeFailures(n)
  return failures ? `${describeStatusBase(n)} · ${failures}` : describeStatusBase(n)
}
