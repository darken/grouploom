<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import BaseButton from '@/components/BaseButton.vue'
import BaseDialog from '@/components/BaseDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorBanner from '@/components/ErrorBanner.vue'
import EventForm from '@/components/EventForm.vue'
import EventList from '@/components/EventList.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import SegmentedControl from '@/components/SegmentedControl.vue'
import { useToast } from '@/composables/useToast'
import { useEventTypesStore } from '@/stores/eventTypes'
import { useEventsStore, type EventDraft, type EventWhen } from '@/stores/events'

const route = useRoute()
const router = useRouter()
const store = useEventsStore()
const eventTypes = useEventTypesStore()
const toast = useToast()

// Filters and page live in the URL so reload and back/forward keep the view. The URL page is 1-based.
const when = computed<EventWhen>(() => (route.query.when === 'past' ? 'past' : 'upcoming'))
const typeId = computed(() => (typeof route.query.type === 'string' ? route.query.type : ''))
const pageIndex = computed(() => Math.max(0, (Number.parseInt(String(route.query.page ?? '1'), 10) || 1) - 1))

function query(w: EventWhen, type: string, page: number) {
  return {
    ...(w === 'past' ? { when: 'past' } : {}),
    ...(type ? { type } : {}),
    ...(page > 0 ? { page: String(page + 1) } : {}),
  }
}

const linkQuery = computed(() => query(when.value, typeId.value, pageIndex.value))

const retry = () => store.fetch({ when: when.value, eventTypeId: typeId.value, page: pageIndex.value })

// Always load on arrival: events are created, edited and deleted on other pages, and the store's list
// may predate that. Cached rows stay visible (with a progress bar) until the fresh list arrives.
retry()

watch([when, typeId, pageIndex], retry)

// The pickers (form and filter) need every type; a failure just leaves the lists short.
onMounted(() => {
  void eventTypes.fetchActiveOptions().catch(() => undefined)
  void eventTypes.fetchArchivedOptions().catch(() => undefined)
})

const segment = computed({
  get: () => when.value,
  set: (value: string) => router.push({ query: query(value === 'past' ? 'past' : 'upcoming', typeId.value, 0) }),
})

const typeFilter = computed({
  get: () => typeId.value,
  set: (value: string) => router.push({ query: query(when.value, value, 0) }),
})

function goToPage(page: number) {
  router.push({ query: query(when.value, typeId.value, page) })
}

// --- create ---
const formOpen = ref(false)

function openForm() {
  store.clearSaveError()
  formOpen.value = true
}

async function save(payload: EventDraft) {
  const created = await store.create(payload)
  if (!created) return
  formOpen.value = false
  toast.show('Event created')
  await router.push({ path: `/events/${created.id}`, query: linkQuery.value })
}
</script>

<template>
  <section>
    <div class="flex flex-wrap items-end justify-between gap-4">
      <div class="flex items-baseline gap-3">
        <h1 class="text-4xl font-semibold tracking-tight sm:text-5xl">Events</h1>
      </div>
      <BaseButton @click="openForm">
        <svg class="size-4 sm:hidden" viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <path d="M10 4v12M4 10h12" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
        </svg>
        <span class="max-sm:sr-only">New event</span>
      </BaseButton>
    </div>

    <div class="mt-8 flex flex-wrap items-center justify-between gap-3">
      <SegmentedControl
        v-model="segment"
        label="Show"
        :options="[
          { value: 'upcoming', label: 'Upcoming' },
          { value: 'past', label: 'Past' },
        ]"
      />
      <select
        v-model="typeFilter"
        aria-label="Event type"
        class="h-10 max-w-full rounded-full border border-transparent bg-[var(--app-fill)] px-4 text-[15px] text-[var(--app-ink)] focus:border-[var(--app-accent)] focus:ring-4 focus:ring-[var(--app-accent)]/20 focus:outline-none"
      >
        <option value="">All types</option>
        <option v-for="type in eventTypes.activeOptions" :key="type.id" :value="type.id">{{ type.name }}</option>
        <option v-for="type in eventTypes.archivedOptions" :key="type.id" :value="type.id">{{ type.name }} (archived)</option>
      </select>
    </div>

    <div class="mt-6 space-y-4">
      <ErrorBanner v-if="store.error" :error="store.error">
        <BaseButton variant="plain" @click="retry">Retry</BaseButton>
      </ErrorBanner>

      <div v-if="store.loading && store.loaded" role="progressbar" aria-label="Loading" class="h-0.5 overflow-hidden rounded-full bg-[var(--app-fill)]">
        <div class="h-full w-1/3 animate-pulse rounded-full bg-[var(--app-accent)]" />
      </div>

      <div v-if="!store.loaded && store.loading" class="space-y-6">
        <div v-for="n in 2" :key="n">
          <div class="mb-2 h-4 w-20 animate-pulse rounded bg-[var(--app-fill)]" />
          <div class="space-y-px overflow-hidden rounded-2xl border border-[var(--app-line)]/60">
            <div v-for="m in 2" :key="m" data-skeleton class="h-16 animate-pulse bg-[var(--app-fill)]" />
          </div>
        </div>
      </div>

      <template v-else-if="store.loaded">
        <EventList v-if="store.items.length" :items="store.items" :link-query="linkQuery" />
        <EmptyState v-else-if="typeId" title="No events of this type.">
          <BaseButton variant="secondary" @click="typeFilter = ''">Show all types</BaseButton>
        </EmptyState>
        <EmptyState
          v-else-if="when === 'upcoming'"
          title="Nothing scheduled."
          description="Plan your group's next practice, meeting or outing."
        >
          <BaseButton @click="openForm">New event</BaseButton>
        </EmptyState>
        <EmptyState v-else title="No past events yet." />

        <PaginationBar
          v-if="store.pageInfo"
          :page="store.pageInfo.number"
          :size="store.pageInfo.size"
          :total="store.pageInfo.totalElements"
          @update:page="goToPage"
        />
      </template>
    </div>

    <BaseDialog :open="formOpen" title="New event" @close="formOpen = false">
      <EventForm
        :event-types="eventTypes.activeOptions"
        :saving="store.saving"
        :field-errors="store.fieldErrors"
        @submit="save"
        @cancel="formOpen = false"
      />
    </BaseDialog>
  </section>
</template>
