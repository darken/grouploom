<script setup lang="ts">
import { computed, useId } from 'vue'

const model = defineModel<string>({ default: '' })

const props = defineProps<{
  label: string
  name: string
  maxlength?: number
  hint?: string
  error?: string
  rows?: number
  disabled?: boolean
}>()

const emit = defineEmits<{ blur: [] }>()

const id = useId()
const describedBy = computed(() => (props.error ? `${id}-error` : props.hint ? `${id}-hint` : undefined))
const showCounter = computed(() => props.maxlength !== undefined && model.value.length >= props.maxlength * 0.8)
</script>

<template>
  <div>
    <label :for="id" class="mb-1.5 block text-sm font-medium">{{ label }}</label>
    <textarea
      :id="id"
      v-model="model"
      :name="name"
      :rows="rows ?? 3"
      :maxlength="maxlength"
      :disabled="disabled"
      :aria-invalid="error ? 'true' : undefined"
      :aria-describedby="describedBy"
      class="w-full resize-none rounded-xl border border-transparent bg-[var(--app-fill)] px-3.5 py-2.5 text-[16px] text-[var(--app-ink)] transition-shadow duration-150 ease-out focus:border-[var(--app-accent)] focus:outline-none focus:ring-4 focus:ring-[var(--app-accent)]/20 disabled:opacity-60 aria-[invalid=true]:border-[var(--app-danger)]"
      @blur="emit('blur')"
    />
    <div class="mt-1.5 flex justify-between gap-3 text-sm">
      <p v-if="error" :id="`${id}-error`" class="text-[var(--app-danger)]">{{ error }}</p>
      <p v-else-if="hint" :id="`${id}-hint`" class="text-[var(--app-muted)]">{{ hint }}</p>
      <span v-else />
      <span v-if="showCounter" class="tabular-nums text-[var(--app-muted)]">{{ model.length }}/{{ maxlength }}</span>
    </div>
  </div>
</template>
