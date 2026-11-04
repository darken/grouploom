<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import AttendanceList from '@/components/AttendanceList.vue'
import AttendanceSummary from '@/components/AttendanceSummary.vue'
import BaseButton from '@/components/BaseButton.vue'
import BaseDialog from '@/components/BaseDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import CancelEventDialog from '@/components/CancelEventDialog.vue'
import ErrorBanner from '@/components/ErrorBanner.vue'
import EventForm from '@/components/EventForm.vue'
import NotificationList from '@/components/NotificationList.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import ScheduleNotificationDialog from '@/components/ScheduleNotificationDialog.vue'
import SegmentedControl from '@/components/SegmentedControl.vue'
import { useToast } from '@/composables/useToast'
import { useAttendanceStore, type AttendanceAnswer, type AttendanceFilter } from '@/stores/attendance'
import { useEventNotificationsStore, type AudienceKind, type ScheduleNotificationInput } from '@/stores/eventNotifications'
import { useEventTypesStore } from '@/stores/eventTypes'
import { useEventsStore, type EventDraft } from '@/stores/events'
import { eventTypeColor } from '@/ui/eventTypeColors'
import { formatEventTime } from '@/ui/formatEventTime'
import { describeSendTime, formatMonthDay } from '@/ui/notificationText'
import { browserTimeZone } from '@/ui/timeZones'

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const store = useEventsStore()
const eventTypes = useEventTypesStore()
const notifications = useEventNotificationsStore()
const attendance = useAttendanceStore()
const toast = useToast()

const id = computed(() => String(route.params.id))
// The list's query state (when, type, page) rides along so the back link restores that view.
// The attendance filter belongs to this page only.
const listQuery = computed(() => Object.fromEntries(Object.entries(route.query).filter(([key]) => key !== 'attendance')))
const event = computed(() => store.current)
const notFound = computed(() => store.currentError?.status === 404)

// --- attendance ---
const FILTER_QUERY = { going: 'GOING', 'not-going': 'NOT_GOING', 'no-reply': 'NO_REPLY' } as const
const filterOptions = computed(() => [
  { value: 'ALL', label: t('attendance.all') },
  { value: 'GOING', label: t('attendance.going') },
  { value: 'NOT_GOING', label: t('attendance.notGoing') },
  { value: 'NO_REPLY', label: t('attendance.noReply') },
])

/** The filter lives in the URL (`?attendance=going`), so a reload or a shared link shows the same list. */
const attendanceFilter = computed<AttendanceFilter>({
  get() {
    const value = String(route.query.attendance ?? '')
    return value in FILTER_QUERY ? FILTER_QUERY[value as keyof typeof FILTER_QUERY] : 'ALL'
  },
  set(filter) {
    const rest = { ...route.query }
    delete rest.attendance
    const key = Object.entries(FILTER_QUERY).find(([, value]) => value === filter)?.[0]
    void router.replace({ query: key ? { ...rest, attendance: key } : rest })
  },
})

watch(attendanceFilter, (filter) => void attendance.fetchRows({ filter, page: 0 }))

const attendancePending = computed(() => attendance.summary === null && attendance.rows.length === 0)

async function changeAnswer(action: () => Promise<void>) {
  await action()
  if (attendance.error) toast.show(t('events.toast.answerFailed'))
}

const setAnswer = (memberId: string, response: AttendanceAnswer) => changeAnswer(() => attendance.set(memberId, response))
const clearAnswer = (memberId: string) => changeAnswer(() => attendance.clear(memberId))

watch(
  id,
  (value) => {
    void store.fetchOne(value)
    void notifications.fetch(value)
    notifications.start(value)
    void attendance.fetchSummary(value)
    void attendance.fetchRows({ filter: attendanceFilter.value, page: 0 })
  },
  { immediate: true },
)

// The router sets a generic page title; the event's own title replaces it (and again after a language change).
watch([() => event.value?.title, locale], ([title]) => {
  if (title) document.title = `${title} · GroupLoom`
})

