<script setup lang="ts">
import { computed, reactive, ref } from 'vue'

import type { EventDraft, EventItem } from '@/stores/events'
import type { EventTypeColorKey } from '@/ui/eventTypeColors'
import { fromWallClockMs, toLocalDateTime, wallClockMs, type DateTimeParts } from '@/ui/localDateTime'
import { browserTimeZone } from '@/ui/timeZones'
import BaseButton from './BaseButton.vue'
import DateTimeFields from './DateTimeFields.vue'
import EventTypeSelect from './EventTypeSelect.vue'
import TextAreaField from './TextAreaField.vue'
import TextField from './TextField.vue'
import TimeZoneField from './TimeZoneField.vue'

interface TypeChoice {
  id: string
  name: string
  color: EventTypeColorKey
  archived: boolean
  defaultDurationMinutes?: number
}

const props = defineProps<{
  event?: EventItem
  /** Active event types the user may pick. */
  eventTypes: TypeChoice[]
  saving?: boolean
  fieldErrors?: Record<string, string>
}>()

const emit = defineEmits<{ submit: [payload: EventDraft]; cancel: [] }>()

const MAX_DAYS_MS = 7 * 24 * 60 * 60 * 1000
const PAST_ALLOWANCE_MS = 60_000

const pad = (n: number) => String(n).padStart(2, '0')

/** New events start at the next full hour and last an hour. */
function defaultTimes(): DateTimeParts {
  const start = new Date()
  start.setMinutes(0, 0, 0)
  start.setHours(start.getHours() + 1)
  const startDate = `${start.getFullYear()}-${pad(start.getMonth() + 1)}-${pad(start.getDate())}`
  const startTime = `${pad(start.getHours())}:00`
  const end = fromWallClockMs(wallClockMs(startDate, startTime) + 60 * 60_000)
  return { startDate, startTime, endDate: end.date, endTime: end.time }
}

function eventTimes(event: EventItem): DateTimeParts {
  return {
    startDate: event.localStart.slice(0, 10),
    startTime: event.localStart.slice(11, 16),
    endDate: event.localEnd.slice(0, 10),
    endTime: event.localEnd.slice(11, 16),
  }
}

const initial = {
  title: props.event?.title ?? '',
  eventTypeId: props.event?.eventType.id ?? '',
  description: props.event?.description ?? '',
  location: props.event?.location ?? '',
  timeZone: props.event?.timeZone ?? browserTimeZone(),
  times: props.event ? eventTimes(props.event) : defaultTimes(),
}

const form = reactive({ ...initial, times: { ...initial.times } })
const touched = ref<Record<string, boolean>>({})
const submitted = ref(false)

const suggestedDuration = computed(
  () => props.eventTypes.find((t) => t.id === form.eventTypeId)?.defaultDurationMinutes,
)

/** Today's wall-clock "now" in the chosen zone, as the same fake-UTC milliseconds the form compares. */
function nowInZoneMs(zone: string): number {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: zone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(new Date(Date.now() - PAST_ALLOWANCE_MS))
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? '00'
  return wallClockMs(`${get('year')}-${get('month')}-${get('day')}`, `${get('hour')}:${get('minute')}`)
}

const startChanged = computed(
  () => form.times.startDate !== initial.times.startDate || form.times.startTime !== initial.times.startTime,
)

const clientErrors = computed(() => {
  const title = form.title.trim()
  const t = form.times
  const start = wallClockMs(t.startDate, t.startTime)
  const end = wallClockMs(t.endDate, t.endTime)
  let startError = ''
  let endError = ''
  if (Number.isNaN(start)) startError = 'Enter a start date and time.'
  else if ((!props.event || startChanged.value) && start < nowInZoneMs(form.timeZone)) {
    startError = "The start can't be in the past."
  }
  if (Number.isNaN(end)) endError = 'Enter an end date and time.'
  else if (!Number.isNaN(start) && end <= start) endError = 'End must be after the start.'
  else if (!Number.isNaN(start) && end - start > MAX_DAYS_MS) endError = 'An event can last at most 7 days.'
  return {
    title: !title ? 'Enter a title.' : title.length > 120 ? 'Use 120 characters or fewer.' : '',
    eventTypeId: !form.eventTypeId ? 'Choose an event type.' : '',
    localStart: startError,
    localEnd: endError,
    location: form.location.trim().length > 200 ? 'Use 200 characters or fewer.' : '',
    description: form.description.trim().length > 2000 ? 'Use 2000 characters or fewer.' : '',
  }
})

