<script setup lang="ts">
import { onMounted, useId, useTemplateRef, watch } from 'vue'

const props = defineProps<{ title: string; open: boolean }>()
const emit = defineEmits<{ close: [] }>()

const titleId = useId()
const dialog = useTemplateRef<HTMLDialogElement>('dialog')
let opener: HTMLElement | null = null

function show() {
  opener = document.activeElement instanceof HTMLElement ? document.activeElement : null
  dialog.value?.showModal()
}

function hide() {
  dialog.value?.close()
  opener?.focus()
  opener = null
}

onMounted(() => {
  if (props.open) show()
})

watch(
  () => props.open,
  (open) => (open ? show() : hide()),
  { flush: 'post' },
)

function onBackdropClick(event: MouseEvent) {
  // Clicks on the padding-free <dialog> itself can only come from the backdrop.
  if (event.target === dialog.value) emit('close')
}
</script>

<template>
  <dialog
    ref="dialog"
    :aria-labelledby="titleId"
    class="m-auto max-h-[calc(100dvh-2rem)] w-full max-w-md overflow-y-auto overscroll-contain rounded-2xl border border-[var(--app-line)]/60 bg-[var(--app-surface)] p-0 text-[var(--app-ink)] shadow-[0_20px_60px_rgba(0,0,0,0.18)] transition-[opacity,transform,overlay,display] duration-150 ease-out backdrop:bg-black/30 backdrop:backdrop-blur-sm max-sm:mb-0 max-sm:max-w-none max-sm:rounded-b-none open:opacity-100 starting:open:opacity-0 starting:open:scale-95"
    @cancel.prevent="emit('close')"
    @click="onBackdropClick"
  >
    <div v-if="open" class="p-6">
      <h2 :id="titleId" class="mb-4 text-xl font-semibold tracking-tight">{{ title }}</h2>
      <slot />
    </div>
  </dialog>
</template>
