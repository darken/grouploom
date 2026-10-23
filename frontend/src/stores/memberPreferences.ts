import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { api } from '@/api/client'
import { ApiError, unwrap } from '@/api/errors'
import type { components } from '@/api/schema'

export type MemberPreferences = components['schemas']['PublicPreferencesResponse']
type UpdateBody = components['schemas']['UpdatePreferencesRequest']

export type PreferencesStatus = 'loading' | 'ready' | 'invalid' | 'expired' | 'error'
export type SaveStatus = 'idle' | 'saving' | 'saved' | 'error'

// Kept per tab so a reload still works after the fragment has been removed from the address bar.
const STORAGE_KEY = 'gl_member_token'

function readSaved(): string | null {
  try {
    return sessionStorage.getItem(STORAGE_KEY)
  } catch {
    return null
  }
}

function writeSaved(token: string) {
  try {
    sessionStorage.setItem(STORAGE_KEY, token)
  } catch {
    // Private mode or blocked storage: the page works, it just won't survive a reload.
  }
}

function toApiError(e: unknown): ApiError {
  return e instanceof ApiError
    ? e
    : new ApiError({ title: 'Network error', detail: 'Could not reach the server.' }, 0)
}

// The token travels in a header, never in the URL, so it stays out of server logs.
const tokenParams = (token: string | null) => ({ header: { 'X-Member-Token': token ?? '' } })

export const useMemberPreferencesStore = defineStore('memberPreferences', () => {
  const token = ref<string | null>(null)
  const data = ref<MemberPreferences | null>(null)
  const status = ref<PreferencesStatus>('loading')
  const saving = ref(false)
  const saved = ref(false)
  const error = ref<ApiError | null>(null)

  const saveStatus = computed<SaveStatus>(() => {
    if (error.value && status.value === 'ready') return 'error'
    if (saving.value) return 'saving'
    return saved.value ? 'saved' : 'idle'
  })

  /** The last state the server confirmed; what a failed save reverts to. */
  let confirmed: MemberPreferences | null = null
  let failed: UpdateBody | null = null
  let dirty = false
  let inFlight = false

  /** Takes the link token from the URL fragment (`#t=…`), keeps it for this tab and removes it from the address bar. */
  function init(hash: string) {
    const fromFragment = new URLSearchParams(hash.replace(/^#/, '')).get('t')
    if (fromFragment) {
      writeSaved(fromFragment)
      // Keep the router's history state; only the fragment goes.
      window.history.replaceState(window.history.state, '', window.location.pathname + window.location.search)
    }
    token.value = fromFragment || readSaved() || null
    if (!token.value) status.value = 'invalid'
  }

  function unauthorized(e: ApiError): boolean {
    if (e.status !== 401) return false
    status.value = e.problem.code === 'member-token.expired' ? 'expired' : 'invalid'
    return true
  }

  async function fetchPreferences(): Promise<MemberPreferences> {
    const result = unwrap(await api.GET('/public/preferences', { params: tokenParams(token.value) }))
    confirmed = structuredClone(result)
    return result
  }

  async function load() {
    if (!token.value) {
      status.value = 'invalid'
      return
    }
    status.value = 'loading'
    error.value = null
    try {
      data.value = await fetchPreferences()
      status.value = 'ready'
    } catch (e) {
      const apiError = toApiError(e)
      if (unauthorized(apiError)) return
      error.value = apiError
      status.value = 'error'
    }
  }

  function body(): UpdateBody {
    return {
      invitationsPaused: data.value?.invitationsPaused ?? false,
      eventTypes: (data.value?.eventTypes ?? []).map((t) => ({ id: t.id, subscribed: t.subscribed })),
    }
  }

  /** One request at a time; whatever the member chose last is what gets saved next. */
  async function flush() {
    if (inFlight) return
    inFlight = true
    saving.value = true
    try {
      while (dirty && data.value) {
        dirty = false
        const sent = body()
        try {
          const result = unwrap(await api.PUT('/public/preferences', { params: tokenParams(token.value), body: sent }))
          confirmed = structuredClone(result)
          // A newer choice made while this request was in flight stays on screen until it is saved too.
          if (!dirty) data.value = result
          saved.value = !dirty
        } catch (e) {
          dirty = false
          const apiError = toApiError(e)
          if (unauthorized(apiError)) return
          failed = sent
          data.value = confirmed ? structuredClone(confirmed) : data.value
          error.value = apiError
          if (apiError.problem.code === 'preference.unknown-event-type') {
            // An event type was archived since the page loaded: show the current list.
            await fetchPreferences().then((fresh) => (data.value = fresh), () => undefined)
          }
        }
      }
    } finally {
      inFlight = false
      saving.value = false
    }
  }

  function change(apply: (current: MemberPreferences) => boolean) {
    if (status.value !== 'ready' || !data.value || !apply(data.value)) return
    error.value = null
    failed = null
    saved.value = false
    dirty = true
    void flush()
  }

  const setSubscribed = (typeId: string, value: boolean) =>
    change((current) => {
      const type = current.eventTypes.find((t) => t.id === typeId)
      if (!type || type.subscribed === value) return false
      type.subscribed = value
      return true
    })

  const setPaused = (value: boolean) =>
    change((current) => {
      if (current.invitationsPaused === value) return false
      current.invitationsPaused = value
      return true
    })

  /** Applies the change that failed to save once more. */
  function retry() {
    const attempt = failed
    if (!attempt) return
    change((current) => {
      current.invitationsPaused = attempt.invitationsPaused
      for (const choice of attempt.eventTypes) {
        const type = current.eventTypes.find((t) => t.id === choice.id)
        if (type) type.subscribed = choice.subscribed
      }
      return true
    })
  }

  return { token, data, status, saving, error, saveStatus, init, load, setSubscribed, setPaused, retry }
})
