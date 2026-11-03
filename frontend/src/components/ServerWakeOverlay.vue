<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'

import { useI18n } from 'vue-i18n'

import { retryWake, serverStatus } from '@/api/wake-middleware'
import BaseButton from '@/components/BaseButton.vue'

const { t } = useI18n()

const visible = computed(() => serverStatus.value !== 'awake')

// Seconds since the server stopped answering; keeps counting across "try again".
const seconds = ref(0)
let timer: ReturnType<typeof setInterval> | undefined

watch(
  visible,
  (show) => {
    clearInterval(timer)
    seconds.value = 0
    if (show) timer = setInterval(() => seconds.value++, 1000)
  },
  { immediate: true },
)

onBeforeUnmount(() => clearInterval(timer))
</script>

<template>
  <Transition name="page">
    <div
      v-if="visible"
      class="fixed inset-0 z-[60] flex items-center justify-center bg-[var(--app-canvas)]/80 px-4 backdrop-blur-sm"
    >
      <div
        :role="serverStatus === 'unreachable' ? 'alert' : 'status'"
        aria-live="polite"
        class="w-full max-w-sm rounded-2xl border border-[var(--app-line)]/60 bg-[var(--app-surface)] p-6 text-center shadow-[0_20px_60px_rgba(0,0,0,0.18)]"
      >
        <template v-if="serverStatus === 'waking'">
          <svg class="mx-auto mb-4 size-8 animate-spin text-[var(--app-accent)]" viewBox="0 0 20 20" fill="none" aria-hidden="true">
            <circle cx="10" cy="10" r="7" stroke="currentColor" stroke-opacity="0.25" stroke-width="2" />
            <path d="M17 10a7 7 0 0 0-7-7" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
          </svg>
          <h2 class="text-lg font-semibold tracking-tight">{{ t('wake.waking.title') }}</h2>
          <p class="mt-2 text-[15px] text-[var(--app-muted)]">{{ t('wake.waking.body') }}</p>
          <p class="mt-4 text-sm text-[var(--app-muted)] tabular-nums">{{ t('wake.waking.seconds', { n: seconds }) }}</p>
        </template>
        <template v-else>
          <h2 class="text-lg font-semibold tracking-tight">{{ t('wake.unreachable.title') }}</h2>
          <p class="mt-2 text-[15px] text-[var(--app-muted)]">{{ t('wake.unreachable.body') }}</p>
          <BaseButton class="mt-5" @click="retryWake">{{ t('wake.unreachable.retry') }}</BaseButton>
        </template>
      </div>
    </div>
  </Transition>
</template>
