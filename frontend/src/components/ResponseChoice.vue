<script setup lang="ts">
import { computed, ref } from 'vue'

import type { AttendanceChoice } from '@/stores/memberAttendance'

const model = defineModel<AttendanceChoice | null>({ required: true })

const CHOICES: { value: AttendanceChoice; label: string }[] = [
  { value: 'GOING', label: 'Going' },
  { value: 'NOT_GOING', label: "Can't go" },
]

const buttons = ref<HTMLButtonElement[]>([])
const selected = computed(() => CHOICES.findIndex((c) => c.value === model.value))

function move(step: number) {
  const from = selected.value
  const next = from === -1 ? (step > 0 ? 0 : CHOICES.length - 1) : (from + step + CHOICES.length) % CHOICES.length
  model.value = CHOICES[next]!.value
  buttons.value[next]?.focus()
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'ArrowRight' || event.key === 'ArrowDown') {
    event.preventDefault()
    move(1)
  } else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') {
    event.preventDefault()
    move(-1)
  }
}

const CARD =
  'flex min-h-[4.5rem] flex-col items-center justify-center gap-1.5 rounded-2xl border px-4 py-4 text-[17px] font-medium transition-colors duration-150'

function cardClass(value: AttendanceChoice) {
  if (value !== model.value) return `${CARD} border-[var(--app-line)] bg-[var(--app-surface)] hover:bg-[var(--app-fill)]`
  const tint = value === 'GOING' ? 'bg-green-50 dark:bg-green-950' : 'bg-[var(--app-surface)]'
  return `${CARD} ring-2 ring-[var(--app-accent)] border-transparent ${tint}`
}
</script>

<template>
  <div role="radiogroup" aria-label="Will you come?" class="grid grid-cols-1 gap-3 min-[360px]:grid-cols-2">
    <button
      v-for="(choice, i) in CHOICES"
      :key="choice.value"
      :ref="(el) => (buttons[i] = el as HTMLButtonElement)"
      type="button"
      role="radio"
      :aria-checked="choice.value === model"
      :tabindex="choice.value === model || (selected === -1 && i === 0) ? 0 : -1"
      :class="cardClass(choice.value)"
      @click="model = choice.value"
      @keydown="onKeydown"
    >
      <svg v-if="choice.value === 'GOING'" class="size-6" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path d="m5 12.5 4.5 4.5L19 7.5" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
      <svg v-else class="size-6" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path d="m6.5 6.5 11 11m0-11-11 11" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" />
      </svg>
      {{ choice.label }}
    </button>
  </div>
</template>
