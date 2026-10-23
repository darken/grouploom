<script setup lang="ts">
import { computed, useId } from 'vue'
import { RouterLink } from 'vue-router'

import type { EventTypeColorKey } from '@/ui/eventTypeColors'
import { eventTypeColor } from '@/ui/eventTypeColors'

interface TypeOption {
  id: string
  name: string
  color: EventTypeColorKey
  archived: boolean
}

const model = defineModel<string>({ default: '' })

const props = defineProps<{
  /** Active types the user may pick. */
  options: TypeOption[]
  /** The event's current type when editing: kept selectable even if it has been archived since. */
  current?: TypeOption
  error?: string
  disabled?: boolean
}>()

const id = useId()

const choices = computed(() => {
  const list = props.options.map((o) => ({ ...o, label: o.name }))
  const current = props.current
  if (current && !list.some((o) => o.id === current.id)) {
    list.unshift({ ...current, label: current.archived ? `${current.name} (archived)` : current.name })
  }
  return list
})

const chosen = computed(() => choices.value.find((o) => o.id === model.value))
</script>

<template>
  <div>
    <label :for="id" class="mb-1.5 block text-sm font-medium">Type</label>
    <p v-if="!choices.length" class="flex h-11 items-center text-[15px] text-[var(--app-muted)]">
      <RouterLink to="/event-types" class="text-[var(--app-accent)] underline-offset-2 hover:underline">
        Create an event type first
      </RouterLink>
    </p>
    <div v-else class="relative">
      <span
        v-if="chosen"
        data-color-dot
        class="pointer-events-none absolute top-1/2 left-3.5 size-3 -translate-y-1/2 rounded-full"
        :class="eventTypeColor(chosen.color).dot"
        aria-hidden="true"
      />
      <select
        :id="id"
        v-model="model"
        name="eventTypeId"
        :disabled="disabled"
        :aria-invalid="error ? 'true' : undefined"
        class="h-11 w-full rounded-xl border border-transparent bg-[var(--app-fill)] pr-3.5 text-[16px] text-[var(--app-ink)] transition-shadow duration-150 ease-out focus:border-[var(--app-accent)] focus:outline-none focus:ring-4 focus:ring-[var(--app-accent)]/20 disabled:opacity-60 aria-[invalid=true]:border-[var(--app-danger)]"
        :class="chosen ? 'pl-9' : 'pl-3.5'"
      >
        <option value="" disabled>Choose a type</option>
        <option v-for="option in choices" :key="option.id" :value="option.id">{{ option.label }}</option>
      </select>
    </div>
    <p v-if="error" class="mt-1.5 text-sm text-[var(--app-danger)]">{{ error }}</p>
  </div>
</template>
