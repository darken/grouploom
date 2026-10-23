<script setup lang="ts">
import BaseButton from './BaseButton.vue'
import BaseDialog from './BaseDialog.vue'

defineProps<{
  open: boolean
  title: string
  message: string
  confirmLabel: string
  destructive?: boolean
  /** For a notice that only needs acknowledging. */
  hideCancel?: boolean
}>()
const emit = defineEmits<{ confirm: []; cancel: [] }>()
</script>

<template>
  <BaseDialog :open="open" :title="title" @close="emit('cancel')">
    <p class="text-[15px] text-[var(--app-muted)]">{{ message }}</p>
    <div class="mt-6 flex justify-end gap-2">
      <BaseButton v-if="!hideCancel" variant="plain" @click="emit('cancel')">Cancel</BaseButton>
      <BaseButton :variant="destructive ? 'danger' : 'primary'" @click="emit('confirm')">{{ confirmLabel }}</BaseButton>
    </div>
  </BaseDialog>
</template>
