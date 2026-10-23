import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { api } from '@/api/client'
import { ApiError, unwrap } from '@/api/errors'
import type { components } from '@/api/schema'
import { fieldErrorsFrom } from '@/composables/useFieldErrors'
import type { PageInfo } from '@/stores/eventTypes'

export type Member = components['schemas']['MemberResponse']
export type CreateMemberInput = components['schemas']['CreateMemberRequest']
export type UpdateMemberInput = components['schemas']['UpdateMemberRequest']
export type PreferencesLink = components['schemas']['PreferencesLinkResponse']

/** What the member form emits: the edit fields additionally carry the version. */
export type MemberDraft = CreateMemberInput & Partial<Pick<UpdateMemberInput, 'version'>>

const PAGE_SIZE = 20

function toApiError(e: unknown): ApiError {
  return e instanceof ApiError
    ? e
    : new ApiError({ title: 'Network error', detail: 'Could not reach the server.' }, 0)
}

export const useMembersStore = defineStore('members', () => {
  const items = ref<Member[]>([])
  const pageInfo = ref<PageInfo | null>(null)
  const query = ref('')
  const loading = ref(false)
  const loaded = ref(false)
  const saving = ref(false)
  // `error` is for loading and removing; `saveError` is for the create/edit form.
  const error = ref<ApiError | null>(null)
  const saveError = ref<ApiError | null>(null)

  const page = computed(() => pageInfo.value?.number ?? 0)
  const stale = computed(() => saveError.value?.problem.code === 'member.stale')
  const fieldErrors = computed<Record<string, string>>(() => {
    const problem = saveError.value?.problem
    if (!problem || problem.code === 'member.stale') return {}
    if (problem.code && problem.field) return { [problem.field]: problem.detail ?? 'Invalid value.' }
    return fieldErrorsFrom(saveError.value)
  })

  // Only the latest request may apply its result, so fast typing can't show stale results.
  let latestRequest = 0

  async function fetch(options: { q: string; page: number }) {
    const request = ++latestRequest
    query.value = options.q
    loading.value = true
    error.value = null
    try {
      const data = unwrap(
        await api.GET('/members', {
          params: {
            query: { q: options.q.trim() || undefined, page: options.page, size: PAGE_SIZE, sort: ['name,asc'] },
          },
        }),
      )
      if (request !== latestRequest) return
      items.value = data.content
      pageInfo.value = {
        number: data.page.number ?? 0,
        size: data.page.size ?? PAGE_SIZE,
        totalElements: data.page.totalElements ?? 0,
        totalPages: data.page.totalPages ?? 1,
      }
      loaded.value = true
    } catch (e) {
      if (request === latestRequest) error.value = toApiError(e)
    } finally {
      if (request === latestRequest) loading.value = false
    }
  }

  const reload = () => fetch({ q: query.value, page: page.value })

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

  const create = (input: CreateMemberInput) =>
    save(async () => {
      unwrap(await api.POST('/members', { body: input }))
      await reload()
    })

  const update = (id: string, input: UpdateMemberInput) =>
    save(async () => {
      const updated = unwrap(await api.PUT('/members/{id}', { params: { path: { id } }, body: input }))
      items.value = items.value.map((i) => (i.id === id ? updated : i))
    })

  async function remove(id: string): Promise<boolean> {
    error.value = null
    try {
      const response = await api.DELETE('/members/{id}', { params: { path: { id } } })
      if (response.error !== undefined) unwrap({ ...response, data: undefined })
    } catch (e) {
      error.value = toApiError(e)
      return false
    }
    await reload()
    if (items.value.length === 0 && page.value > 0 && !error.value) {
      await fetch({ q: query.value, page: page.value - 1 })
    }
    return true
  }

  /** Issues a new personal link for the member (earlier ones keep working); null when it couldn't be created. */
  async function createPreferencesLink(id: string): Promise<PreferencesLink | null> {
    error.value = null
    try {
      return unwrap(await api.POST('/members/{id}/preferences-link', { params: { path: { id } } }))
    } catch (e) {
      error.value = toApiError(e)
      return null
    }
  }

  function clearSaveError() {
    saveError.value = null
  }

  return {
    items, pageInfo, page, query, loading, loaded, saving, error, saveError, stale, fieldErrors,
    fetch, create, update, remove, createPreferencesLink, clearSaveError,
  }
})
