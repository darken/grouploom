<script setup lang="ts">
import { computed, ref, useId } from 'vue'
import { useI18n } from 'vue-i18n'

import { formatDuration } from '@/ui/formatDuration'

const model = defineModel<number | undefined>()

defineProps<{ label?: string; error?: string }>()

const { t } = useI18n()

const PRESET_MINUTES = [30, 60, 90, 120, 180, 240, 1440]
const presets = computed(() => PRESET_MINUTES.map((minutes) => ({ minutes, label: formatDuration(minutes) })))

const id = useId()
const customChosen = ref(false)

const selected = computed(() => {
  if (customChosen.value) return 'custom'
  if (model.value === undefined) return ''
  return PRESET_MINUTES.includes(model.value) ? String(model.value) : 'custom'
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
    <label :for="id" class="mb-1.5 block text-sm font-medium">{{ label ?? t('events.duration.default') }}</label>
    <div class="flex gap-2">
      <select
        :id="id"
        :value="selected"
        class="h-11 flex-1 rounded-xl border border-transparent bg-[var(--app-fill)] px-3 text-[16px] text-[var(--app-ink)] transition-shadow duration-150 ease-out focus:border-[var(--app-accent)] focus:outline-none focus:ring-4 focus:ring-[var(--app-accent)]/20"
        @change="onSelect"
      >
        <option value="">{{ t('events.duration.none') }}</option>
        <option v-for="preset in presets" :key="preset.minutes" :value="String(preset.minutes)">{{ preset.label }}</option>
        <option value="custom">{{ t('events.duration.custom') }}</option>
      </select>
      <div v-if="selected === 'custom'" class="flex items-center gap-2">
        <input
          type="number"
          min="5"
          max="1440"
          :value="model ?? ''"
          :aria-label="t('events.duration.customAria')"
          :aria-invalid="error ? 'true' : undefined"
          class="h-11 w-24 rounded-xl border border-transparent bg-[var(--app-fill)] px-3 text-[16px] text-[var(--app-ink)] focus:border-[var(--app-accent)] focus:outline-none focus:ring-4 focus:ring-[var(--app-accent)]/20 aria-[invalid=true]:border-[var(--app-danger)]"
          @input="onCustomInput"
        />
        <span class="text-sm text-[var(--app-muted)]">{{ t('common.minutesUnit') }}</span>
      </div>
    </div>
    <p v-if="error" class="mt-1.5 text-sm text-[var(--app-danger)]">{{ error }}</p>
  </div>
</template>
