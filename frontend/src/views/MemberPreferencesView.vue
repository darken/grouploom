<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'

import BaseButton from '@/components/BaseButton.vue'
import ErrorBanner from '@/components/ErrorBanner.vue'
import LinkProblem from '@/components/LinkProblem.vue'
import PreferenceRow from '@/components/PreferenceRow.vue'
import SaveStatus from '@/components/SaveStatus.vue'
import ToggleSwitch from '@/components/ToggleSwitch.vue'
import { useMemberPreferencesStore } from '@/stores/memberPreferences'

const route = useRoute()
const store = useMemberPreferencesStore()

function open(hash: string) {
  store.init(hash)
  store.load()
}

onMounted(() => open(route.hash || window.location.hash))

// A different link opened in this same tab only changes the fragment.
watch(
  () => route.hash,
  (hash) => {
    if (hash) open(hash)
  },
)

const data = computed(() => store.data)
const paused = computed(() => data.value?.invitationsPaused ?? false)

const sectionHeader = 'text-[13px] font-medium uppercase tracking-wide text-[var(--app-muted)]'
const group = 'divide-y divide-[var(--app-line)]/60 overflow-hidden rounded-2xl border border-[var(--app-line)]/60 bg-[var(--app-surface)]'
</script>

<template>
  <div>
    <h1 class="text-[28px] leading-tight font-semibold tracking-tight">Email preferences</h1>

    <div v-if="store.status === 'loading'" aria-busy="true" class="mt-6">
      <div data-skeleton class="h-5 w-3/4 animate-pulse rounded bg-[var(--app-fill)]" />
      <div class="mt-8 divide-y divide-[var(--app-line)]/60 overflow-hidden rounded-2xl border border-[var(--app-line)]/60 bg-[var(--app-surface)]">
        <div v-for="n in 3" :key="n" data-skeleton class="flex min-h-[3.25rem] items-center gap-3 px-4">
          <span class="size-3 animate-pulse rounded-full bg-[var(--app-fill)]" />
          <span class="h-4 w-32 animate-pulse rounded bg-[var(--app-fill)]" />
        </div>
      </div>
    </div>

    <LinkProblem v-else-if="store.status === 'invalid' || store.status === 'expired'" :kind="store.status" />

    <div v-else-if="store.status === 'error' && store.error" class="mt-6">
      <ErrorBanner :error="store.error">
        <BaseButton variant="secondary" @click="store.load()">Retry</BaseButton>
      </ErrorBanner>
    </div>

    <template v-else-if="data">
      <p class="mt-3 text-[17px] leading-snug">
        Hi {{ data.memberName }} — choose which {{ data.groupName }} events you'd like to hear about.
      </p>
      <p class="mt-1 text-[15px] text-[var(--app-muted)]">Sending to {{ data.maskedEmail }}</p>

      <section class="mt-8" aria-labelledby="types-heading">
        <div class="mb-2 flex min-h-5 items-baseline justify-between px-4">
          <h2 id="types-heading" :class="sectionHeader">Event types</h2>
          <SaveStatus :status="store.saveStatus" @retry="store.retry()" />
        </div>
        <ul v-if="data.eventTypes.length" :class="group">
          <PreferenceRow
            v-for="type in data.eventTypes"
            :key="type.id"
            :name="type.name"
            :description="type.description"
            :color="type.color"
            :model-value="type.subscribed"
            :disabled="paused"
            @update:model-value="store.setSubscribed(type.id, $event)"
          />
        </ul>
        <p v-else class="rounded-2xl border border-dashed border-[var(--app-line)] px-4 py-6 text-center text-[15px] text-[var(--app-muted)]">
          {{ data.groupName }} hasn't set up any event types yet.
        </p>
        <p v-if="paused" class="mt-2 px-4 text-[13px] text-[var(--app-muted)]">
          Invitations are paused. Your choices below are kept.
        </p>
      </section>

      <section class="mt-8" aria-labelledby="all-heading">
        <h2 id="all-heading" class="mb-2 px-4" :class="sectionHeader">All invitations</h2>
        <ul :class="group">
          <li class="flex min-h-[3.25rem] items-center gap-3 py-1 pr-2 pl-4">
            <span id="pause-label" class="min-w-0 flex-1 py-2 text-[17px] leading-snug">Pause all invitations</span>
            <ToggleSwitch
              :model-value="paused"
              aria-labelledby="pause-label"
              aria-describedby="pause-help"
              @update:model-value="store.setPaused($event)"
            />
          </li>
        </ul>
        <p id="pause-help" class="mt-2 px-4 text-[13px] text-[var(--app-muted)]">
          You won't get invitations from {{ data.groupName }} until you turn this off.
        </p>
      </section>
    </template>
  </div>
</template>
