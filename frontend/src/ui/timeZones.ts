export function browserTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone
}

function offsetLabel(zone: string, at: Date): string {
  const part = new Intl.DateTimeFormat('en-US', { timeZone: zone, timeZoneName: 'shortOffset' })
    .formatToParts(at)
    .find((p) => p.type === 'timeZoneName')
  // "GMT+5:30" -> "UTC+5:30"; plain "GMT" is UTC itself.
  return part?.value.replace('GMT', 'UTC') ?? 'UTC'
}

/** Every IANA zone the browser knows, labeled with its UTC offset at `at`. */
export function timeZoneOptions(at: Date = new Date()): { value: string; label: string }[] {
  // Intl.supportedValuesOf is missing from this project's TypeScript lib typings.
  const intl = Intl as unknown as { supportedValuesOf?: (key: 'timeZone') => string[] }
  const supported = intl.supportedValuesOf?.('timeZone') ?? []
  const zones = new Set<string>([...supported, 'UTC', browserTimeZone()])
  return [...zones].sort().map((value) => ({ value, label: `${value} (${offsetLabel(value, at)})` }))
}
