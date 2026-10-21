<script setup lang="ts">
withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary' | 'plain'
    type?: 'button' | 'submit'
    loading?: boolean
    block?: boolean
  }>(),
  { variant: 'primary', type: 'button', loading: false, block: false },
)

const styles = {
  primary: 'bg-[var(--app-accent)] text-white hover:bg-[var(--app-accent-hover)]',
  secondary:
    'border border-[var(--app-line)] bg-[var(--app-surface)] text-[var(--app-ink)] hover:border-[var(--app-muted)]',
  plain: 'text-[var(--app-accent)] hover:bg-[var(--app-accent)]/10',
}
</script>

<template>
  <button
    :type="type"
    :disabled="loading"
    :aria-busy="loading"
    class="inline-flex min-h-11 items-center justify-center gap-2 rounded-full px-5 py-2 text-[15px] font-medium transition-colors duration-150 ease-out disabled:opacity-60"
    :class="[styles[variant], block && 'w-full']"
  >
    <svg v-if="loading" class="size-4 animate-spin" viewBox="0 0 20 20" fill="none" aria-hidden="true">
      <circle cx="10" cy="10" r="7" stroke="currentColor" stroke-opacity="0.3" stroke-width="2" />
      <path d="M17 10a7 7 0 0 0-7-7" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
    </svg>
    <slot />
  </button>
</template>
