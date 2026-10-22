<script setup lang="ts">
import { ref } from 'vue'

import { EVENT_TYPE_COLORS } from '@/ui/eventTypeColors'

const model = defineModel<string>({ required: true })

const swatches = ref<HTMLButtonElement[]>([])

function move(from: number, step: number) {
  const next = (from + step + EVENT_TYPE_COLORS.length) % EVENT_TYPE_COLORS.length
  const color = EVENT_TYPE_COLORS[next]
  if (!color) return
  model.value = color.key
  swatches.value[next]?.focus()
}

function onKeydown(event: KeyboardEvent, index: number) {
  if (event.key === 'ArrowRight' || event.key === 'ArrowDown') {
    event.preventDefault()
    move(index, 1)
  } else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') {
    event.preventDefault()
    move(index, -1)
  }
}
</script>

<template>
  <div role="radiogroup" aria-label="Color" class="flex flex-wrap gap-2">
    <button
      v-for="(color, index) in EVENT_TYPE_COLORS"
      :key="color.key"
      :ref="(el) => (swatches[index] = el as HTMLButtonElement)"
      type="button"
      role="radio"
      :aria-label="color.label"
      :aria-checked="model === color.key"
      :tabindex="model === color.key ? 0 : -1"
      class="grid size-8 place-items-center rounded-full text-white transition-shadow duration-150 ease-out"
      :class="[color.dot, model === color.key && 'ring-2 ring-[var(--app-ink)] ring-offset-2 ring-offset-[var(--app-surface)]']"
      @click="model = color.key"
      @keydown="onKeydown($event, index)"
    >
      <svg v-if="model === color.key" class="size-4" viewBox="0 0 20 20" fill="none" aria-hidden="true">
        <path d="M5 10.5l3.2 3.2L15 6.8" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </button>
  </div>
</template>
