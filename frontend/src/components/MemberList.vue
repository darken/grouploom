<script setup lang="ts">
import { nextTick, ref, useTemplateRef } from 'vue'

import { useToast } from '@/composables/useToast'
import { useMembersStore, type Member } from '@/stores/members'
import { highlight } from '@/ui/highlight'
import { relativeTime } from '@/ui/relativeTime'
import BaseButton from './BaseButton.vue'
import BaseDialog from './BaseDialog.vue'
import MemberAvatar from './MemberAvatar.vue'
import OverflowMenu, { type OverflowMenuItem } from './OverflowMenu.vue'

defineProps<{ items: Member[]; query?: string }>()

const emit = defineEmits<{
  edit: [item: Member]
  remove: [item: Member]
  'send-email': [item: Member]
}>()

const root = ref<HTMLElement | null>(null)

const menuItems: OverflowMenuItem[] = [
  { id: 'email', label: 'Email preferences link' },
  { id: 'copy-link', label: 'Copy preferences link' },
  { id: 'edit', label: 'Edit' },
  { id: 'remove', label: 'Remove', destructive: true },
]

function choose(action: string, item: Member) {
  if (action === 'edit') emit('edit', item)
  else if (action === 'remove') emit('remove', item)
  else if (action === 'email') emit('send-email', item)
  else if (action === 'copy-link') void copyLink(item)
}

// --- preferences link ---
const toast = useToast()
// Set when the clipboard can't be written to, so the organizer can copy the link by hand.
const fallback = ref<{ name: string; url: string } | null>(null)
const linkField = useTemplateRef<HTMLInputElement>('linkField')

function lifetime(expiresAt: string): string {
  const days = Math.max(1, Math.round((Date.parse(expiresAt) - Date.now()) / 86_400_000))
  return `${days} ${days === 1 ? 'day' : 'days'}`
}

async function copyLink(item: Member) {
  // Looked up here, not at setup, so the list stays presentational until this action is used.
  const link = await useMembersStore().createPreferencesLink(item.id)
  if (!link) {
    toast.show("Couldn't create the link. Try again.")
    return
  }
  try {
    await navigator.clipboard.writeText(link.url)
    toast.show(`Link copied. It works for ${lifetime(link.expiresAt)}.`)
  } catch {
    // No clipboard API (insecure context) or the browser refused (e.g. Safari after an awaited request).
    fallback.value = { name: item.name, url: link.url }
    await nextTick()
    await nextTick()
    linkField.value?.focus()
    linkField.value?.select()
  }
}

function closeFallback() {
  const name = fallback.value?.name
  fallback.value = null
  // The dialog is removed rather than closed, so hand focus back to the row's actions button ourselves.
  void nextTick(() => root.value?.querySelector<HTMLElement>(`button[aria-label="Actions for ${name}"]`)?.focus())
}

function copyFromField() {
  linkField.value?.select()
  if (document.execCommand?.('copy')) {
    toast.show('Link copied.')
    closeFallback()
  }
}
</script>

<template>
  <ul
    ref="root"
    class="divide-y divide-[var(--app-line)]/60 rounded-2xl border border-[var(--app-line)]/60 bg-[var(--app-surface)]"
  >
    <li v-for="item in items" :key="item.id" class="group relative">
      <div class="flex items-center">
        <!-- preventDefault on Enter stops the browser's synthesized click, so edit fires once. -->
        <button
          type="button"
          data-row
          class="flex min-h-16 min-w-0 flex-1 items-center gap-4 rounded-2xl px-5 pt-3 text-left transition-colors duration-150 hover:bg-[var(--app-fill)]/60"
          :class="item.preferencesEmail ? 'pb-1' : 'pb-3'"
          @click="emit('edit', item)"
          @keydown.enter.prevent="emit('edit', item)"
        >
          <MemberAvatar :id="item.id" :name="item.name" />
          <span class="min-w-0 flex-1">
            <span class="flex items-center gap-2">
              <span class="truncate font-medium">
                <template v-for="(part, i) in highlight(item.name, query ?? '')" :key="i">
                  <strong v-if="part.match" data-match class="font-bold">{{ part.text }}</strong>
                  <template v-else>{{ part.text }}</template>
                </template>
              </span>
              <span
                v-if="item.invitationsPaused"
                data-paused
                class="shrink-0 rounded-full bg-[var(--app-fill)] px-2 py-0.5 text-xs font-medium text-[var(--app-muted)]"
                >Paused</span
              >
            </span>
            <span class="block truncate text-sm text-[var(--app-muted)]">
              <template v-for="(part, i) in highlight(item.email, query ?? '')" :key="i">
                <strong v-if="part.match" data-match class="font-bold">{{ part.text }}</strong>
                <template v-else>{{ part.text }}</template>
              </template>
            </span>
          </span>
        </button>
        <div class="pr-3">
          <OverflowMenu
            :label="`Actions for ${item.name}`"
            :items="menuItems"
            trigger-class="focus-visible:opacity-100 group-focus-within:opacity-100 group-hover:opacity-100 sm:opacity-0 [@media(hover:none)]:opacity-100"
            @select="choose($event, item)"
          />
        </div>
      </div>
      <!-- Outside the row button so "Try again" is not a button inside a button. -->
      <p
        v-if="item.preferencesEmail"
        data-email-status
        role="status"
        class="pb-3 pl-[4.75rem] pr-5 text-sm"
        :class="item.preferencesEmail.status === 'FAILED' ? 'text-[var(--app-danger)]' : 'text-[var(--app-muted)]'"
      >
        <template v-if="item.preferencesEmail.status === 'FAILED'">
          Couldn't deliver link
          <button type="button" class="font-medium underline underline-offset-2" @click="emit('send-email', item)">
            Try again
          </button>
        </template>
        <template v-else-if="item.preferencesEmail.status === 'SENT'">
          Link sent · {{ relativeTime(item.preferencesEmail.at) }}
        </template>
        <template v-else>Sending link…</template>
      </p>
    </li>
  </ul>

  <BaseDialog v-if="fallback" title="Preferences link" open @close="closeFallback">
    <p class="mb-3 text-[15px] text-[var(--app-muted)]">
      Copy this link and send it to {{ fallback.name }}. It's personal to them.
    </p>
    <input
      ref="linkField"
      type="text"
      readonly
      :value="fallback.url"
      aria-label="Preferences link"
      class="min-h-11 w-full rounded-xl border border-[var(--app-line)] bg-[var(--app-fill)] px-3 text-[15px]"
      @focus="linkField?.select()"
    />
    <div class="mt-5 flex justify-end gap-2">
      <BaseButton variant="plain" @click="closeFallback">Done</BaseButton>
      <BaseButton @click="copyFromField">Copy</BaseButton>
    </div>
  </BaseDialog>
</template>