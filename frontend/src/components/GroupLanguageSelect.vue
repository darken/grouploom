<script setup lang="ts">
import { useId } from 'vue'
import { useI18n } from 'vue-i18n'

import { LANGUAGE_NAMES, SUPPORTED_LOCALES, type Locale } from '@/i18n'

defineProps<{ modelValue: Locale; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: Locale] }>()

const { t } = useI18n()
const id = useId()

function onChange(event: Event) {
  emit('update:modelValue', (event.target as HTMLSelectElement).value as Locale)
}
</script>

<template>
  <div>
    <label :for="id" class="mb-1 block text-sm font-medium">{{ t('account.emailLanguage') }}</label>
    <select
      :id="id"
      name="language"
      :value="modelValue"
      :disabled="disabled"
      :aria-describedby="`${id}-help`"
      class="h-10 w-full rounded-xl border border-transparent bg-[var(--app-fill)] px-3 text-[15px] text-[var(--app-ink)] focus:border-[var(--app-accent)] focus:ring-4 focus:ring-[var(--app-accent)]/20 focus:outline-none disabled:opacity-60"
      @change="onChange"
    >
      <option v-for="locale in SUPPORTED_LOCALES" :key="locale" :value="locale">{{ LANGUAGE_NAMES[locale] }}</option>
    </select>
    <p :id="`${id}-help`" class="mt-1 text-[13px] text-[var(--app-muted)]">{{ t('account.emailLanguageHelp') }}</p>
  </div>
</template>
