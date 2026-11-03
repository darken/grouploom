<script setup lang="ts">
import { nextTick, ref, useTemplateRef } from 'vue'
import { I18nT, useI18n } from 'vue-i18n'

import { timeZoneOptions } from '@/ui/timeZones'

const model = defineModel<string>({ required: true })

defineProps<{ error?: string; disabled?: boolean }>()

const { t } = useI18n()
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
    <I18nT v-if="!open" keypath="events.form.timeZoneCurrent" tag="p" class="text-sm text-[var(--app-muted)]">
      <template #zone>{{ model }}</template>
      <template #change>
        <button
          type="button"
          class="rounded text-[var(--app-accent)] underline-offset-2 hover:underline disabled:opacity-60"
          :disabled="disabled"
          @click="expand"
        >
          {{ t('events.form.timeZoneChange') }}
        </button>
      </template>
    </I18nT>
    <div v-else>
      <label class="mb-1.5 block text-sm font-medium">
        {{ t('events.form.timeZone') }}
        <select
          ref="select"
          v-model="model"
          name="timeZone"
          :aria-label="t('events.form.timeZone')"
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
