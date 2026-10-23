<script setup lang="ts">
import { ref, watch } from 'vue'

import BaseButton from './BaseButton.vue'
import BaseDialog from './BaseDialog.vue'
import TextAreaField from './TextAreaField.vue'

const props = defineProps<{
  open: boolean
  title: string
  /** Whether any invitation or reminder has already gone out. */
  notified: boolean
  saving?: boolean
  error?: string
}>()
const emit = defineEmits<{ submit: [message: string]; close: [] }>()

const message = ref('')

// A fresh message each time the dialog opens.
watch(
  () => props.open,
  (open) => {
    if (open) message.value = ''
  },
)
</script>

<template>
  <BaseDialog :open="open" :title="`Cancel '${title}'?`" @close="emit('close')">
    <p class="text-[15px] text-[var(--app-muted)]">
      {{ notified ? 'Members who were notified will get a cancellation email.' : 'No one has been notified yet.' }}
    </p>
    <TextAreaField v-model="message" class="mt-4" label="Add a message" name="message" :maxlength="1000" :error="error" :disabled="saving" />
    <div class="mt-6 flex justify-end gap-2">
      <BaseButton variant="plain" @click="emit('close')">Keep event</BaseButton>
      <BaseButton variant="danger" :loading="saving" @click="emit('submit', message)">Cancel event</BaseButton>
    </div>
  </BaseDialog>
</template>
