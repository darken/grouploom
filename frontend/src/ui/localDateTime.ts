// Wall-clock date/time strings (`YYYY-MM-DD`, `HH:mm`) handled as if they were UTC, so arithmetic on them
// never meets a DST change. The server does the real time zone conversion.

export interface DateTimeParts {
  startDate: string
  startTime: string
  endDate: string
  endTime: string
}

export function wallClockMs(date: string, time: string): number {
  return Date.parse(`${date}T${time}:00Z`)
}

export function fromWallClockMs(ms: number): { date: string; time: string } {
  const iso = new Date(ms).toISOString()
  return { date: iso.slice(0, 10), time: iso.slice(11, 16) }
}

export function toLocalDateTime(date: string, time: string): string {
  return `${date}T${time}:00`
}
