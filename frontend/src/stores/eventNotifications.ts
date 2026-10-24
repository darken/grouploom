import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { api } from '@/api/client'
import { ApiError, unwrap } from '@/api/errors'
import type { components } from '@/api/schema'
import { fieldErrorsFrom } from '@/composables/useFieldErrors'

export type EventNotification = components['schemas']['EventNotificationResponse']
export type ScheduleNotificationInput = components['schemas']['ScheduleNotificationRequest']
export type Audience = components['schemas']['AudienceResponse']
/** Cancellations have no audience preview. */
export type AudienceKind = Exclude<ScheduleNotificationInput['kind'], 'CANCELLATION'>

export type ScheduleResult =
  | { status: 'ok'; notification: EventNotification }
  | { status: 'limit' | 'cancelled' | 'started' | 'invalid' | 'error' }

export type UnscheduleResult = { status: 'ok' | 'already-sent' | 'error' }

/** How long to wait before looking again at a notification that was sent "now", to pick up delivery counts. */
const DELIVERY_REFETCH_MS = 10_000

const CONFLICTS: Record<string, 'limit' | 'cancelled' | 'started'> = {
  'notification.limit': 'limit',
  'event.cancelled': 'cancelled',
  'event.started': 'started',
}

function toApiError(e: unknown): ApiError {
  return e instanceof ApiError
    ? e
    : new ApiError({ title: 'Network error', detail: 'Could not reach the server.' }, 0)
}

const isActive = (n: EventNotification) => n.status === 'SCHEDULED' || n.status === 'SENDING'
const when = (n: EventNotification) => Date.parse(n.sentAt ?? n.sendAt)

/** Scheduled ones first, soonest first; then sent and cancelled ones, newest first. */
export function sortNotifications(items: readonly EventNotification[]): EventNotification[] {
  return [...items].sort((a, b) => {
    if (isActive(a) !== isActive(b)) return isActive(a) ? -1 : 1
    return isActive(a) ? Date.parse(a.sendAt) - Date.parse(b.sendAt) : when(b) - when(a)
  })
}

export const useEventNotificationsStore = defineStore('eventNotifications', () => {
  const eventId = ref('')
  const items = ref<EventNotification[]>([])
  const audience = ref<Audience | null>(null)
  const loading = ref(false)
  const loaded = ref(false)
  const saving = ref(false)
  const error = ref<ApiError | null>(null)
  const saveError = ref<ApiError | null>(null)

  const fieldErrors = computed<Record<string, string>>(() => fieldErrorsFrom(saveError.value))

  let latestList = 0
  let latestAudience = 0
  let delivered: ReturnType<typeof setTimeout> | undefined
  let removeFocusListener: (() => void) | undefined

  async function fetch(id: string) {
    const request = ++latestList
    // Don't show another event's notifications while this one loads; a reload of the same event keeps them.
    if (eventId.value !== id) {
      items.value = []
      audience.value = null
      loaded.value = false
    }
    eventId.value = id
    loading.value = true
    error.value = null
    try {
      const data = unwrap(await api.GET('/events/{eventId}/notifications', { params: { path: { eventId: id } } }))
      if (request !== latestList) return
      items.value = sortNotifications(data)
      loaded.value = true
    } catch (e) {
      if (request === latestList) error.value = toApiError(e)
    } finally {
      if (request === latestList) loading.value = false
    }
  }

  async function fetchAudience(id: string, kind: AudienceKind) {
    const request = ++latestAudience
    try {
      const data = unwrap(
        await api.GET('/events/{eventId}/notifications/audience', { params: { path: { eventId: id }, query: { kind } } }),
      )
      if (request === latestAudience) audience.value = data
    } catch {
      // The audience line is a nicety; the dialog works without it.
      if (request === latestAudience) audience.value = null
    }
  }

  /**
   * `onDelivered` is called once, ten seconds after a "now" notification is created, with the refreshed item,
   * so the caller can report how many members it reached.
   */
  async function schedule(
    input: ScheduleNotificationInput,
    onDelivered?: (notification: EventNotification | undefined) => void,
  ): Promise<ScheduleResult> {
    const id = eventId.value
    saving.value = true
    saveError.value = null
    try {
      const created = unwrap(
        await api.POST('/events/{eventId}/notifications', { params: { path: { eventId: id } }, body: input }),
      )
      items.value = sortNotifications([...items.value.filter((n) => n.id !== created.id), created])
      if (input.timing === 'NOW') scheduleDeliveryRefetch(id, created.id, onDelivered)
      return { status: 'ok', notification: created }
    } catch (e) {
      const failure = toApiError(e)
      saveError.value = failure
      const conflict = failure.status === 409 ? CONFLICTS[failure.problem.code ?? ''] : undefined
      if (conflict) return { status: conflict }
      return { status: failure.status === 400 && failure.problem.errors?.length ? 'invalid' : 'error' }
    } finally {
      saving.value = false
    }
  }

  function scheduleDeliveryRefetch(
    id: string,
    notificationId: string,
    onDelivered?: (notification: EventNotification | undefined) => void,
  ) {
    clearTimeout(delivered)
    delivered = setTimeout(async () => {
      delivered = undefined
      await fetch(id)
      onDelivered?.(items.value.find((n) => n.id === notificationId))
    }, DELIVERY_REFETCH_MS)
  }

  async function unschedule(notificationId: string): Promise<UnscheduleResult> {
    const id = eventId.value
    saveError.value = null
    try {
      const response = await api.DELETE('/events/{eventId}/notifications/{notificationId}', {
        params: { path: { eventId: id, notificationId } },
      })
      if (response.error !== undefined) unwrap({ ...response, data: undefined })
    } catch (e) {
      const failure = toApiError(e)
      saveError.value = failure
      if (failure.status === 409 && failure.problem.code === 'notification.already-sent') {
        await fetch(id)
        return { status: 'already-sent' }
      }
      return { status: 'error' }
    }
    items.value = items.value.filter((n) => n.id !== notificationId)
    return { status: 'ok' }
  }

  /** Looks again when the window regains focus (no polling); call `stop` when leaving the event page. */
  function start(id: string) {
    stop()
    const onFocus = () => void fetch(id)
    window.addEventListener('focus', onFocus)
    removeFocusListener = () => window.removeEventListener('focus', onFocus)
  }

  function stop() {
    removeFocusListener?.()
    removeFocusListener = undefined
    clearTimeout(delivered)
    delivered = undefined
  }

  function clearSaveError() {
    saveError.value = null
  }

  return {
    eventId, items, audience, loading, loaded, saving, error, saveError, fieldErrors,
    fetch, fetchAudience, schedule, unschedule, start, stop, clearSaveError,
  }
})
