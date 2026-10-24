import { ref } from 'vue'
import { defineStore } from 'pinia'

import { api } from '@/api/client'
import { ApiError, unwrap } from '@/api/errors'
import type { components } from '@/api/schema'
import type { PageInfo } from '@/stores/eventTypes'

export type AttendanceSummary = components['schemas']['AttendanceSummaryResponse']
export type AttendanceRow = components['schemas']['AttendanceRowResponse']
export type AttendanceAnswer = components['schemas']['SetAttendanceRequest']['response']
export type AttendanceFilter = 'ALL' | 'GOING' | 'NOT_GOING' | 'NO_REPLY'

const PAGE_SIZE = 50

function toApiError(e: unknown): ApiError {
  return e instanceof ApiError
    ? e
    : new ApiError({ title: 'Network error', detail: 'Could not reach the server.' }, 0)
}

/** The attendance of the event being looked at on the event page. */
export const useAttendanceStore = defineStore('attendance', () => {
  const eventId = ref('')
  const summary = ref<AttendanceSummary | null>(null)
  const rows = ref<AttendanceRow[]>([])
  const pageInfo = ref<PageInfo | null>(null)
  const page = ref(0)
  const filter = ref<AttendanceFilter>('ALL')
  const loading = ref(false)
  const error = ref<ApiError | null>(null)

  // Only the latest request may apply its result, so a slow response can't replace a newer one.
  let latestRows = 0
  let latestSummary = 0

  async function fetchSummary(id: string) {
    const request = ++latestSummary
    // Don't show another event's numbers while this one loads; a reload of the same event keeps them.
    if (eventId.value !== id) {
      eventId.value = id
      summary.value = null
      rows.value = []
      pageInfo.value = null
      page.value = 0
      filter.value = 'ALL'
    }
    try {
      const data = unwrap(await api.GET('/events/{eventId}/attendance/summary', { params: { path: { eventId: id } } }))
      if (request === latestSummary) summary.value = data
    } catch (e) {
      if (request === latestSummary) error.value = toApiError(e)
    }
  }

  async function fetchRows(options: { filter: AttendanceFilter; page: number }, quiet = false) {
    const request = ++latestRows
    const id = eventId.value
    filter.value = options.filter
    page.value = options.page
    if (!quiet) loading.value = true
    error.value = null
    try {
      const data = unwrap(
        await api.GET('/events/{eventId}/attendance', {
          params: { path: { eventId: id }, query: { filter: options.filter, page: options.page, size: PAGE_SIZE } },
        }),
      )
      if (request !== latestRows) return
      rows.value = data.content
      pageInfo.value = {
        number: data.page.number ?? 0,
        size: data.page.size ?? PAGE_SIZE,
        totalElements: data.page.totalElements ?? 0,
        totalPages: data.page.totalPages ?? 1,
      }
    } catch (e) {
      if (request === latestRows) error.value = toApiError(e)
    } finally {
      if (request === latestRows) loading.value = false
    }
  }

  const replaceRow = (memberId: string, next: AttendanceRow) => {
    rows.value = rows.value.map((r) => (r.member.id === memberId ? next : r))
  }

  /** Looks again at the numbers and the current page, so a changed answer leaves a filtered list it no longer fits. */
  async function refresh() {
    await Promise.all([fetchSummary(eventId.value), fetchRows({ filter: filter.value, page: page.value }, true)])
  }

  /** Shows the new answer at once and puts the old one back if the server refuses it. */
  async function change(memberId: string, optimistic: (row: AttendanceRow) => AttendanceRow, send: () => Promise<AttendanceRow | void>) {
    const previous = rows.value.find((r) => r.member.id === memberId)
    if (!previous) return
    error.value = null
    replaceRow(memberId, optimistic(previous))
    try {
      const saved = await send()
      if (saved) replaceRow(memberId, saved)
    } catch (e) {
      replaceRow(memberId, previous)
      error.value = toApiError(e)
      return
    }
    await refresh()
  }

  const set = (memberId: string, response: AttendanceAnswer) =>
    change(
      memberId,
      (row) => ({ member: row.member, response, source: 'ORGANIZER', respondedAt: new Date().toISOString() }),
      async () =>
        unwrap(
          await api.PUT('/events/{eventId}/attendance/{memberId}', {
            params: { path: { eventId: eventId.value, memberId } },
            body: { response },
          }),
        ),
    )

  const clear = (memberId: string) =>
    change(
      memberId,
      (row) => ({ member: row.member }),
      async () => {
        const result = await api.DELETE('/events/{eventId}/attendance/{memberId}', {
          params: { path: { eventId: eventId.value, memberId } },
        })
        if (result.error !== undefined) unwrap({ ...result, data: undefined })
      },
    )

  return { eventId, summary, rows, pageInfo, page, filter, loading, error, fetchSummary, fetchRows, set, clear }
})
