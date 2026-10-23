<script setup lang="ts">
import { computed, reactive, ref, useTemplateRef } from 'vue'

import type { Member, MemberDraft } from '@/stores/members'
import BaseButton from './BaseButton.vue'
import TextField from './TextField.vue'

const props = defineProps<{ member?: Member; saving?: boolean; fieldErrors?: Record<string, string> }>()

const emit = defineEmits<{ submit: [payload: MemberDraft]; 'submit-another': [payload: MemberDraft]; cancel: [] }>()

const initial = { name: props.member?.name ?? '', email: props.member?.email ?? '' }

const form = reactive({ ...initial })
const touched = ref<Record<string, boolean>>({})
const submitted = ref(false)
const root = useTemplateRef<HTMLFormElement>('root')

// Mirrors the backend: @Email(regexp = ".+@.+\\..+"), max 254; name max 120.
const EMAIL_PATTERN = /^\S+@\S+\.\S+$/

const clientErrors = computed(() => {
  const name = form.name.trim()
  const email = form.email.trim()
  return {
    name: !name ? 'Enter a name.' : name.length > 120 ? 'Use 120 characters or fewer.' : '',
    email: !email
      ? 'Enter an email address.'
      : email.length > 254
        ? 'Use 254 characters or fewer.'
        : !EMAIL_PATTERN.test(email)
          ? 'Enter a valid email address.'
          : '',
  }
})

type Field = keyof typeof clientErrors.value

function errorFor(field: Field) {
  const showClient = touched.value[field] || submitted.value
  return (showClient ? clientErrors.value[field] : '') || props.fieldErrors?.[field] || ''
}

const valid = computed(() => Object.values(clientErrors.value).every((e) => !e))
const changed = computed(() => form.name !== initial.name || form.email !== initial.email)
const canSave = computed(() => valid.value && changed.value && !props.saving)

function payload(): MemberDraft {
  return {
    name: form.name.trim(),
    email: form.email.trim(),
    ...(props.member ? { version: props.member.version } : {}),
  }
}

function submit(another = false) {
  submitted.value = true
  if (!valid.value) return
  if (another) emit('submit-another', payload())
  else emit('submit', payload())
}

function focusName() {
  root.value?.querySelector<HTMLInputElement>('input[name=name]')?.focus()
}

/** Clears the form for the next entry ("Save and add another") and puts the cursor back in Name. */
function reset() {
  form.name = ''
  form.email = ''
  touched.value = {}
  submitted.value = false
  focusName()
}

defineExpose({ reset, focusName })

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && (event.metaKey || event.ctrlKey) && canSave.value) {
    event.preventDefault()
    submit()
  }
}
</script>

<template>
  <form ref="root" class="space-y-5" novalidate @submit.prevent="submit()" @keydown="onKeydown">
    <TextField
      v-model="form.name"
      label="Name"
      name="name"
      autocomplete="off"
      :error="errorFor('name')"
      :disabled="saving"
      @blur="touched.name = true"
    />
    <TextField
      v-model="form.email"
      label="Email"
      name="email"
      type="email"
      autocomplete="off"
      :error="errorFor('email')"
      :disabled="saving"
      @blur="touched.email = true"
    />
    <div class="flex flex-col-reverse gap-2 pt-2 sm:flex-row sm:justify-end">
      <BaseButton class="max-sm:w-full" variant="plain" @click="emit('cancel')">Cancel</BaseButton>
      <BaseButton v-if="!member" class="max-sm:w-full" variant="secondary" :disabled="!canSave" @click="submit(true)">
        Save and add another
      </BaseButton>
      <BaseButton class="max-sm:w-full" type="submit" :loading="saving" :disabled="!canSave">Save</BaseButton>
    </div>
  </form>
</template>