onMounted(() => void eventTypes.fetchActiveOptions().catch(() => undefined))

const retry = () => store.fetchOne(id.value)

// --- edit ---
const formOpen = ref(false)

function openForm() {
  store.clearSaveError()
  formOpen.value = true
}

async function save(payload: EventDraft) {
  const current = event.value
  if (!current) return
  if (!(await store.update(current.id, { ...payload, version: payload.version ?? current.version }))) return
  formOpen.value = false
  toast.show(t('events.toast.saved'))
}

async function reloadAfterStale() {
  formOpen.value = false
  await retry()
}

// --- delete ---
const menuOpen = ref(false)
const menu = ref<HTMLElement | null>(null)
const confirmingDelete = ref(false)
const deleteBlocked = ref(false)

function onPointerDown(e: Event) {
  if (menuOpen.value && menu.value && !menu.value.contains(e.target as Node)) menuOpen.value = false
}

onMounted(() => document.addEventListener('pointerdown', onPointerDown))
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onPointerDown)
  notifications.stop()
})

function askDelete() {
  menuOpen.value = false
  confirmingDelete.value = true
}

async function confirmDelete() {
  confirmingDelete.value = false
  const current = event.value
  if (!current) return
  const result = await store.remove(current.id)
  if (result === 'ok') {
    toast.show(t('events.toast.deleted'))
    await router.push({ path: '/events', query: listQuery.value })
  } else if (result === 'has-notifications') {
    deleteBlocked.value = true
  }
}

// --- cancel ---
const cancelling = ref(false)
const cancelError = ref('')

const cancelled = computed(() => Boolean(event.value?.cancelledAt))
const started = computed(() => (event.value ? Date.parse(event.value.start) <= Date.now() : false))
const ended = computed(() => (event.value ? Date.parse(event.value.end) <= Date.now() : false))
const canCancel = computed(() => !cancelled.value && !ended.value)
const canSchedule = computed(() => !cancelled.value && !started.value)
const notified = computed(() => notifications.items.some((n) => n.status === 'SENT' || n.status === 'SENDING'))

function openCancel() {
  menuOpen.value = false
  deleteBlocked.value = false
  cancelError.value = ''
  cancelling.value = true
}

async function submitCancel(message: string) {
  const current = event.value
  if (!current) return
  const result = await store.cancel(current.id, message)
  if (result.status === 'ok') {
    cancelling.value = false
    toast.show(t('events.toast.cancelled'))
    void notifications.fetch(current.id)
  } else if (result.status === 'cancelled') {
    cancelError.value = t('events.error.alreadyCancelled')
  } else if (result.status === 'ended') {
    cancelError.value = t('events.error.alreadyEnded')
  } else {
    cancelError.value = store.saveError?.message ?? t('events.error.cancelFailed')
  }
}

// --- notifications ---
const scheduling = ref(false)
const scheduleError = ref('')

function openSchedule() {
  const current = event.value
  if (!current) return
  notifications.clearSaveError()
  scheduleError.value = ''
  void notifications.fetchAudience(current.id, 'INVITATION')
  scheduling.value = true
}

const loadAudience = (kind: AudienceKind) => {
  if (event.value) void notifications.fetchAudience(event.value.id, kind)
}

const SCHEDULE_MESSAGES = {
  limit: 'events.error.scheduleLimit',
  cancelled: 'events.error.scheduleCancelled',
  started: 'events.error.scheduleStarted',
  error: 'events.error.scheduleFailed',
} as const

async function submitSchedule(input: ScheduleNotificationInput) {
  const kind = input.kind === 'INVITATION' ? 'INVITATION' : 'REMINDER'
  const result = await notifications.schedule(input, (delivered) => {
    const count = delivered?.recipientCount
    if (count === undefined) return
    toast.show(count === 0 ? t('events.toast.noOne') : t(`events.toast.sent.${kind}`, count))
  })
  if (result.status === 'ok') {
    scheduling.value = false
    toast.show(
      input.timing === 'NOW'
        ? t(`events.toast.sending.${kind}`)
        : t(`events.toast.scheduled.${kind}`, { when: describeSendTime(result.notification) }),
    )
  } else if (result.status !== 'invalid') {
    scheduleError.value = t(SCHEDULE_MESSAGES[result.status])
  }
}

