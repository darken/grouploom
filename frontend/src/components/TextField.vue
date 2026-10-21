<script setup lang="ts">
import { computed, useId } from 'vue'

const model = defineModel<string>({ default: '' })

const props = defineProps<{
  label: string
  name: string
  type?: string
  autocomplete?: string
  hint?: string
  error?: string
  disabled?: boolean
}>()

const emit = defineEmits<{ blur: [] }>()

const id = useId()
const hintId = computed(() => `${id}-hint`)
const errorId = computed(() => `${id}-error`)
const describedBy = computed(() => (props.error ? errorId.value : props.hint ? hintId.value : undefined))
</script>

<template>
  <div>
    <label :for="id" class="mb-1.5 block text-sm font-medium">{{ label }}</label>
    <div class="relative">
      <input
        :id="id"
        v-model="model"
        :name="name"
        :type="type ?? 'text'"
        :autocomplete="autocomplete"
        :disabled="disabled"
        :aria-invalid="error ? 'true' : undefined"
        :aria-describedby="describedBy"
        class="h-11 w-full rounded-xl border border-transparent bg-[var(--app-fill)] px-3.5 text-[16px] text-[var(--app-ink)] transition-shadow duration-150 ease-out focus:border-[var(--app-accent)] focus:outline-none focus:ring-4 focus:ring-[var(--app-accent)]/20 disabled:opacity-60 aria-[invalid=true]:border-[var(--app-danger)]"
        :class="$slots.suffix && 'pr-11'"
        @blur="emit('blur')"
      />
      <div v-if="$slots.suffix" class="absolute inset-y-0 right-1 flex items-center">
        <slot name="suffix" />
      </div>
    </div>
    <p v-if="error" :id="errorId" class="mt-1.5 text-sm text-[var(--app-danger)]">{{ error }}</p>
    <p v-else-if="hint" :id="hintId" class="mt-1.5 text-sm text-[var(--app-muted)]">{{ hint }}</p>
  </div>
</template>
