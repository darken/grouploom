<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'

import type { SaveStatus } from '@/stores/memberPreferences'

const props = defineProps<{ status: SaveStatus }>()
const emit = defineEmits<{ retry: [] }>()

// "Saved" is a confirmation, not a state: it fades away after two seconds.
const SAVED_MS = 2000
const savedVisible = ref(props.status === 'saved')
let timer: ReturnType<typeof setTimeout> | undefined

function showSaved() {
  savedVisible.value = true
  clearTimeout(timer)
  timer = setTimeout(() => (savedVisible.value = false), SAVED_MS)
}

if (props.status === 'saved') showSaved()

watch(
  () => props.status,
  (status) => {
    if (status === 'saved') showSaved()
    else {
      clearTimeout(timer)
      savedVisible.value = false
    }
  },
)

onBeforeUnmount(() => clearTimeout(timer))
</script>

<template>
  <span aria-live="polite" class="text-[13px] text-[var(--app-muted)]">
    <template v-if="status === 'saving'">Saving…</template>
    <Transition name="fade">
      <span v-if="status === 'saved' && savedVisible">Saved</span>
    </Transition>
    <template v-if="status === 'error'">
      Couldn't save.
      <button type="button" class="rounded font-medium text-[var(--app-accent)] underline-offset-2 hover:underline" @click="emit('retry')">Try again.</button>
    </template>
  </span>
</template>

<style scoped>
.fade-leave-active {
  transition: opacity 300ms ease-out;
}
.fade-leave-to {
  opacity: 0;
}
@media (prefers-reduced-motion: reduce) {
  .fade-leave-active {
    transition: none;
  }
}
</style>
