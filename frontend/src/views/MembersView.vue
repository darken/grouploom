<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, useTemplateRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import BaseButton from '@/components/BaseButton.vue'
import BaseDialog from '@/components/BaseDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorBanner from '@/components/ErrorBanner.vue'
import MemberForm from '@/components/MemberForm.vue'
import MemberList from '@/components/MemberList.vue'
import OverflowMenu from '@/components/OverflowMenu.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import SearchField from '@/components/SearchField.vue'
import { useToast } from '@/composables/useToast'
import { useMembersStore, type Member, type MemberDraft } from '@/stores/members'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const store = useMembersStore()
const toast = useToast()

// Search and page live in the URL so reload and back/forward keep the view. The URL page is 1-based.
const q = computed(() => (typeof route.query.q === 'string' ? route.query.q : ''))
const pageIndex = computed(() => Math.max(0, (Number.parseInt(String(route.query.page ?? '1'), 10) || 1) - 1))

function query(search: string, page: number) {
  return { ...(search.trim() ? { q: search.trim() } : {}), ...(page > 0 ? { page: String(page + 1) } : {}) }
}

watch(
  [q, pageIndex],
  ([search, page]) => {
    if (store.loaded && store.query === search && store.page === page) return
    store.fetch({ q: search, page })
  },
  { immediate: true },
)

// Searching always goes back to the first page.
const search = computed({
  get: () => q.value,
  set: (value: string) => router.push({ query: query(value, 0) }),
})

function goToPage(page: number) {
  router.push({ query: query(q.value, page) })
}

const retry = () => store.fetch({ q: q.value, page: pageIndex.value })

const countLabel = computed(() => {
  const total = store.pageInfo?.totalElements ?? 0
  return t('members.count', total)
})

// --- create / edit ---
const formOpen = ref(false)
const editing = ref<Member | null>(null)
const form = useTemplateRef<InstanceType<typeof MemberForm>>('form')

function openForm(item: Member | null) {
  store.clearSaveError()
  editing.value = item
  formOpen.value = true
}

function closeForm() {
  formOpen.value = false
}

async function save(payload: MemberDraft) {
  const current = editing.value
  const ok = current
    ? await store.update(current.id, { ...payload, version: payload.version ?? current.version })
    : await store.create(payload)
  if (!ok) return
  closeForm()
  toast.show(current ? t('common.saveStatus.saved') : t('members.toast.added'))
}

async function saveAndAddAnother(payload: MemberDraft) {
  if (!(await store.create(payload))) return
  toast.show(t('members.toast.addedNamed', { name: payload.name }))
  form.value?.reset()
}

async function reloadAfterStale() {
  closeForm()
  await retry()
}

// --- email preferences links ---
async function sendLink(target: Member) {
  const result = await store.sendPreferencesEmail(target.id)
  if (result === 'queued') toast.show(t('members.toast.sendingLink', { name: target.name }))
  else if (result === 'cooldown') toast.show(t('members.toast.cooldown', { name: target.name }))
  else toast.show(t('members.toast.sendFailed', { name: target.name }))
}

const bulkItems = computed(() => [
  {
    id: 'email-all',
    label: t('members.emailAll'),
    // Unknown while searching: an empty result says nothing about the rest of the group.
    disabled: !store.loaded || (!q.value.trim() && (store.pageInfo?.totalElements ?? 0) === 0),
  },
])

const confirmingBulk = ref(false)

// The total counts search matches, but the send goes to the whole group, so no count while searching.
const bulkMessage = computed(() => {
  if (q.value.trim()) return t('members.bulk.everyone')
  return t('members.bulk.count', store.pageInfo?.totalElements ?? 0)
})

async function confirmBulk() {
  confirmingBulk.value = false
  const result = await store.sendPreferencesEmailToAll()
  if (!result) {
    toast.show(t('members.toast.bulkFailed'))
    return
  }
  const skipped = result.skipped > 0 ? t('members.toast.skipped', { n: result.skipped }) : ''
  toast.show(
    result.queued === 0
      ? `${t('members.toast.noLinks')}${skipped}`
      : `${t('members.toast.sendingLinks', result.queued)}${skipped}`,
  )
}

// Only a link still being sent can change behind our back, so that is the only reason to look again.
function onFocus() {
  if (store.hasPendingEmail && !store.loading) void store.refresh()
}

onMounted(() => window.addEventListener('focus', onFocus))
onBeforeUnmount(() => window.removeEventListener('focus', onFocus))

// --- remove ---
const removing = ref<Member | null>(null)

