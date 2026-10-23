<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, useTemplateRef } from 'vue'

export interface OverflowMenuItem {
  id: string
  label: string
  destructive?: boolean
  disabled?: boolean
}

defineProps<{ label: string; items: OverflowMenuItem[]; triggerClass?: string }>()
const emit = defineEmits<{ select: [id: string] }>()

const open = ref(false)
const root = useTemplateRef<HTMLElement>('root')
const trigger = useTemplateRef<HTMLButtonElement>('trigger')

const enabledItems = () =>
  Array.from(root.value?.querySelectorAll<HTMLElement>('[role=menuitem]:not(:disabled)') ?? [])

async function toggle() {
  open.value = !open.value
  if (open.value) {
    await nextTick()
    enabledItems()[0]?.focus()
  }
}

function close(returnFocus = false) {
  open.value = false
  if (returnFocus) trigger.value?.focus()
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault()
    close(true)
    return
  }
  if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return
  event.preventDefault()
  const rows = enabledItems()
  if (rows.length === 0) return
  const at = rows.indexOf(document.activeElement as HTMLElement)
  rows[(at + (event.key === 'ArrowDown' ? 1 : -1) + rows.length) % rows.length]?.focus()
}

function choose(id: string) {
  close()
  emit('select', id)
}

function onPointerDown(event: Event) {
  if (open.value && root.value && !root.value.contains(event.target as Node)) open.value = false
}

onMounted(() => document.addEventListener('pointerdown', onPointerDown))
onBeforeUnmount(() => document.removeEventListener('pointerdown', onPointerDown))
</script>

<template>
  <div ref="root" class="relative">
    <button
      ref="trigger"
      type="button"
      aria-haspopup="menu"
      :aria-expanded="open"
      :aria-label="label"
      class="grid size-9 place-items-center rounded-full text-[var(--app-muted)] transition-opacity duration-150 hover:bg-[var(--app-fill)]"
      :class="triggerClass"
      @click="toggle"
    >
      <svg class="size-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
        <circle cx="4.5" cy="10" r="1.5" /><circle cx="10" cy="10" r="1.5" /><circle cx="15.5" cy="10" r="1.5" />
      </svg>
    </button>
    <div
      v-if="open"
      role="menu"
      class="absolute right-0 z-10 mt-1 w-max min-w-48 rounded-xl border border-[var(--app-line)]/60 bg-[var(--app-surface)] p-1 shadow-[0_8px_30px_rgba(0,0,0,0.1)]"
      @keydown="onKeydown"
    >
      <button
        v-for="item in items"
        :key="item.id"
        role="menuitem"
        type="button"
        :disabled="item.disabled"
        class="block w-full whitespace-nowrap rounded-lg px-3 py-2 text-left text-[15px] hover:bg-[var(--app-fill)] disabled:opacity-40 disabled:hover:bg-transparent"
        :class="item.destructive ? 'text-[var(--app-danger)]' : ''"
        @click="choose(item.id)"
      >
        {{ item.label }}
      </button>
    </div>
  </div>
</template>
