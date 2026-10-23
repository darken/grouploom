<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import type { Audience, ScheduleNotificationInput } from '@/stores/eventNotifications'
import type { EventItem } from '@/stores/events'
import { fromWallClockMs, wallClockMs, type DateTimeParts } from '@/ui/localDateTime'
import { describeOffset } from '@/ui/notificationText'
import { browserTimeZone } from '@/ui/timeZones'

import BaseButton from './BaseButton.vue'
import BaseDialog from './BaseDialog.vue'
import DateTimeFields from './DateTimeFields.vue'
import SegmentedControl from './SegmentedControl.vue'
import TextAreaField from './TextAreaField.vue'

const props = defineProps<{
  open: boolean
  event: EventItem
  audience: Audience | null
  saving?: boolean
  fieldErrors?: Record<string, string>
  /** A problem that isn't about one field, e.g. the limit of scheduled notifications. */
  error?: string
  /** The current moment; injectable so "Too late" is testable. */
  now?: Date
}>()
const emit = defineEmits<{ submit: [input: ScheduleNotificationInput]; close: [] }>()

const PAST_ALLOWANCE_MS = 60_000
const OFFSETS = [120, 1_440, 10_080]

type Choice = 'now' | 'custom' | `${number}`

const kind = ref<ScheduleNotificationInput['kind']>('INVITATION')
const choice = ref<Choice>('now')
const message = ref('')
const custom = ref<DateTimeParts>({ startDate: '', startTime: '', endDate: '', endTime: '' })
const customIncomplete = ref(false)

const KINDS = [
  { value: 'INVITATION', label: 'Invitation' },
  { value: 'REMINDER', label: 'Reminder' },
]

function reset() {
  kind.value = 'INVITATION'
  choice.value = 'now'
  message.value = ''
  customIncomplete.value = false
  // Start from the same time the day before, in the event's own zone.
  const [date = '', time = ''] = props.event.localStart.split('T')
  const dayBefore = fromWallClockMs(wallClockMs(date, time.slice(0, 5)) - 86_400_000)
  custom.value = { startDate: dayBefore.date, startTime: time.slice(0, 5), endDate: dayBefore.date, endTime: time.slice(0, 5) }
}

watch(() => props.open, (open) => open && reset(), { immediate: true })

const options = computed(() => {
  const now = (props.now ?? new Date()).getTime()
  const start = Date.parse(props.event.start)
  return OFFSETS.map((minutes) => ({
    value: String(minutes) as Choice,
    label: describeOffset(minutes),
    late: start - minutes * 60_000 < now - PAST_ALLOWANCE_MS,
  }))
})

const differentZone = computed(() => props.event.timeZone !== browserTimeZone())

const audienceText = computed(() => {
  const a = props.audience
  if (!a) return null
  if (a.eligible === 0) return { warning: true, text: 'No members will receive this right now.' }
  const left = a.total - a.eligible
  const why = left > 0 ? ` ${left} ${left === 1 ? 'has' : 'have'} paused invitations or turned off ${props.event.eventType.name}.` : ''
  return { warning: false, text: `Will be sent to ${a.eligible} of ${a.total} members.${why}` }
})

const submitLabel = computed(() => (choice.value === 'now' ? 'Send now' : 'Schedule'))

function submit() {
  if (props.saving) return
  const base = { kind: kind.value, ...(message.value.trim() ? { message: message.value.trim() } : {}) }
  if (choice.value === 'now') {
    emit('submit', { ...base, timing: 'NOW' })
  } else if (choice.value === 'custom') {
    const { startDate, startTime } = custom.value
    customIncomplete.value = !startDate || !startTime
    if (customIncomplete.value) return
    emit('submit', { ...base, timing: 'AT', localSendAt: `${startDate}T${startTime}:00` })
  } else {
    emit('submit', { ...base, timing: 'BEFORE_START', offsetMinutes: Number(choice.value) })
  }
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) {
    event.preventDefault()
    submit()
  }
}

const ROW = 'flex min-h-11 items-center gap-3 px-4 py-2.5 has-[:disabled]:opacity-50'
</script>

<template>
  <BaseDialog :open="open" title="Notify members" @close="emit('close')">
    <div @keydown="onKeydown">
      <SegmentedControl v-model="kind" :options="KINDS" label="Notification type" />

      <fieldset class="mt-5">
        <legend class="mb-1.5 text-sm font-medium">When</legend>
        <div class="divide-y divide-[var(--app-line)]/60 overflow-hidden rounded-xl border border-[var(--app-line)]/60">
          <label :class="ROW">
            <input v-model="choice" type="radio" name="timing" value="now" class="accent-[var(--app-accent)]" />
            <span class="flex-1">Now</span>
          </label>
          <label v-for="o in options" :key="o.value" :class="ROW">
            <input v-model="choice" type="radio" name="timing" :value="o.value" :disabled="o.late" class="accent-[var(--app-accent)]" />
            <span class="flex-1">{{ o.label }}</span>
            <span v-if="o.late" class="text-sm text-[var(--app-muted)]">Too late</span>
          </label>
          <label :class="ROW">
            <input v-model="choice" type="radio" name="timing" value="custom" class="accent-[var(--app-accent)]" />
            <span class="flex-1">Custom…</span>
          </label>
        </div>
        <p v-if="fieldErrors?.offsetMinutes" class="mt-1.5 text-sm text-[var(--app-danger)]">{{ fieldErrors.offsetMinutes }}</p>
      </fieldset>

      <div v-if="choice === 'custom'" class="mt-4">
        <DateTimeFields v-model="custom" single start-label="Send at" :start-error="fieldErrors?.localSendAt ?? (customIncomplete ? 'Choose a date and time.' : undefined)" />
        <p v-if="differentZone" class="mt-1.5 text-sm text-[var(--app-muted)]">Times are in {{ event.timeZone }}.</p>
      </div>

      <TextAreaField v-model="message" class="mt-5" label="Add a note (optional)" name="message" :maxlength="1000" :error="fieldErrors?.message" />

      <p
        v-if="audienceText"
        data-audience
        class="mt-4 text-sm"
        :class="audienceText.warning ? 'text-orange-600 dark:text-orange-400' : 'text-[var(--app-muted)]'"
      >
        {{ audienceText.text }}
      </p>

      <p v-if="error" role="alert" class="mt-4 text-sm text-[var(--app-danger)]">{{ error }}</p>

      <div class="mt-6 flex justify-end gap-2">
        <BaseButton variant="plain" @click="emit('close')">Cancel</BaseButton>
        <BaseButton :loading="saving" @click="submit">{{ submitLabel }}</BaseButton>
      </div>
    </div>
  </BaseDialog>
</template>
