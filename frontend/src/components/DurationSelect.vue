<script setup lang="ts">
import { computed, ref, useId } from 'vue'

const model = defineModel<number | undefined>()

defineProps<{ label?: string; error?: string }>()

const PRESETS = [
  { minutes: 30, label: '30 min' },
  { minutes: 60, label: '1 hr' },
  { minutes: 90, label: '1.5 hr' },
  { minutes: 120, label: '2 hr' },
  { minutes: 180, label: '3 hr' },
  { minutes: 240, label: '4 hr' },
  { minutes: 1440, label: 'All day' },
]

const id = useId()
const customChosen = ref(false)

const selected = computed(() => {
  if (customChosen.value) return 'custom'
  if (model.value === undefined) return ''
  return PRESETS.some((p) => p.minutes === model.value) ? String(model.value) : 'custom'
})

function onSelect(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  customChosen.value = value === 'custom'
  if (value === '') model.value = undefined
  else if (value !== 'custom') model.value = Number(value)
}

function onCustomInput(event: Event) {
  const raw = (event.target as HTMLInputElement).value
  model.value = raw === '' ? undefined : Number(raw)
}
</script>

<template>
  <div>
    <label :for="id" class="mb-1.5 block text-sm font-medium">{{ label ?? 'Default duration' }}</label>
    <div class="flex gap-2">
      <select
        :id="id"
        :value="selected"
        class="h-11 flex-1 rounded-xl border border-transparent bg-[var(--app-fill)] px-3 text-[16px] text-[var(--app-ink)] transition-shadow duration-150 ease-out focus:border-[var(--app-accent)] focus:outline-none focus:ring-4 focus:ring-[var(--app-accent)]/20"
        @change="onSelect"
      >
        <option value="">None</option>
        <option v-for="preset in PRESETS" :key="preset.minutes" :value="String(preset.minutes)">{{ preset.label }}</option>
        <option value="custom">Custom…</option>
      </select>
      <div v-if="selected === 'custom'" class="flex items-center gap-2">
        <input
          type="number"
          min="5"
          max="1440"
          :value="model ?? ''"
          :aria-label="'Custom duration in minutes'"
          :aria-invalid="error ? 'true' : undefined"
          class="h-11 w-24 rounded-xl border border-transparent bg-[var(--app-fill)] px-3 text-[16px] text-[var(--app-ink)] focus:border-[var(--app-accent)] focus:outline-none focus:ring-4 focus:ring-[var(--app-accent)]/20 aria-[invalid=true]:border-[var(--app-danger)]"
          @input="onCustomInput"
        />
        <span class="text-sm text-[var(--app-muted)]">min</span>
      </div>
    </div>
    <p v-if="error" class="mt-1.5 text-sm text-[var(--app-danger)]">{{ error }}</p>
  </div>
</template>
