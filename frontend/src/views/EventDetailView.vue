<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import BaseButton from '@/components/BaseButton.vue'
import BaseDialog from '@/components/BaseDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorBanner from '@/components/ErrorBanner.vue'
import EventForm from '@/components/EventForm.vue'
import { useToast } from '@/composables/useToast'
import { useEventTypesStore } from '@/stores/eventTypes'
import { useEventsStore, type EventDraft } from '@/stores/events'
import { eventTypeColor } from '@/ui/eventTypeColors'
import { formatEventTime } from '@/ui/formatEventTime'
import { browserTimeZone } from '@/ui/timeZones'

const route = useRoute()
const router = useRouter()
const store = useEventsStore()
const eventTypes = useEventTypesStore()
const toast = useToast()

const id = computed(() => String(route.params.id))
// The list's query state (when, type, page) rides along so the back link restores that view.
const listQuery = computed(() => route.query)
const event = computed(() => store.current)
const notFound = computed(() => store.currentError?.status === 404)

watch(id, (value) => store.fetchOne(value), { immediate: true })

watch(
  () => event.value?.title,
  (title) => {
    if (title) document.title = `${title} · GroupLoom`
  },
)

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
  toast.show('Saved')
}

async function reloadAfterStale() {
  formOpen.value = false
  await retry()
}

// --- delete ---
const menuOpen = ref(false)
const menu = ref<HTMLElement | null>(null)
const confirmingDelete = ref(false)

function onPointerDown(e: Event) {
  if (menuOpen.value && menu.value && !menu.value.contains(e.target as Node)) menuOpen.value = false
}

onMounted(() => document.addEventListener('pointerdown', onPointerDown))
onBeforeUnmount(() => document.removeEventListener('pointerdown', onPointerDown))

function askDelete() {
  menuOpen.value = false
  confirmingDelete.value = true
}

async function confirmDelete() {
  confirmingDelete.value = false
  const current = event.value
  if (!current) return
  if (await store.remove(current.id)) {
    toast.show('Deleted')
    await router.push({ path: '/events', query: listQuery.value })
  }
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
      Events
    </RouterLink>

    <div v-if="store.currentLoading && !event" data-skeleton class="mt-6 space-y-4" aria-busy="true">
      <div class="h-12 w-2/3 animate-pulse rounded-xl bg-[var(--app-fill)]" />
      <div class="h-6 w-1/2 animate-pulse rounded-lg bg-[var(--app-fill)]" />
      <div class="h-24 animate-pulse rounded-2xl bg-[var(--app-fill)]" />
    </div>

    <EmptyState
      v-else-if="notFound"
      class="mt-8"
      title="This event doesn't exist or was deleted."
    >
      <RouterLink
        :to="{ path: '/events', query: listQuery }"
        class="inline-flex min-h-11 items-center rounded-full bg-[var(--app-accent)] px-5 py-2 text-[15px] font-medium text-white hover:bg-[var(--app-accent-hover)]"
      >
        Back to events
      </RouterLink>
    </EmptyState>

    <ErrorBanner v-else-if="store.currentError && !event" class="mt-6" :error="store.currentError">
      <BaseButton variant="plain" @click="retry">Retry</BaseButton>
    </ErrorBanner>

    <article v-else-if="event" class="mt-4">
      <div class="flex items-start justify-between gap-4">
        <h1 class="min-w-0 text-4xl font-semibold tracking-tight break-words sm:text-5xl">{{ event.title }}</h1>
        <div class="flex shrink-0 items-center gap-2 pt-1">
          <BaseButton variant="secondary" @click="openForm">Edit</BaseButton>
          <div ref="menu" class="relative">
            <button
              type="button"
              aria-haspopup="menu"
              :aria-expanded="menuOpen"
              aria-label="More actions"
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
              <button role="menuitem" type="button" class="block w-full rounded-lg px-3 py-2 text-left text-[15px] text-[var(--app-danger)] hover:bg-[var(--app-fill)]" @click="askDelete">Delete</button>
            </div>
          </div>
        </div>
      </div>

      <p
        data-type-chip
        class="mt-4 inline-flex items-center gap-2 rounded-full bg-[var(--app-fill)] px-3 py-1 text-sm font-medium"
      >
        <span data-color-dot class="size-2.5 rounded-full" :class="eventTypeColor(event.eventType.color).dot" aria-hidden="true" />
        {{ event.eventType.name }}<template v-if="event.eventType.archived"> (archived)</template>
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
    </article>

    <BaseDialog :open="formOpen" title="Edit event" @close="formOpen = false">
      <div
        v-if="store.stale"
        role="alert"
        class="mb-5 rounded-xl border border-[var(--app-danger)]/30 p-4 text-[15px]"
      >
        <p class="font-medium">Changed by someone else</p>
        <p class="text-[var(--app-muted)]">This event was updated while you were editing it.</p>
        <BaseButton class="mt-3" variant="secondary" @click="reloadAfterStale">Reload</BaseButton>
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

    <ConfirmDialog
      :open="confirmingDelete"
      :title="`Delete '${event?.title ?? ''}'?`"
      message="This can't be undone."
      confirm-label="Delete"
      destructive
      @confirm="confirmDelete"
      @cancel="confirmingDelete = false"
    />
  </section>
</template>
