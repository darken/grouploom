<script setup lang="ts">
import { computed, ref } from 'vue'

const model = defineModel<string>({ required: true })

const props = defineProps<{ options: { value: string; label: string }[]; label: string }>()

const buttons = ref<HTMLButtonElement[]>([])
const index = computed(() => Math.max(0, props.options.findIndex((o) => o.value === model.value)))

function move(step: number) {
  const next = (index.value + step + props.options.length) % props.options.length
  const option = props.options[next]
  if (!option) return
  model.value = option.value
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
</script>

<template>
  <div
    role="radiogroup"
    :aria-label="label"
    class="relative inline-grid auto-cols-fr grid-flow-col rounded-full bg-[var(--app-fill)] p-0.5"
  >
    <span
      aria-hidden="true"
      class="absolute inset-y-0.5 left-0.5 rounded-full bg-[var(--app-surface)] shadow-[0_1px_3px_rgba(0,0,0,0.12)] transition-transform duration-200 ease-out"
      :style="{ width: `calc((100% - 4px) / ${options.length})`, transform: `translateX(${index * 100}%)` }"
    />
    <button
      v-for="(option, i) in options"
      :key="option.value"
      :ref="(el) => (buttons[i] = el as HTMLButtonElement)"
      type="button"
      role="radio"
      :aria-checked="option.value === model"
      :tabindex="option.value === model ? 0 : -1"
      class="relative z-[1] rounded-full px-3 py-1.5 text-sm font-medium whitespace-nowrap transition-colors duration-150 sm:px-4"
      :class="option.value === model ? 'text-[var(--app-ink)]' : 'text-[var(--app-muted)]'"
      @click="model = option.value"
      @keydown="onKeydown"
    >
      {{ option.label }}
    </button>
  </div>
</template>
