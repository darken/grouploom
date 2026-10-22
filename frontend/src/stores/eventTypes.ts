import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { api } from '@/api/client'
import { ApiError, unwrap } from '@/api/errors'
import type { components } from '@/api/schema'
import { fieldErrorsFrom } from '@/composables/useFieldErrors'

export type EventType = components['schemas']['EventTypeResponse']
export type CreateEventTypeInput = components['schemas']['CreateEventTypeRequest']
export type UpdateEventTypeInput = components['schemas']['UpdateEventTypeRequest']

/** What the create/edit form emits: the edit fields additionally carry archived and version. */
export type EventTypeDraft = CreateEventTypeInput & Partial<Pick<UpdateEventTypeInput, 'archived' | 'version'>>

export interface PageInfo {
  number: number
  size: number
  totalElements: number
  totalPages: number
}

const PAGE_SIZE = 20

function toApiError(e: unknown): ApiError {
  return e instanceof ApiError
    ? e
    : new ApiError({ title: 'Network error', detail: 'Could not reach the server.' }, 0)
}

export const useEventTypesStore = defineStore('eventTypes', () => {
  const items = ref<EventType[]>([])
  const pageInfo = ref<PageInfo | null>(null)
  const archived = ref(false)
  const loading = ref(false)
  const loaded = ref(false)
  const saving = ref(false)
  // `error` is for loading and deleting; `saveError` is for the create/edit form.
  const error = ref<ApiError | null>(null)
  const saveError = ref<ApiError | null>(null)
  /** The server's copy of the last successful update (e.g. to build an Undo). */
  const lastSaved = ref<EventType | null>(null)

  const page = computed(() => pageInfo.value?.number ?? 0)
  const stale = computed(() => saveError.value?.problem.code === 'event-type.stale')
  const fieldErrors = computed<Record<string, string>>(() => {
    const problem = saveError.value?.problem
    if (!problem) return {}
    if (problem.code === 'event-type.stale') return {}
    if (problem.code && problem.field) return { [problem.field]: problem.detail ?? 'Invalid value.' }
    return fieldErrorsFrom(saveError.value)
  })

  async function fetch(options: { archived: boolean; page: number }) {
    archived.value = options.archived
    loading.value = true
    error.value = null
    try {
      const data = unwrap(
        await api.GET('/event-types', {
          params: { query: { archived: options.archived, page: options.page, size: PAGE_SIZE, sort: ['name,asc'] } },
        }),
      )
      items.value = data.content
      pageInfo.value = {
        number: data.page.number ?? 0,
        size: data.page.size ?? PAGE_SIZE,
        totalElements: data.page.totalElements ?? 0,
        totalPages: data.page.totalPages ?? 1,
      }
      loaded.value = true
    } catch (e) {
      error.value = toApiError(e)
    } finally {
      loading.value = false
    }
  }

  const reload = () => fetch({ archived: archived.value, page: page.value })

  async function save(request: () => Promise<void>): Promise<boolean> {
    saving.value = true
    saveError.value = null
    try {
      await request()
      return true
    } catch (e) {
      saveError.value = toApiError(e)
      return false
    } finally {
      saving.value = false
    }
  }

  const create = (input: CreateEventTypeInput) =>
    save(async () => {
      unwrap(await api.POST('/event-types', { body: input }))
      await reload()
    })

  const update = (id: string, input: UpdateEventTypeInput) =>
    save(async () => {
      const updated = unwrap(await api.PUT('/event-types/{id}', { params: { path: { id } }, body: input }))
      lastSaved.value = updated
      if (updated.archived !== archived.value) {
        items.value = items.value.filter((i) => i.id !== id)
        if (pageInfo.value) pageInfo.value.totalElements = Math.max(0, pageInfo.value.totalElements - 1)
      } else {
        items.value = items.value.map((i) => (i.id === id ? updated : i))
      }
    })

  async function remove(id: string): Promise<boolean> {
    error.value = null
    try {
      const response = await api.DELETE('/event-types/{id}', { params: { path: { id } } })
      if (response.error !== undefined) unwrap({ ...response, data: undefined })
    } catch (e) {
      error.value = toApiError(e)
      return false
    }
    await reload()
    if (items.value.length === 0 && page.value > 0 && !error.value) {
      await fetch({ archived: archived.value, page: page.value - 1 })
    }
    return true
  }

  function clearSaveError() {
    saveError.value = null
  }

  return {
    items, pageInfo, page, archived, loading, loaded, saving, error, saveError, lastSaved, stale, fieldErrors,
    fetch, create, update, remove, clearSaveError,
  }
})