type Field = keyof typeof clientErrors.value

// Dates are valid by default, so their errors can show straight away; the rest wait for blur or submit.
function errorFor(field: Field) {
  const alwaysShown = field === 'localStart' || field === 'localEnd'
  const showClient = alwaysShown || touched.value[field] || submitted.value
  return (showClient ? clientErrors.value[field] : '') || props.fieldErrors?.[field] || ''
}

const hasTypes = computed(() => props.eventTypes.length > 0 || props.event !== undefined)
const valid = computed(() => Object.values(clientErrors.value).every((e) => !e))
const changed = computed(
  () =>
    form.title !== initial.title ||
    form.eventTypeId !== initial.eventTypeId ||
    form.description !== initial.description ||
    form.location !== initial.location ||
    form.timeZone !== initial.timeZone ||
    (Object.keys(form.times) as (keyof DateTimeParts)[]).some((k) => form.times[k] !== initial.times[k]),
)
const canSave = computed(() => hasTypes.value && valid.value && (!props.event || changed.value) && !props.saving)

function payload(): EventDraft {
  return {
    eventTypeId: form.eventTypeId,
    title: form.title.trim(),
    description: form.description.trim() || undefined,
    location: form.location.trim() || undefined,
    localStart: toLocalDateTime(form.times.startDate, form.times.startTime),
    localEnd: toLocalDateTime(form.times.endDate, form.times.endTime),
    timeZone: form.timeZone,
    ...(props.event ? { version: props.event.version } : {}),
  }
}

function submit() {
  submitted.value = true
  if (!valid.value || !hasTypes.value) return
  emit('submit', payload())
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && (event.metaKey || event.ctrlKey) && canSave.value) {
    event.preventDefault()
    submit()
  }
}
</script>

<template>
  <form class="space-y-5" novalidate @submit.prevent="submit" @keydown="onKeydown">
    <TextField
      v-model="form.title"
      label="Title"
      name="title"
      autocomplete="off"
      :error="errorFor('title')"
      :disabled="saving"
      @blur="touched.title = true"
    />
    <EventTypeSelect
      v-model="form.eventTypeId"
      :options="eventTypes"
      :current="event?.eventType"
      :error="errorFor('eventTypeId')"
      :disabled="saving"
    />
    <DateTimeFields
      v-model="form.times"
      :suggested-duration-minutes="suggestedDuration"
      :end-edited="event !== undefined"
      :start-error="errorFor('localStart')"
      :end-error="errorFor('localEnd')"
      :disabled="saving"
    />
    <TimeZoneField v-model="form.timeZone" :error="fieldErrors?.timeZone" :disabled="saving" />
    <TextField
      v-model="form.location"
      label="Location"
      name="location"
      autocomplete="off"
      :error="errorFor('location')"
      :disabled="saving"
      @blur="touched.location = true"
    />
    <TextAreaField
      v-model="form.description"
      label="Description"
      name="description"
      :maxlength="2000"
      :error="errorFor('description')"
      :disabled="saving"
      @blur="touched.description = true"
    />
    <div class="flex flex-col-reverse gap-2 pt-2 sm:flex-row sm:justify-end">
      <BaseButton class="max-sm:w-full" variant="plain" @click="emit('cancel')">Cancel</BaseButton>
      <BaseButton class="max-sm:w-full" type="submit" :loading="saving" :disabled="!canSave">Save</BaseButton>
    </div>
  </form>
</template>
