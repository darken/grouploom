<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import type { EventType, EventTypeDraft } from '@/stores/eventTypes'
import BaseButton from './BaseButton.vue'
import ColorSwatchPicker from './ColorSwatchPicker.vue'
import DurationSelect from './DurationSelect.vue'
import TextAreaField from './TextAreaField.vue'
import TextField from './TextField.vue'

const { t } = useI18n()

const props = defineProps<{ eventType?: EventType; saving?: boolean; fieldErrors?: Record<string, string> }>()

const emit = defineEmits<{ submit: [payload: EventTypeDraft]; cancel: [] }>()

const initial = {
  name: props.eventType?.name ?? '',
  description: props.eventType?.description ?? '',
  color: props.eventType?.color ?? 'blue',
  defaultDurationMinutes: props.eventType?.defaultDurationMinutes,
}

const form = reactive({ ...initial })
const touched = ref<Record<string, boolean>>({})
const submitted = ref(false)

const clientErrors = computed(() => {
  const name = form.name.trim()
  const minutes = form.defaultDurationMinutes
  return {
    name: !name ? t('eventTypes.form.enterName') : name.length > 80 ? t('common.maxChars', { n: 80 }) : '',
    description: form.description.trim().length > 500 ? t('common.maxChars', { n: 500 }) : '',
    defaultDurationMinutes:
      minutes !== undefined && (!Number.isInteger(minutes) || minutes < 5 || minutes > 1440)
        ? t('eventTypes.form.durationRange')
        : '',
  }
})

type Field = keyof typeof clientErrors.value

function errorFor(field: Field) {
  const showClient = touched.value[field] || submitted.value
  return (showClient ? clientErrors.value[field] : '') || props.fieldErrors?.[field] || ''
}

const valid = computed(() => Object.values(clientErrors.value).every((e) => !e))
const changed = computed(
  () =>
    form.name !== initial.name ||
    form.description !== initial.description ||
    form.color !== initial.color ||
    form.defaultDurationMinutes !== initial.defaultDurationMinutes,
)
const canSave = computed(() => valid.value && changed.value && !props.saving)

function submit() {
  submitted.value = true
  if (!valid.value) return
  emit('submit', {
    name: form.name.trim(),
    description: form.description.trim() || undefined,
    color: form.color,
    defaultDurationMinutes: form.defaultDurationMinutes,
    ...(props.eventType ? { archived: props.eventType.archived, version: props.eventType.version } : {}),
  })
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
      v-model="form.name"
      :label="t('eventTypes.form.name')"
      name="name"
      autocomplete="off"
      :error="errorFor('name')"
      :disabled="saving"
      @blur="touched.name = true"
    />
    <TextAreaField
      v-model="form.description"
      :label="t('eventTypes.form.description')"
      name="description"
      :maxlength="500"
      :error="errorFor('description')"
      :disabled="saving"
      @blur="touched.description = true"
    />
    <div>
      <p class="mb-1.5 text-sm font-medium">{{ t('eventTypes.color') }}</p>
      <ColorSwatchPicker v-model="form.color" />
    </div>
    <DurationSelect v-model="form.defaultDurationMinutes" :error="errorFor('defaultDurationMinutes')" />
    <div class="flex justify-end gap-2 pt-2">
      <BaseButton variant="plain" @click="emit('cancel')">{{ t('common.cancel') }}</BaseButton>
      <BaseButton type="submit" :loading="saving" :disabled="!canSave">{{ t('common.save') }}</BaseButton>
    </div>
  </form>
</template>
