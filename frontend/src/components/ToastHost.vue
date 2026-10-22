<script setup lang="ts">
import { useToast } from '@/composables/useToast'

const { toast, dismiss } = useToast()

function runAction() {
  toast.value?.action?.run()
  dismiss()
}
</script>

<template>
  <div role="status" aria-live="polite" class="pointer-events-none fixed inset-x-0 bottom-6 z-50 flex justify-center px-4">
    <Transition name="page">
      <div
        v-if="toast"
        class="pointer-events-auto flex items-center gap-4 rounded-full bg-[var(--app-ink)] py-2 pr-2 pl-5 text-[15px] text-[var(--app-canvas)] shadow-[0_8px_30px_rgba(0,0,0,0.2)]"
      >
        <span>{{ toast.message }}</span>
        <button
          v-if="toast.action"
          type="button"
          class="rounded-full px-3 py-1 font-medium text-[var(--app-accent)] transition-colors duration-150 hover:bg-white/10"
          @click="runAction"
        >
          {{ toast.action.label }}
        </button>
      </div>
    </Transition>
  </div>
</template>
