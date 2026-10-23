import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { api } from '@/api/client'
import { ApiError, unwrap } from '@/api/errors'
import type { components } from '@/api/schema'
import { fieldErrorsFrom } from '@/composables/useFieldErrors'
import type { PageInfo } from '@/stores/eventTypes'

export type EventItem = components['schemas']['EventResponse']
export type CreateEventInput = components['schemas']['CreateEventRequest']
export type UpdateEventInput = components['schemas']['UpdateEventRequest']
export type EventWhen = 'upcoming' | 'past'

/** What the event form emits: the edit fields additionally carry the version. */
export type EventDraft = CreateEventInput & Partial<Pick<UpdateEventInput, 'version'>>

const PAGE_SIZE = 20
const NEXT_UP_SIZE = 3

function toApiError(e: unknown): ApiError {
  return e instanceof ApiError
    ? e
    : new ApiError({ title: 'Network error', detail: 'Could not reach the server.' }, 0)
}

export const useEventsStore = defineStore('events', () => {
  const items = ref<EventItem[]>([])
  const pageInfo = ref<PageInfo | null>(null)
  const when = ref<EventWhen>('upcoming')
  const eventTypeId = ref('')
  const loading = ref(false)
  const loaded = ref(false)
  const error = ref<ApiError | null>(null)

  const current = ref<EventItem | null>(null)
  const currentLoading = ref(false)
  const currentError = ref<ApiError | null>(null)

  const nextUp = ref<EventItem[]>([])
  const nextUpLoaded = ref(false)
  const nextUpError = ref<ApiError | null>(null)

  const saving = ref(false)
  const saveError = ref<ApiError | null>(null)

  const page = computed(() => pageInfo.value?.number ?? 0)
  const stale = computed(() => saveError.value?.problem.code === 'event.stale')
  const fieldErrors = computed<Record<string, string>>(() => {
    const problem = saveError.value?.problem
    if (!problem || problem.code === 'event.stale') return {}
    return fieldErrorsFrom(saveError.value)
  })

  // Only the latest request may apply its result, so quick filter changes can't show stale results.
  let latestList = 0
  let latestOne = 0

  async function fetch(options: { when: EventWhen; eventTypeId: string; page: number }) {
    const request = ++latestList
    when.value = options.when
    eventTypeId.value = options.eventTypeId
    loading.value = true
    error.value = null
    try {
      const data = unwrap(
        await api.GET('/events', {
          params: {
            query: {
              when: options.when,
              eventTypeId: options.eventTypeId || undefined,
              page: options.page,
              size: PAGE_SIZE,
            },
          },
        }),
      )
      if (request !== latestList) return
      items.value = data.content
      pageInfo.value = {
        number: data.page.number ?? 0,
        size: data.page.size ?? PAGE_SIZE,
        totalElements: data.page.totalElements ?? 0,
        totalPages: data.page.totalPages ?? 1,
      }
      loaded.value = true
    } catch (e) {
      if (request === latestList) error.value = toApiError(e)
    } finally {
      if (request === latestList) loading.value = false
    }
  }

  async function fetchOne(id: string) {
    const request = ++latestOne
    // Don't flash the previous event while a different one loads; a reload of the same one keeps it.
    if (current.value?.id !== id) current.value = null
    currentLoading.value = true
    currentError.value = null
    try {
      const data = unwrap(await api.GET('/events/{id}', { params: { path: { id } } }))
      if (request === latestOne) current.value = data
    } catch (e) {
      if (request === latestOne) {
        current.value = null
        currentError.value = toApiError(e)
      }
    } finally {
      if (request === latestOne) currentLoading.value = false
    }
  }

  async function fetchNextUp() {
    nextUpError.value = null
    try {
      const data = unwrap(
        await api.GET('/events', { params: { query: { when: 'upcoming', page: 0, size: NEXT_UP_SIZE } } }),
      )
      nextUp.value = data.content
      nextUpLoaded.value = true
    } catch (e) {
      nextUpError.value = toApiError(e)
    }
  }

  async function save<T>(request: () => Promise<T>): Promise<T | null> {
    saving.value = true
    saveError.value = null
    try {
      return await request()
    } catch (e) {
      saveError.value = toApiError(e)
      return null
    } finally {
      saving.value = false
    }
  }

  /** Returns the created event so the caller can open it, or null when saving failed. */
  const create = (input: CreateEventInput) =>
    save(async () => unwrap(await api.POST('/events', { body: input })))

  async function update(id: string, input: UpdateEventInput): Promise<boolean> {
    const updated = await save(async () =>
      unwrap(await api.PUT('/events/{id}', { params: { path: { id } }, body: input })),
    )
    if (!updated) return false
    if (current.value?.id === id) current.value = updated
    items.value = items.value.map((i) => (i.id === id ? updated : i))
    return true
  }

  async function remove(id: string): Promise<boolean> {
    error.value = null
    try {
      const response = await api.DELETE('/events/{id}', { params: { path: { id } } })
      if (response.error !== undefined) unwrap({ ...response, data: undefined })
    } catch (e) {
      error.value = toApiError(e)
      return false
    }
    if (items.value.some((i) => i.id === id) && pageInfo.value) {
      pageInfo.value.totalElements = Math.max(0, pageInfo.value.totalElements - 1)
    }
    items.value = items.value.filter((i) => i.id !== id)
    if (current.value?.id === id) current.value = null
    return true
  }

  function clearSaveError() {
    saveError.value = null
  }

  return {
    items, pageInfo, page, when, eventTypeId, loading, loaded, error,
    current, currentLoading, currentError,
    nextUp, nextUpLoaded, nextUpError,
    saving, saveError, stale, fieldErrors,
    fetch, fetchOne, fetchNextUp, create, update, remove, clearSaveError,
  }
})
