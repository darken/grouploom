<script setup lang="ts">
withDefaults(defineProps<{ variant?: 'primary' | 'secondary' | 'plain'; type?: 'button' | 'submit' }>(), {
  variant: 'primary',
  type: 'button',
})

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
    class="inline-flex items-center justify-center rounded-full px-5 py-2 text-[15px] font-medium transition-colors duration-150 ease-out disabled:opacity-50"
    :class="styles[variant]"
  >
    <slot />
  </button>
</template>
