<script setup lang="ts">
import type { ApiError } from '@/api/errors'

defineProps<{ error: ApiError }>()
</script>

<template>
  <div
    role="alert"
    class="flex gap-3 rounded-2xl border border-[var(--app-danger)]/30 bg-[var(--app-surface)] p-4 text-[15px]"
  >
    <svg class="mt-0.5 size-5 shrink-0 text-[var(--app-danger)]" viewBox="0 0 20 20" fill="none" aria-hidden="true">
      <circle cx="10" cy="10" r="8" stroke="currentColor" stroke-width="1.5" />
      <path d="M10 6v5M10 13.5v.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
    </svg>
    <div class="flex-1">
      <p class="font-medium">{{ error.problem.title ?? 'Something went wrong' }}</p>
      <p v-if="error.problem.detail" class="text-[var(--app-muted)]">{{ error.problem.detail }}</p>
      <ul v-if="error.problem.errors?.length" class="mt-2 list-disc pl-5 text-[var(--app-muted)]">
        <li v-for="item in error.problem.errors" :key="item.field">{{ item.field }}: {{ item.message }}</li>
      </ul>
    </div>
    <div class="shrink-0 self-center empty:hidden"><slot /></div>
  </div>
</template>