async function unschedule(notificationId: string) {
  const result = await notifications.unschedule(notificationId)
  if (result.status === 'ok') toast.show(t('events.toast.unscheduled'))
  else if (result.status === 'already-sent') toast.show(t('events.toast.alreadySent'))
}
</script>

<template>
  <section>
    <RouterLink
      data-back
      :to="{ path: '/events', query: listQuery }"
      class="-ml-1 inline-flex items-center gap-1 rounded-md px-1 text-[15px] text-[var(--app-accent)] hover:underline"
    >
      <svg class="size-4" viewBox="0 0 20 20" fill="none" aria-hidden="true">
        <path d="m12 4-6 6 6 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
      {{ t('events.title') }}
    </RouterLink>

    <div v-if="store.currentLoading && !event" data-skeleton class="mt-6 space-y-4" aria-busy="true">
      <div class="h-12 w-2/3 animate-pulse rounded-xl bg-[var(--app-fill)]" />
      <div class="h-6 w-1/2 animate-pulse rounded-lg bg-[var(--app-fill)]" />
      <div class="h-24 animate-pulse rounded-2xl bg-[var(--app-fill)]" />
    </div>

    <EmptyState
      v-else-if="notFound"
      class="mt-8"
:title="t('events.detail.notFound')"
    >
      <RouterLink
        :to="{ path: '/events', query: listQuery }"
        class="inline-flex min-h-11 items-center rounded-full bg-[var(--app-accent)] px-5 py-2 text-[15px] font-medium text-white hover:bg-[var(--app-accent-hover)]"
      >
        {{ t('events.detail.backToEvents') }}
      </RouterLink>
    </EmptyState>

    <ErrorBanner v-else-if="store.currentError && !event" class="mt-6" :error="store.currentError">
      <BaseButton variant="plain" @click="retry">{{ t('common.retry') }}</BaseButton>
    </ErrorBanner>

    <article v-else-if="event" class="mt-4">
      <div class="flex items-start justify-between gap-4 max-sm:ja:flex-wrap">
        <h1 class="min-w-0 text-4xl font-semibold tracking-tight break-words sm:text-5xl max-sm:ja:text-3xl" :class="cancelled && 'line-through decoration-2'">{{ event.title }}</h1>
        <div class="flex shrink-0 items-center gap-2 pt-1">
          <BaseButton v-if="!cancelled" variant="secondary" @click="openForm">{{ t('events.detail.edit') }}</BaseButton>
          <div ref="menu" class="relative">
            <button
              type="button"
              aria-haspopup="menu"
              :aria-expanded="menuOpen"
              :aria-label="t('events.detail.moreActions')"
              class="grid size-11 place-items-center rounded-full text-[var(--app-muted)] hover:bg-[var(--app-fill)]"
              @click="menuOpen = !menuOpen"
            >
              <svg class="size-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                <circle cx="4.5" cy="10" r="1.5" /><circle cx="10" cy="10" r="1.5" /><circle cx="15.5" cy="10" r="1.5" />
              </svg>
            </button>
            <div
              v-if="menuOpen"
              role="menu"
              class="absolute right-0 z-10 mt-1 w-44 rounded-xl border border-[var(--app-line)]/60 bg-[var(--app-surface)] p-1 shadow-[0_8px_30px_rgba(0,0,0,0.1)]"
              @keydown.esc="menuOpen = false"
            >
              <button v-if="canCancel" role="menuitem" type="button" class="block w-full rounded-lg px-3 py-2 text-left text-[15px] text-[var(--app-danger)] hover:bg-[var(--app-fill)]" @click="openCancel">{{ t('events.detail.cancelMenu') }}</button>
              <button role="menuitem" type="button" class="block w-full rounded-lg px-3 py-2 text-left text-[15px] text-[var(--app-danger)] hover:bg-[var(--app-fill)]" @click="askDelete">{{ t('common.delete') }}</button>
            </div>
          </div>
        </div>
      </div>

      <p v-if="event.cancelledAt" data-cancelled-banner class="mt-3 rounded-xl bg-[var(--app-fill)] px-4 py-3 text-[15px]">
        <span class="font-medium text-[var(--app-danger)]">{{ t('events.detail.cancelledBanner', { date: formatMonthDay(event.cancelledAt, event.timeZone) }) }}</span>
        <span v-if="event.cancellationMessage" class="mt-1 block whitespace-pre-line text-[var(--app-muted)]">{{ event.cancellationMessage }}</span>
      </p>

      <p
        data-type-chip
        class="mt-4 inline-flex items-center gap-2 rounded-full bg-[var(--app-fill)] px-3 py-1 text-sm font-medium"
      >
        <span data-color-dot class="size-2.5 rounded-full" :class="eventTypeColor(event.eventType.color).dot" aria-hidden="true" />
        {{ event.eventType.archived ? t('common.archivedName', { name: event.eventType.name }) : event.eventType.name }}
      </p>

      <p data-time class="mt-4 text-xl text-[var(--app-muted)] tabular-nums sm:text-2xl">
        {{ formatEventTime(event, browserTimeZone()) }}
      </p>

      <p v-if="event.location" data-location class="mt-3 flex items-center gap-2 text-[17px]">
        <svg class="size-4 shrink-0 text-[var(--app-muted)]" viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <path d="M10 18s6-5.2 6-10a6 6 0 1 0-12 0c0 4.8 6 10 6 10Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" />
          <circle cx="10" cy="8" r="2" stroke="currentColor" stroke-width="1.6" />
        </svg>
        {{ event.location }}
      </p>

      <p v-if="event.description" data-description class="mt-6 max-w-2xl text-[17px] leading-relaxed whitespace-pre-line">{{ event.description }}</p>

      <section data-attendance class="mt-10" aria-labelledby="attendance-heading">
        <div class="mb-3 flex flex-wrap items-center justify-between gap-x-4">
          <h2 id="attendance-heading" class="text-xl font-semibold tracking-tight">{{ t('attendance.title') }}</h2>
          <AttendanceSummary
            v-if="attendance.summary"
            :summary="attendance.summary"
            :filter="attendanceFilter"
            @select="attendanceFilter = $event"
          />
        </div>
        <ErrorBanner v-if="attendance.error && attendancePending" :error="attendance.error">
          <BaseButton variant="plain" @click="attendance.fetchSummary(event.id); attendance.fetchRows({ filter: attendanceFilter, page: 0 })">{{ t('common.retry') }}</BaseButton>
        </ErrorBanner>
        <EmptyState v-else-if="attendance.summary?.members === 0" :title="t('attendance.addMembers')">
          <RouterLink
            to="/members"
            class="inline-flex min-h-11 items-center rounded-full bg-[var(--app-accent)] px-5 py-2 text-[15px] font-medium text-white hover:bg-[var(--app-accent-hover)]"
          >
            {{ t('attendance.goToMembers') }}
          </RouterLink>
        </EmptyState>
        <template v-else-if="!attendancePending">
          <SegmentedControl v-model="attendanceFilter" :options="filterOptions" :label="t('attendance.filterLabel')" />
          <AttendanceList
            v-if="attendance.rows.length > 0"
            class="mt-4"
            :items="attendance.rows"
            :read-only="cancelled"
            @set="setAnswer"
            @clear="clearAnswer"
          />
          <p
            v-else-if="!attendance.loading"
            class="mt-4 rounded-2xl border border-dashed border-[var(--app-line)] px-4 py-8 text-center text-[15px] text-[var(--app-muted)]"
          >
            {{ t('attendance.nobody') }}
          </p>
          <PaginationBar
            v-if="attendance.pageInfo"
            class="mt-3"
            :page="attendance.page"
            :size="attendance.pageInfo.size"
            :total="attendance.pageInfo.totalElements"
            @update:page="attendance.fetchRows({ filter: attendanceFilter, page: $event })"
          />
        </template>
      </section>

      <section data-notifications class="mt-10" aria-labelledby="notifications-heading">
        <div class="mb-3 flex items-center justify-between gap-3">
          <h2 id="notifications-heading" class="text-xl font-semibold tracking-tight">{{ t('events.detail.notifications') }}</h2>
          <BaseButton v-if="canSchedule" variant="secondary" @click="openSchedule">{{ t('events.detail.schedule') }}</BaseButton>
        </div>
        <ErrorBanner v-if="notifications.error && !notifications.loaded" :error="notifications.error">
          <BaseButton variant="plain" @click="notifications.fetch(event.id)">{{ t('common.retry') }}</BaseButton>
        </ErrorBanner>
        <NotificationList v-else-if="notifications.items.length > 0" :items="notifications.items" @unschedule="unschedule" />
        <EmptyState
          v-else-if="notifications.loaded"
          :title="t('events.detail.noNotifications')"
          :description="canSchedule ? t('events.detail.inviteByEmail') : undefined"
        >
          <BaseButton v-if="canSchedule" @click="openSchedule">{{ t('events.detail.scheduleInvitation') }}</BaseButton>
        </EmptyState>
      </section>
    </article>

    <BaseDialog :open="formOpen" :title="t('events.detail.editTitle')" @close="formOpen = false">
      <div
        v-if="store.stale"
        role="alert"
        class="mb-5 rounded-xl border border-[var(--app-danger)]/30 p-4 text-[15px]"
      >
        <p class="font-medium">{{ t('events.detail.staleTitle') }}</p>
        <p class="text-[var(--app-muted)]">{{ t('events.detail.staleBody') }}</p>
        <BaseButton class="mt-3" variant="secondary" @click="reloadAfterStale">{{ t('events.detail.reload') }}</BaseButton>
      </div>
      <EventForm
        v-if="event"
        :key="`${event.id}-${event.version}`"
        :event="event"
        :event-types="eventTypes.activeOptions"
        :saving="store.saving"
        :field-errors="store.fieldErrors"
        @submit="save"
        @cancel="formOpen = false"
      />
    </BaseDialog>

    <ScheduleNotificationDialog
      v-if="event"
      :open="scheduling"
      :event="event"
      :audience="notifications.audience"
      :saving="notifications.saving"
      :field-errors="notifications.fieldErrors"
      :error="scheduleError"
      @submit="submitSchedule"
      @kind-change="loadAudience"
      @close="scheduling = false"
    />

    <CancelEventDialog
      :open="cancelling"
      :title="event?.title ?? ''"
      :notified="notified"
      :saving="store.saving"
      :error="cancelError"
      @submit="submitCancel"
      @close="cancelling = false"
    />

    <ConfirmDialog
      v-if="event?.cancelledAt"
      :open="deleteBlocked"
      :title="t('events.detail.cantDeleteTitle', { title: event.title })"
      :message="t('events.detail.cantDeleteMessage')"
      :confirm-label="t('events.detail.ok')"
      hide-cancel
      @confirm="deleteBlocked = false"
      @cancel="deleteBlocked = false"
    />
    <ConfirmDialog
      v-else
      :open="deleteBlocked"
      :title="t('events.detail.deleteTitle', { title: event?.title ?? '' })"
      :message="t('events.detail.deleteNotifiedMessage')"
      :confirm-label="t('events.detail.cancelMenu')"
      @confirm="openCancel"
      @cancel="deleteBlocked = false"
    />

    <ConfirmDialog
      :open="confirmingDelete"
      :title="t('events.detail.deleteTitle', { title: event?.title ?? '' })"
      :message="t('events.detail.deleteMessage')"
      :confirm-label="t('common.delete')"
      destructive
      @confirm="confirmDelete"
      @cancel="confirmingDelete = false"
    />
  </section>
</template>
