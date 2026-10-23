<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

import { useAuthStore } from '@/stores/auth'
import { initials as initialsOf } from '@/ui/initials'

const auth = useAuthStore()

const open = ref(false)
const root = ref<HTMLElement | null>(null)
const trigger = ref<HTMLButtonElement | null>(null)
const popover = ref<HTMLElement | null>(null)

const initials = computed(() => initialsOf(auth.user?.displayName ?? ''))

function focusables(): HTMLElement[] {
  return Array.from(popover.value?.querySelectorAll<HTMLElement>('button, [href]') ?? [])
}

async function toggle() {
  open.value = !open.value
  if (open.value) {
    await nextTick()
    focusables()[0]?.focus()
  }
}

function close(returnFocus = true) {
  open.value = false
  if (returnFocus) trigger.value?.focus()
}

function onKeydown(event: KeyboardEvent) {
  if (!open.value) return
  if (event.key === 'Escape') {
    event.preventDefault()
    close()
  } else if (event.key === 'Tab') {
    // Keep focus inside the popover while it is open.
    const items = focusables()
    const first = items[0]
    const last = items[items.length - 1]
    if (!first || !last) return
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first.focus()
    }
  }
}

function onPointerDown(event: Event) {
  if (open.value && root.value && !root.value.contains(event.target as Node)) close(false)
}

async function signOut() {
  close(false)
  await auth.logout()
}

onMounted(() => document.addEventListener('pointerdown', onPointerDown))
onBeforeUnmount(() => document.removeEventListener('pointerdown', onPointerDown))
</script>

<template>
  <div v-if="auth.user" ref="root" class="relative" @keydown="onKeydown">
    <button
      ref="trigger"
      type="button"
      class="grid size-9 place-items-center rounded-full bg-[var(--app-fill)] text-sm font-semibold transition-colors duration-150 hover:bg-[var(--app-line)]"
      aria-haspopup="dialog"
      :aria-expanded="open"
      aria-label="Account"
      @click="toggle"
    >
      {{ initials }}
    </button>
    <div
      v-if="open"
      ref="popover"
      role="dialog"
      aria-label="Account"
      class="absolute right-0 mt-2 w-64 rounded-2xl border border-[var(--app-line)]/60 bg-[var(--app-surface)] p-4 shadow-[0_8px_30px_rgba(0,0,0,0.08)]"
    >
      <p class="font-medium">{{ auth.user.displayName }}</p>
      <p class="truncate text-sm text-[var(--app-muted)]">{{ auth.user.email }}</p>
      <p class="mt-1 text-sm text-[var(--app-muted)]">{{ auth.user.group.name }}</p>
      <button
        type="button"
        class="mt-4 w-full rounded-xl px-3 py-2 text-left text-[15px] text-[var(--app-accent)] transition-colors duration-150 hover:bg-[var(--app-accent)]/10"
        @click="signOut"
      >
        Sign out
      </button>
    </div>
  </div>
</template>
