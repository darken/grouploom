<script setup lang="ts">
import { useId } from 'vue'

import { eventTypeColor, type EventTypeColorKey } from '@/ui/eventTypeColors'
import ToggleSwitch from './ToggleSwitch.vue'

const props = defineProps<{
  name: string
  description?: string
  color: EventTypeColorKey
  modelValue: boolean
  disabled?: boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const nameId = useId()
const descriptionId = useId()

// The whole row is the label: a click anywhere but on the switch (which toggles itself) toggles it.
function onRowClick(event: MouseEvent) {
  if (props.disabled || (event.target as HTMLElement).closest('[role=switch]')) return
  emit('update:modelValue', !props.modelValue)
}
</script>

<template>
  <li
    class="flex min-h-[3.25rem] cursor-pointer items-center gap-3 py-1 pr-2 pl-4 transition-opacity duration-150"
    :class="disabled && 'cursor-default opacity-50'"
    @click="onRowClick"
  >
    <span data-dot class="size-3 shrink-0 rounded-full" :class="eventTypeColor(color).dot" aria-hidden="true" />
    <span class="min-w-0 flex-1 py-2" data-name>
      <span :id="nameId" class="block text-[17px] leading-snug">{{ name }}</span>
      <span v-if="description" :id="descriptionId" class="block text-[15px] leading-snug text-[var(--app-muted)]">{{
        description
      }}</span>
    </span>
    <ToggleSwitch
      :model-value="modelValue"
      :disabled="disabled"
      :aria-labelledby="nameId"
      :aria-describedby="description ? descriptionId : undefined"
      @update:model-value="emit('update:modelValue', $event)"
    />
  </li>
</template>
