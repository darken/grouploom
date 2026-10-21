<script setup lang="ts">
import { computed } from 'vue'

import { useSystemStore } from '@/stores/system'
import AppHeader from './AppHeader.vue'

const system = useSystemStore()
const version = computed(() => system.info?.version)
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <AppHeader />
    <main class="mx-auto w-full max-w-5xl flex-1 px-6 py-16">
      <slot />
    </main>
    <footer class="mx-auto w-full max-w-5xl px-6 py-8 text-sm text-[var(--app-muted)]">
      <span v-if="version">GroupLoom v{{ version }}</span>
    </footer>
  </div>
</template>
