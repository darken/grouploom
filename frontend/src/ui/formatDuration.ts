export function formatDuration(minutes: number): string {
  if (minutes >= 1440) return 'All day'
  if (minutes < 60) return `${minutes} min`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  if (rest === 0) return `${hours} hr`
  if (rest === 30) return `${hours}.5 hr`
  return `${hours} hr ${rest} min`
}
