<script setup lang="ts">
import { ref, watch } from 'vue'

import { fromWallClockMs, wallClockMs, type DateTimeParts } from '@/ui/localDateTime'

const model = defineModel<DateTimeParts>({ required: true })

const props = defineProps<{
  /** Sets the end this long after the start, until the end has been edited (or `endEdited` locks it). */
  suggestedDurationMinutes?: number
  endEdited?: boolean
  startError?: string
  endError?: string
  disabled?: boolean
  /** Only the start date and time, e.g. for "send at"; the end is left untouched. */
  single?: boolean
  startLabel?: string
}>()

const endTouched = ref(props.endEdited ?? false)

const INPUT =
  'h-11 min-w-0 flex-1 rounded-xl border border-transparent bg-[var(--app-fill)] px-3 text-[16px] tabular-nums text-[var(--app-ink)] transition-shadow duration-150 ease-out focus:border-[var(--app-accent)] focus:outline-none focus:ring-4 focus:ring-[var(--app-accent)]/20 disabled:opacity-60 aria-[invalid=true]:border-[var(--app-danger)]'

function setStart(field: 'startDate' | 'startTime', value: string) {
  const next = { ...model.value, [field]: value }
  if (props.single) {
    model.value = next
    return
  }
  const before = wallClockMs(model.value.startDate, model.value.startTime)
  const after = wallClockMs(next.startDate, next.startTime)
  const end = wallClockMs(next.endDate, next.endTime)
  // Keep the duration: move the end by however far the start moved.
  if (![before, after, end].some(Number.isNaN)) {
    const shifted = fromWallClockMs(end + (after - before))
    next.endDate = shifted.date
    next.endTime = shifted.time
  }
  model.value = next
}

function setEnd(field: 'endDate' | 'endTime', value: string) {
  endTouched.value = true
  model.value = { ...model.value, [field]: value }
}

watch(
  () => props.suggestedDurationMinutes,
  (minutes) => {
    if (minutes === undefined || endTouched.value) return
    const start = wallClockMs(model.value.startDate, model.value.startTime)
    if (Number.isNaN(start)) return
    const end = fromWallClockMs(start + minutes * 60_000)
    model.value = { ...model.value, endDate: end.date, endTime: end.time }
  },
)
</script>

<template>
  <div class="space-y-4">
    <div>
      <p class="mb-1.5 text-sm font-medium">{{ startLabel ?? 'Starts' }}</p>
      <div class="flex gap-2">
        <input
          type="date"
          name="startDate"
          aria-label="Start date"
          :class="INPUT"
          :value="model.startDate"
          :disabled="disabled"
          :aria-invalid="startError ? 'true' : undefined"
          @input="setStart('startDate', ($event.target as HTMLInputElement).value)"
        />
        <input
          type="time"
          step="300"
          name="startTime"
          aria-label="Start time"
          :class="INPUT"
          :value="model.startTime"
          :disabled="disabled"
          :aria-invalid="startError ? 'true' : undefined"
          @input="setStart('startTime', ($event.target as HTMLInputElement).value)"
        />
      </div>
      <p v-if="startError" class="mt-1.5 text-sm text-[var(--app-danger)]">{{ startError }}</p>
    </div>
    <div v-if="!single">
      <p class="mb-1.5 text-sm font-medium">Ends</p>
      <div class="flex gap-2">
        <input
          type="date"
          name="endDate"
          aria-label="End date"
          :class="INPUT"
          :value="model.endDate"
          :disabled="disabled"
          :aria-invalid="endError ? 'true' : undefined"
          @input="setEnd('endDate', ($event.target as HTMLInputElement).value)"
        />
        <input
          type="time"
          step="300"
          name="endTime"
          aria-label="End time"
          :class="INPUT"
          :value="model.endTime"
          :disabled="disabled"
          :aria-invalid="endError ? 'true' : undefined"
          @input="setEnd('endTime', ($event.target as HTMLInputElement).value)"
        />
      </div>
      <p v-if="endError" class="mt-1.5 text-sm text-[var(--app-danger)]">{{ endError }}</p>
    </div>
  </div>
</template>
