<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import BaseButton from '@/components/BaseButton.vue'
import BaseDialog from '@/components/BaseDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorBanner from '@/components/ErrorBanner.vue'
import EventTypeForm from '@/components/EventTypeForm.vue'
import EventTypeList from '@/components/EventTypeList.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import SegmentedControl from '@/components/SegmentedControl.vue'
import { useToast } from '@/composables/useToast'
import { useEventTypesStore, type EventType, type EventTypeDraft } from '@/stores/eventTypes'

const route = useRoute()
const router = useRouter()
const store = useEventTypesStore()
const toast = useToast()

// Filter and page live in the URL so reload and back/forward keep the view. The URL page is 1-based.
const archivedFilter = computed(() => route.query.archived === '1')
const pageIndex = computed(() => Math.max(0, (Number.parseInt(String(route.query.page ?? '1'), 10) || 1) - 1))

function query(archived: boolean, page: number) {
  return { ...(archived ? { archived: '1' } : {}), ...(page > 0 ? { page: String(page + 1) } : {}) }
}

watch(
  [archivedFilter, pageIndex],
  ([archived, page]) => {
    if (store.loaded && store.archived === archived && store.page === page) return
    store.fetch({ archived, page })
  },
  { immediate: true },
)

const segment = computed({
  get: () => (archivedFilter.value ? 'archived' : 'active'),
  set: (value: string) => router.push({ query: query(value === 'archived', 0) }),
})

function goToPage(page: number) {
  router.push({ query: query(archivedFilter.value, page) })
}

const retry = () => store.fetch({ archived: archivedFilter.value, page: pageIndex.value })

// --- create / edit ---
const formOpen = ref(false)
const editing = ref<EventType | null>(null)

function openForm(item: EventType | null) {
  store.clearSaveError()
  editing.value = item
  formOpen.value = true
}

function closeForm() {
  formOpen.value = false
}

async function save(payload: EventTypeDraft) {
  const current = editing.value
  const ok = current
    ? await store.update(current.id, { ...payload, archived: payload.archived ?? current.archived, version: payload.version ?? current.version })
    : await store.create(payload)
  if (!ok) return
  closeForm()
  toast.show(current ? 'Saved' : 'Event type created')
  await syncPage()
}

async function reloadAfterStale() {
  closeForm()
  await retry()
}

// --- archive / unarchive ---
async function setArchived(item: EventType, archived: boolean) {
  const ok = await store.update(item.id, {
    name: item.name,
    description: item.description,
    color: item.color,
    defaultDurationMinutes: item.defaultDurationMinutes,
    archived,
    version: item.version,
  })
  if (!ok) {
    toast.show(store.stale ? 'Changed by someone else. Reloading…' : `Couldn't ${archived ? 'archive' : 'unarchive'}.`)
    if (store.stale) await retry()
    return
  }
  const saved = store.lastSaved
  toast.show(
    archived ? 'Archived' : 'Unarchived',
    archived && saved ? { label: 'Undo', run: () => void setArchived(saved, false) } : undefined,
  )
}

// --- delete ---
const deleting = ref<EventType | null>(null)

async function confirmDelete() {
  const target = deleting.value
  deleting.value = null
  if (!target) return
  if (await store.remove(target.id)) {
    toast.show('Deleted')
    await syncPage()
  }
}

// The store may have stepped back a page after a delete; keep the URL in step with it.
async function syncPage() {
  if (store.page !== pageIndex.value) await router.replace({ query: query(archivedFilter.value, store.page) })
}
</script>

<template>
  <section>
    <div class="flex flex-wrap items-end justify-between gap-4">
      <div class="flex items-baseline gap-3">
        <h1 class="text-4xl font-semibold tracking-tight sm:text-5xl">Event types</h1>
        <span v-if="store.pageInfo" class="text-2xl text-[var(--app-muted)]" aria-label="Total">{{ store.pageInfo.totalElements }}</span>
      </div>
      <BaseButton @click="openForm(null)">
        <svg class="size-4 sm:hidden" viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <path d="M10 4v12M4 10h12" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
        </svg>
        <span class="max-sm:sr-only">New event type</span>
      </BaseButton>
    </div>

    <div class="mt-8">
      <SegmentedControl
        v-model="segment"
        label="Show"
        :options="[
          { value: 'active', label: 'Active' },
          { value: 'archived', label: 'Archived' },
        ]"
      />
    </div>

    <div class="mt-6 space-y-4">
      <ErrorBanner v-if="store.error" :error="store.error">
        <BaseButton variant="plain" @click="retry">Retry</BaseButton>
      </ErrorBanner>

      <div v-if="store.loading && store.loaded" role="progressbar" aria-label="Loading" class="h-0.5 overflow-hidden rounded-full bg-[var(--app-fill)]">
        <div class="h-full w-1/3 animate-pulse rounded-full bg-[var(--app-accent)]" />
      </div>

      <div v-if="!store.loaded && store.loading" class="space-y-px overflow-hidden rounded-2xl border border-[var(--app-line)]/60">
        <div v-for="n in 3" :key="n" data-skeleton class="h-16 animate-pulse bg-[var(--app-fill)]" />
      </div>

      <template v-else-if="store.loaded">
        <EventTypeList
          v-if="store.items.length"
          :items="store.items"
          @edit="openForm"
          @archive="setArchived($event, true)"
          @unarchive="setArchived($event, false)"
          @delete="deleting = $event"
        />
        <EmptyState
          v-else-if="!archivedFilter"
          title="No event types yet"
          description="Event types let you group similar events, like practices or meetings."
        >
          <BaseButton @click="openForm(null)">Create event type</BaseButton>
        </EmptyState>
        <EmptyState v-else title="No archived event types." />

        <PaginationBar
          v-if="store.pageInfo"
          :page="store.pageInfo.number"
          :size="store.pageInfo.size"
          :total="store.pageInfo.totalElements"
          @update:page="goToPage"
        />
      </template>
    </div>

    <BaseDialog :open="formOpen" :title="editing ? 'Edit event type' : 'New event type'" @close="closeForm">
      <div
        v-if="store.stale"
        role="alert"
        class="mb-5 rounded-xl border border-[var(--app-danger)]/30 p-4 text-[15px]"
      >
        <p class="font-medium">Changed by someone else</p>
        <p class="text-[var(--app-muted)]">This event type was updated while you were editing it.</p>
        <BaseButton class="mt-3" variant="secondary" @click="reloadAfterStale">Reload</BaseButton>
      </div>
      <EventTypeForm
        :key="editing?.id ?? 'new'"
        :event-type="editing ?? undefined"
        :saving="store.saving"
        :field-errors="store.fieldErrors"
        @submit="save"
        @cancel="closeForm"
      />
    </BaseDialog>

    <ConfirmDialog
      :open="deleting !== null"
      :title="`Delete '${deleting?.name ?? ''}'?`"
      message="This can't be undone."
      confirm-label="Delete"
      destructive
      @confirm="confirmDelete"
      @cancel="deleting = null"
    />
  </section>
</template>
