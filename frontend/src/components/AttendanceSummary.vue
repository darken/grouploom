<script setup lang="ts">
import type { AttendanceFilter, AttendanceSummary } from '@/stores/attendance'

const props = defineProps<{ summary: AttendanceSummary; filter: AttendanceFilter }>()
const emit = defineEmits<{ select: [filter: AttendanceFilter] }>()

const FIGURES = [
  { filter: 'GOING', label: 'Going', count: () => props.summary.going },
  { filter: 'NOT_GOING', label: "Can't go", count: () => props.summary.notGoing },
  { filter: 'NO_REPLY', label: 'No reply', count: () => props.summary.noReply },
] as const

// Tapping the figure that is already active shows everyone again.
const choose = (filter: AttendanceFilter) => emit('select', props.filter === filter ? 'ALL' : filter)
</script>

<template>
  <p class="flex flex-wrap items-center text-[15px] text-[var(--app-muted)]">
    <template v-for="(figure, i) in FIGURES" :key="figure.filter">
      <span v-if="i > 0" aria-hidden="true">·</span>
      <button
        type="button"
        :aria-pressed="filter === figure.filter"
        class="inline-flex min-h-11 items-center gap-1 rounded-lg px-2 hover:bg-[var(--app-fill)] aria-pressed:bg-[var(--app-fill)]"
        @click="choose(figure.filter)"
      >
        <strong data-count class="font-semibold text-[var(--app-ink)] tabular-nums">{{ figure.count() }}</strong>
        {{ figure.label }}
      </button>
    </template>
  </p>
</template>
