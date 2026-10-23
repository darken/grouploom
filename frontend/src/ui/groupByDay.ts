export interface DayEvent {
  localStart: string
  timeZone: string
}

export interface DaySection<T extends DayEvent> {
  /** The event's local date, `YYYY-MM-DD`. */
  key: string
  label: string
  events: T[]
}

const todayIn = (timeZone: string, now: Date) => new Intl.DateTimeFormat('en-CA', { timeZone }).format(now)

function addDays(key: string, days: number): string {
  const date = new Date(`${key}T00:00:00Z`)
  date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0, 10)
}

function label(key: string, timeZone: string, now: Date): string {
  const today = todayIn(timeZone, now)
  if (key === today) return 'Today'
  if (key === addDays(today, 1)) return 'Tomorrow'
  const sameYear = key.slice(0, 4) === today.slice(0, 4)
  return new Intl.DateTimeFormat('en-US', {
    timeZone: 'UTC',
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    ...(sameYear ? {} : { year: 'numeric' }),
  }).format(new Date(`${key}T00:00:00Z`))
}

/**
 * Groups consecutive events into day sections by the event's own local date (never the viewer's);
 * "Today" and "Tomorrow" are worked out in the event's zone too.
 */
export function groupByDay<T extends DayEvent>(events: T[], now: Date = new Date()): DaySection<T>[] {
  const sections: DaySection<T>[] = []
  for (const event of events) {
    const key = event.localStart.slice(0, 10)
    const last = sections[sections.length - 1]
    if (last && last.key === key) last.events.push(event)
    else sections.push({ key, label: label(key, event.timeZone, now), events: [event] })
  }
  return sections
}
