<script setup lang="ts">
import { ref } from 'vue'

import TextField from './TextField.vue'

const model = defineModel<string>({ default: '' })

defineProps<{
  label: string
  name: string
  autocomplete: 'current-password' | 'new-password'
  hint?: string
  error?: string
  disabled?: boolean
}>()

const emit = defineEmits<{ blur: [] }>()

const visible = ref(false)
</script>

<template>
  <TextField
    v-model="model"
    :label="label"
    :name="name"
    :type="visible ? 'text' : 'password'"
    :autocomplete="autocomplete"
    :hint="hint"
    :error="error"
    :disabled="disabled"
    @blur="emit('blur')"
  >
    <template #suffix>
      <button
        type="button"
        class="grid size-9 place-items-center rounded-lg text-[var(--app-muted)] transition-colors duration-150 hover:text-[var(--app-ink)]"
        :aria-label="visible ? 'Hide password' : 'Show password'"
        :aria-pressed="visible"
        @click="visible = !visible"
      >
        <svg v-if="!visible" class="size-5" viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <path d="M1.5 10S4.5 4.5 10 4.5 18.5 10 18.5 10 15.5 15.5 10 15.5 1.5 10 1.5 10Z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" />
          <circle cx="10" cy="10" r="2.5" stroke="currentColor" stroke-width="1.5" />
        </svg>
        <svg v-else class="size-5" viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <path d="M3 3l14 14M8.2 4.8A8 8 0 0 1 10 4.5c5.5 0 8.5 5.5 8.5 5.5a14 14 0 0 1-2.6 3.2M5.6 6.4C3.1 8 1.5 10 1.5 10s3 5.5 8.5 5.5a8 8 0 0 0 3-.6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </button>
    </template>
  </TextField>
</template>
