<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

import type { Member } from '@/stores/members'
import { highlight } from '@/ui/highlight'
import MemberAvatar from './MemberAvatar.vue'

defineProps<{ items: Member[]; query?: string }>()

const emit = defineEmits<{
  edit: [item: Member]
  remove: [item: Member]
}>()

const openId = ref<string | null>(null)
const root = ref<HTMLElement | null>(null)

async function toggle(item: Member, event: Event) {
  // currentTarget is cleared once the handler yields, so grab it first.
  const container = (event.currentTarget as HTMLElement).parentElement
  openId.value = openId.value === item.id ? null : item.id
  if (openId.value) {
    await nextTick()
    container?.querySelector<HTMLElement>('[role=menuitem]')?.focus()
  }
}

function closeMenu(returnFocusTo?: HTMLElement | null) {
  openId.value = null
  returnFocusTo?.focus()
}

function onMenuKeydown(event: KeyboardEvent) {
  const menu = event.currentTarget as HTMLElement
  if (event.key === 'Escape') {
    event.preventDefault()
    closeMenu(menu.parentElement?.querySelector<HTMLElement>('button[aria-haspopup]'))
    return
  }
  if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return
  event.preventDefault()
  const items = Array.from(menu.querySelectorAll<HTMLElement>('[role=menuitem]'))
  const at = items.indexOf(document.activeElement as HTMLElement)
  const next = (at + (event.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length
  items[next]?.focus()
}

function choose(action: 'edit' | 'remove', item: Member) {
  closeMenu()
  if (action === 'edit') emit('edit', item)
  else emit('remove', item)
}

function onPointerDown(event: Event) {
  if (openId.value && root.value && !root.value.contains(event.target as Node)) openId.value = null
}

onMounted(() => document.addEventListener('pointerdown', onPointerDown))
onBeforeUnmount(() => document.removeEventListener('pointerdown', onPointerDown))
</script>

<template>
  <ul
    ref="root"
    class="divide-y divide-[var(--app-line)]/60 rounded-2xl border border-[var(--app-line)]/60 bg-[var(--app-surface)]"
  >
    <li v-for="item in items" :key="item.id" class="group relative flex items-center">
      <!-- preventDefault on Enter stops the browser's synthesized click, so edit fires once. -->
      <button
        type="button"
        data-row
        class="flex min-h-16 min-w-0 flex-1 items-center gap-4 rounded-2xl px-5 py-3 text-left transition-colors duration-150 hover:bg-[var(--app-fill)]/60"
        @click="emit('edit', item)"
        @keydown.enter.prevent="emit('edit', item)"
      >
        <MemberAvatar :id="item.id" :name="item.name" />
        <span class="min-w-0 flex-1">
          <span class="block truncate font-medium">
            <template v-for="(part, i) in highlight(item.name, query ?? '')" :key="i">
              <strong v-if="part.match" data-match class="font-bold">{{ part.text }}</strong>
              <template v-else>{{ part.text }}</template>
            </template>
          </span>
          <span class="block truncate text-sm text-[var(--app-muted)]">
            <template v-for="(part, i) in highlight(item.email, query ?? '')" :key="i">
              <strong v-if="part.match" data-match class="font-bold">{{ part.text }}</strong>
              <template v-else>{{ part.text }}</template>
            </template>
          </span>
        </span>
      </button>
      <div class="relative pr-3">
        <button
          type="button"
          aria-haspopup="menu"
          :aria-expanded="openId === item.id"
          :aria-label="`Actions for ${item.name}`"
          class="grid size-9 place-items-center rounded-full text-[var(--app-muted)] transition-opacity duration-150 hover:bg-[var(--app-fill)] focus-visible:opacity-100 group-focus-within:opacity-100 group-hover:opacity-100 sm:opacity-0 [@media(hover:none)]:opacity-100"
          @click="toggle(item, $event)"
        >
          <svg class="size-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
            <circle cx="4.5" cy="10" r="1.5" /><circle cx="10" cy="10" r="1.5" /><circle cx="15.5" cy="10" r="1.5" />
          </svg>
        </button>
        <div
          v-if="openId === item.id"
          role="menu"
          class="absolute right-3 z-10 mt-1 w-44 rounded-xl border border-[var(--app-line)]/60 bg-[var(--app-surface)] p-1 shadow-[0_8px_30px_rgba(0,0,0,0.1)]"
          @keydown="onMenuKeydown"
        >
          <button role="menuitem" type="button" class="block w-full rounded-lg px-3 py-2 text-left text-[15px] hover:bg-[var(--app-fill)]" @click="choose('edit', item)">Edit</button>
          <button role="menuitem" type="button" class="block w-full rounded-lg px-3 py-2 text-left text-[15px] text-[var(--app-danger)] hover:bg-[var(--app-fill)]" @click="choose('remove', item)">Remove</button>
        </div>
      </div>
    </li>
  </ul>
</template>