async function confirmRemove() {
  const target = removing.value
  removing.value = null
  if (!target) return
  if (await store.remove(target.id)) {
    toast.show(t('members.toast.removed'))
    // The store may have stepped back a page; keep the URL in step with it.
    if (store.page !== pageIndex.value) await router.replace({ query: query(q.value, store.page) })
  }
}
</script>

<template>
  <section>
    <div class="flex flex-wrap items-end justify-between gap-x-4 gap-y-4">
      <div class="flex items-baseline gap-3">
        <h1 class="text-4xl font-semibold tracking-tight sm:text-5xl">{{ t('nav.members') }}</h1>
        <span v-if="store.pageInfo" class="text-lg text-[var(--app-muted)]">{{ countLabel }}</span>
      </div>
      <div class="order-last w-full sm:order-none sm:ml-auto sm:w-72">
        <SearchField v-model="search" />
      </div>
      <div class="ml-auto flex items-center gap-2">
        <OverflowMenu :label="t('members.moreActions')" :items="bulkItems" @select="confirmingBulk = true" />
        <BaseButton @click="openForm(null)">
          <svg class="size-4 sm:hidden" viewBox="0 0 20 20" fill="none" aria-hidden="true">
            <path d="M10 4v12M4 10h12" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
          </svg>
          <span class="max-sm:sr-only">{{ t('members.add') }}</span>
        </BaseButton>
      </div>
    </div>

    <div class="mt-8 space-y-4">
      <ErrorBanner v-if="store.error" :error="store.error">
        <BaseButton variant="plain" @click="retry">{{ t('common.retry') }}</BaseButton>
      </ErrorBanner>

      <div v-if="store.loading && store.loaded" role="progressbar" :aria-label="t('common.loading')" class="h-0.5 overflow-hidden rounded-full bg-[var(--app-fill)]">
        <div class="h-full w-1/3 animate-pulse rounded-full bg-[var(--app-accent)]" />
      </div>

      <div v-if="!store.loaded && store.loading" class="space-y-px overflow-hidden rounded-2xl border border-[var(--app-line)]/60">
        <div v-for="n in 3" :key="n" data-skeleton class="h-16 animate-pulse bg-[var(--app-fill)]" />
      </div>

      <template v-else-if="store.loaded">
        <MemberList
          v-if="store.items.length"
          :items="store.items"
          :query="q"
          @edit="openForm"
          @remove="removing = $event"
          @send-email="sendLink"
        />
        <EmptyState v-else-if="q.trim()" :title="t('members.empty.noMatch', { q: q.trim() })">
          <BaseButton variant="secondary" @click="search = ''">{{ t('common.search.clear') }}</BaseButton>
        </EmptyState>
        <EmptyState
          v-else
          :title="t('members.empty.none')"
          :description="t('members.empty.description')"
        >
          <BaseButton @click="openForm(null)">{{ t('members.add') }}</BaseButton>
        </EmptyState>

        <PaginationBar
          v-if="store.pageInfo"
          :page="store.pageInfo.number"
          :size="store.pageInfo.size"
          :total="store.pageInfo.totalElements"
          @update:page="goToPage"
        />
      </template>
    </div>

    <BaseDialog :open="formOpen" :title="editing ? t('members.editTitle') : t('members.add')" @close="closeForm">
      <div
        v-if="store.stale"
        role="alert"
        class="mb-5 rounded-xl border border-[var(--app-danger)]/30 p-4 text-[15px]"
      >
        <p class="font-medium">{{ t('common.staleTitle') }}</p>
        <p class="text-[var(--app-muted)]">{{ t('members.stale') }}</p>
        <BaseButton class="mt-3" variant="secondary" @click="reloadAfterStale">{{ t('common.reload') }}</BaseButton>
      </div>
      <MemberForm
        ref="form"
        :key="editing?.id ?? 'new'"
        :member="editing ?? undefined"
        :saving="store.saving"
        :field-errors="store.fieldErrors"
        @submit="save"
        @submit-another="saveAndAddAnother"
        @cancel="closeForm"
      />
    </BaseDialog>

    <ConfirmDialog
      :open="confirmingBulk"
      :title="t('members.bulk.title')"
      :message="bulkMessage"
      :confirm-label="t('members.bulk.confirm')"
      @confirm="confirmBulk"
      @cancel="confirmingBulk = false"
    />

    <ConfirmDialog
      :open="removing !== null"
      :title="t('members.remove.title', { name: removing?.name ?? '' })"
      :message="t('members.remove.message')"
      :confirm-label="t('members.remove.confirm')"
      destructive
      @confirm="confirmRemove"
      @cancel="removing = null"
    />
  </section>
</template>
