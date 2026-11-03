import { i18n } from '@/i18n'

export interface TimedEvent {
  start: string
  end: string
  timeZone: string
}

const dateFormat = (timeZone: string) =>
  new Intl.DateTimeFormat(i18n.global.locale.value, { timeZone, weekday: 'short', month: 'short', day: 'numeric' })

const timeFormat = (timeZone: string) =>
  new Intl.DateTimeFormat(i18n.global.locale.value, { timeZone, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })

/** "10:00": the wall-clock time of an instant in `timeZone`, on a 24-hour clock. */
export function formatClockTime(instant: string, timeZone: string): string {
  return timeFormat(timeZone).format(new Date(instant))
}

// en-CA formats as YYYY-MM-DD, which compares as a plain string.
const dayKey = (timeZone: string, date: Date) => new Intl.DateTimeFormat('en-CA', { timeZone }).format(date)

// en-US only knows abbreviations for US zones ("GMT+1" for Madrid); en-GB gives "CET".
function zoneAbbreviation(timeZone: string, at: Date): string {
  return (
    new Intl.DateTimeFormat('en-GB', { timeZone, timeZoneName: 'short' })
      .formatToParts(at)
      .find((p) => p.type === 'timeZoneName')?.value ?? timeZone
  )
}

/**
 * "Mon, Nov 2 · 10:00 – 12:00" or "Mon, Nov 2, 22:00 – Tue, Nov 3, 02:00", always in the event's own zone.
 * The zone abbreviation is appended only when the viewer is in a different zone.
 */
export function formatEventTime(event: TimedEvent, viewerZone: string): string {
  const start = new Date(event.start)
  const end = new Date(event.end)
  const zone = event.timeZone
  const date = dateFormat(zone)
  const time = timeFormat(zone)

  const { t } = i18n.global

  const text =
    dayKey(zone, start) === dayKey(zone, end)
      ? t('time.sameDayRange', { date: date.format(start), start: time.format(start), end: time.format(end) })
      : t('time.multiDayRange', {
          startDate: date.format(start),
          start: time.format(start),
          endDate: date.format(end),
          end: time.format(end),
        })

  return zone === viewerZone ? text : `${text} ${zoneAbbreviation(zone, end)}`
}
