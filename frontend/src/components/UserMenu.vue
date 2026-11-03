<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

import { useI18n } from 'vue-i18n'

import GroupLanguageSelect from '@/components/GroupLanguageSelect.vue'
import { useToast } from '@/composables/useToast'
import { useAuthStore, type GroupLanguage } from '@/stores/auth'
import { initials as initialsOf } from '@/ui/initials'

const auth = useAuthStore()
const { t } = useI18n()
const toast = useToast()

const open = ref(false)
const root = ref<HTMLElement | null>(null)
const trigger = ref<HTMLButtonElement | null>(null)
const popover = ref<HTMLElement | null>(null)

// Only the owner may change group settings.
const canEditGroup = computed(() => auth.user?.role === 'OWNER')
// Bumped when a save fails, so the select goes back to the saved language.
const selectKey = ref(0)

async function changeLanguage(language: GroupLanguage) {
  if (await auth.updateGroupLanguage(language)) return
  selectKey.value++
  toast.show(t('account.languageFailed'))
}

const initials = computed(() => initialsOf(auth.user?.displayName ?? ''))

function focusables(): HTMLElement[] {
  return Array.from(popover.value?.querySelectorAll<HTMLElement>('button, [href], select') ?? [])
}

async function toggle() {
  open.value = !open.value
  if (open.value) {
    await nextTick()
    // The first button, so the settings above it don't take focus from the menu's main action.
    focusables().find((el) => el.tagName !== 'SELECT')?.focus()
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
      :aria-label="t('nav.account')"
      @click="toggle"
    >
      {{ initials }}
    </button>
    <div
      v-if="open"
      ref="popover"
      role="dialog"
      :aria-label="t('nav.account')"
      class="absolute right-0 mt-2 w-64 rounded-2xl border border-[var(--app-line)]/60 bg-[var(--app-surface)] p-4 shadow-[0_8px_30px_rgba(0,0,0,0.08)]"
    >
      <p class="font-medium">{{ auth.user.displayName }}</p>
      <p class="truncate text-sm text-[var(--app-muted)]">{{ auth.user.email }}</p>
      <p class="mt-1 text-sm text-[var(--app-muted)]">{{ auth.user.group.name }}</p>
      <GroupLanguageSelect
        v-if="canEditGroup"
        :key="selectKey"
        class="mt-4"
        :model-value="auth.user.group.language"
        @update:model-value="changeLanguage"
      />
      <button
        type="button"
        class="mt-4 w-full rounded-xl px-3 py-2 text-left text-[15px] text-[var(--app-accent)] transition-colors duration-150 hover:bg-[var(--app-accent)]/10"
        @click="signOut"
      >
        {{ t('nav.signOut') }}
      </button>
    </div>
  </div>
</template>
