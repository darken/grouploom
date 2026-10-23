<script setup lang="ts">
import { nextTick, ref, useTemplateRef } from 'vue'

import { timeZoneOptions } from '@/ui/timeZones'

const model = defineModel<string>({ required: true })

defineProps<{ error?: string; disabled?: boolean }>()

const open = ref(false)
const select = useTemplateRef<HTMLSelectElement>('select')
const options = timeZoneOptions()

async function expand() {
  open.value = true
  await nextTick()
  select.value?.focus()
}
</script>

<template>
  <div>
    <p v-if="!open" class="text-sm text-[var(--app-muted)]">
      Time zone: {{ model }} ·
      <button
        type="button"
        class="rounded text-[var(--app-accent)] underline-offset-2 hover:underline disabled:opacity-60"
        :disabled="disabled"
        @click="expand"
      >
        Change
      </button>
    </p>
    <div v-else>
      <label class="mb-1.5 block text-sm font-medium">
        Time zone
        <select
          ref="select"
          v-model="model"
          name="timeZone"
          aria-label="Time zone"
          :disabled="disabled"
          :aria-invalid="error ? 'true' : undefined"
          class="mt-1.5 h-11 w-full rounded-xl border border-transparent bg-[var(--app-fill)] px-3.5 text-[16px] font-normal text-[var(--app-ink)] focus:border-[var(--app-accent)] focus:ring-4 focus:ring-[var(--app-accent)]/20 focus:outline-none aria-[invalid=true]:border-[var(--app-danger)]"
        >
          <option v-for="option in options" :key="option.value" :value="option.value">{{ option.label }}</option>
        </select>
      </label>
    </div>
    <p v-if="error" class="mt-1.5 text-sm text-[var(--app-danger)]">{{ error }}</p>
  </div>
</template>
