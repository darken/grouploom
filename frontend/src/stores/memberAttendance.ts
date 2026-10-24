import { ref } from 'vue'
import { defineStore } from 'pinia'

import { api } from '@/api/client'
import { ApiError, unwrap } from '@/api/errors'
import type { components } from '@/api/schema'
import { useMemberLinkToken } from '@/composables/useMemberLinkToken'

export type MemberAttendance = components['schemas']['PublicAttendanceResponse']
export type AttendanceChoice = components['schemas']['AnswerRequest']['response']

export type AttendanceStatus = 'loading' | 'ready' | 'invalid' | 'expired' | 'error'

// Its own key, so opening a reply link never replaces the token of a preferences page open in the same tab.
const STORAGE_KEY = 'gl_member_token_attendance'

const FROM_LINK: Record<string, AttendanceChoice> = { going: 'GOING', 'not-going': 'NOT_GOING' }

function toApiError(e: unknown): ApiError {
  return e instanceof ApiError
    ? e
    : new ApiError({ title: 'Network error', detail: 'Could not reach the server.' }, 0)
}

// The token travels in a header, never in the URL, so it stays out of server logs.
const tokenParams = (token: string | null) => ({ header: { 'X-Member-Token': token ?? '' } })

export const useMemberAttendanceStore = defineStore('memberAttendance', () => {
  const link = useMemberLinkToken(STORAGE_KEY)
  const token = link.token
  const data = ref<MemberAttendance | null>(null)
  const choice = ref<AttendanceChoice | null>(null)
  const note = ref('')
  const status = ref<AttendanceStatus>('loading')
  const saving = ref(false)
  const saved = ref(false)
  const error = ref<ApiError | null>(null)

  /** What the link asked for; kept apart from the choice so a reload of the data doesn't undo a tap. */
  let fromLink: AttendanceChoice | null = null

  /** Takes the token and the preselected answer from the URL fragment (`#t=…&r=going`). Records nothing. */
  function init(hash: string) {
    link.open(hash)
    fromLink = FROM_LINK[link.params.value.r ?? ''] ?? null
    choice.value = fromLink
    data.value = null
    note.value = ''
    saved.value = false
    error.value = null
    if (!token.value) status.value = 'invalid'
  }

  function unauthorized(e: ApiError): boolean {
    if (e.status !== 401) return false
    status.value = e.problem.code === 'member-token.expired' ? 'expired' : 'invalid'
    return true
  }

  function receive(result: MemberAttendance) {
    data.value = result
    // The choice stays whatever the member picked, the link's, or else the answer they gave before.
    choice.value ??= fromLink ?? result.response ?? null
    if (!note.value) note.value = result.note ?? ''
  }

  async function load() {
    if (!token.value) {
      status.value = 'invalid'
      return
    }
    status.value = 'loading'
    error.value = null
    try {
      receive(unwrap(await api.GET('/public/attendance', { params: tokenParams(token.value) })))
      status.value = 'ready'
    } catch (e) {
      const failure = toApiError(e)
      if (unauthorized(failure)) return
      error.value = failure
      status.value = 'error'
    }
  }

  async function submit() {
    if (!choice.value || saving.value || status.value !== 'ready') return
    saving.value = true
    error.value = null
    try {
      const trimmed = note.value.trim()
      const result = unwrap(
        await api.PUT('/public/attendance', {
          params: tokenParams(token.value),
          body: trimmed ? { response: choice.value, note: trimmed } : { response: choice.value },
        }),
      )
      data.value = result
      saved.value = true
    } catch (e) {
      const failure = toApiError(e)
      if (unauthorized(failure)) return
      if (failure.status === 409 && failure.problem.code === 'attendance.closed') {
        // The event started or was cancelled since the page loaded: show it as closed.
        await refresh()
        return
      }
      error.value = failure
    } finally {
      saving.value = false
    }
  }

  async function refresh() {
    try {
      data.value = unwrap(await api.GET('/public/attendance', { params: tokenParams(token.value) }))
    } catch (e) {
      const failure = toApiError(e)
      if (!unauthorized(failure)) error.value = failure
    }
  }

  /** Brings the form back from the confirmation. */
  function edit() {
    saved.value = false
    error.value = null
  }

  return { token, data, choice, note, status, saving, saved, error, init, load, submit, edit }
})
