export interface TimedEvent {
  start: string
  end: string
  timeZone: string
}

const dateFormat = (timeZone: string) =>
  new Intl.DateTimeFormat('en-US', { timeZone, weekday: 'short', month: 'short', day: 'numeric' })

const timeFormat = (timeZone: string) =>
  new Intl.DateTimeFormat('en-US', { timeZone, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })

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

  const text =
    dayKey(zone, start) === dayKey(zone, end)
      ? `${date.format(start)} · ${time.format(start)} – ${time.format(end)}`
      : `${date.format(start)}, ${time.format(start)} – ${date.format(end)}, ${time.format(end)}`

  return zone === viewerZone ? text : `${text} ${zoneAbbreviation(zone, end)}`
}
