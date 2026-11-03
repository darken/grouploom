import { i18n } from '@/i18n'

export function formatDuration(minutes: number): string {
  const { t } = i18n.global
  if (minutes >= 1440) return t('time.allDay')
  if (minutes < 60) return t('time.minutes', { n: minutes })
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  if (rest === 0) return t('time.hours', { n: hours })
  if (rest === 30) return t('time.halfHours', { n: hours })
  return t('time.hoursMinutes', { hours, minutes: rest })
}
