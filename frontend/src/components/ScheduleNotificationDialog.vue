<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import type { Audience, AudienceKind, ScheduleNotificationInput } from '@/stores/eventNotifications'
import type { EventItem } from '@/stores/events'
import { fromWallClockMs, wallClockMs, type DateTimeParts } from '@/ui/localDateTime'
import { describeOffset, kindLabel } from '@/ui/notificationText'
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
const emit = defineEmits<{
  submit: [input: ScheduleNotificationInput]
  close: []
  /** The kind changed, so the parent should load that kind's audience. */
  'kind-change': [kind: AudienceKind]
}>()

const { t } = useI18n()

const PAST_ALLOWANCE_MS = 60_000
const OFFSETS = [120, 1_440, 10_080]

type Choice = 'now' | 'custom' | `${number}`

const kind = ref<AudienceKind>('INVITATION')
const choice = ref<Choice>('now')
const message = ref('')
const custom = ref<DateTimeParts>({ startDate: '', startTime: '', endDate: '', endTime: '' })
const customIncomplete = ref(false)

const kinds = computed(() => [
  { value: 'INVITATION', label: kindLabel('INVITATION') },
  { value: 'REMINDER', label: kindLabel('REMINDER') },
])

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
watch(kind, (next) => emit('kind-change', next))

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
  if (a.eligible === 0) return { warning: true, text: t('notify.audience.none') }
  const left = a.total - a.eligible
  const parts = [t('notify.audience.sentTo', { eligible: a.eligible, total: a.total }, a.total)]
  if (left > 0) parts.push(t('notify.audience.paused', { left, type: props.event.eventType.name }, left))
  if (kind.value === 'REMINDER') parts.push(t('notify.audience.skipsNotGoing'))
  return { warning: false, text: parts.join(t('notify.audience.separator')) }
})

const submitLabel = computed(() => (choice.value === 'now' ? t('notify.sendNow') : t('notify.schedule')))

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
  <BaseDialog :open="open" :title="t('notify.title')" @close="emit('close')">
    <div @keydown="onKeydown">
      <SegmentedControl v-model="kind" :options="kinds" :label="t('notify.typeLabel')" />

      <fieldset class="mt-5">
        <legend class="mb-1.5 text-sm font-medium">{{ t('notify.when') }}</legend>
        <div class="divide-y divide-[var(--app-line)]/60 overflow-hidden rounded-xl border border-[var(--app-line)]/60">
          <label :class="ROW">
            <input v-model="choice" type="radio" name="timing" value="now" class="accent-[var(--app-accent)]" />
            <span class="flex-1">{{ t('notification.now') }}</span>
          </label>
          <label v-for="o in options" :key="o.value" :class="ROW">
            <input v-model="choice" type="radio" name="timing" :value="o.value" :disabled="o.late" class="accent-[var(--app-accent)]" />
            <span class="flex-1">{{ o.label }}</span>
            <span v-if="o.late" class="text-sm text-[var(--app-muted)]">{{ t('notify.tooLate') }}</span>
          </label>
          <label :class="ROW">
            <input v-model="choice" type="radio" name="timing" value="custom" class="accent-[var(--app-accent)]" />
            <span class="flex-1">{{ t('notify.custom') }}</span>
          </label>
        </div>
        <p v-if="fieldErrors?.offsetMinutes" class="mt-1.5 text-sm text-[var(--app-danger)]">{{ fieldErrors.offsetMinutes }}</p>
      </fieldset>

      <div v-if="choice === 'custom'" class="mt-4">
        <DateTimeFields v-model="custom" single :start-label="t('notify.sendAt')" :start-error="fieldErrors?.localSendAt ?? (customIncomplete ? t('notify.chooseDateTime') : undefined)" />
        <p v-if="differentZone" class="mt-1.5 text-sm text-[var(--app-muted)]">{{ t('notify.timesIn', { zone: event.timeZone }) }}</p>
      </div>

      <TextAreaField v-model="message" class="mt-5" :label="t('notify.note')" name="message" :maxlength="1000" :error="fieldErrors?.message" />

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
        <BaseButton variant="plain" @click="emit('close')">{{ t('common.cancel') }}</BaseButton>
        <BaseButton :loading="saving" @click="submit">{{ submitLabel }}</BaseButton>
      </div>
    </div>
  </BaseDialog>
</template>
