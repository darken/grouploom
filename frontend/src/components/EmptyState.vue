<script setup lang="ts">
defineProps<{ title: string; description?: string }>()
</script>

<template>
  <div
    class="flex flex-col items-center rounded-2xl border border-dashed border-[var(--app-line)] px-6 py-14 text-center"
  >
    <h2 class="text-lg font-semibold tracking-tight">{{ title }}</h2>
    <p v-if="description" class="mt-1 max-w-sm text-[15px] text-[var(--app-muted)]">{{ description }}</p>
    <div class="mt-5 empty:hidden"><slot /></div>
  </div>
</template>
