<script setup lang="ts">
import { RouterLink } from 'vue-router'

import UserMenu from './UserMenu.vue'
</script>

<template>
  <header
    class="sticky top-0 z-10 border-b border-[var(--app-line)]/60 bg-[var(--app-surface)]/72 backdrop-blur-xl"
  >
    <div class="mx-auto flex h-14 max-w-5xl items-center justify-between px-6">
      <RouterLink to="/" class="flex items-center gap-2 rounded-md text-[17px] font-semibold tracking-tight">
        <svg class="size-5 text-[var(--app-accent)]" viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <path d="M3 6c4-3 10 3 14 0M3 10c4-3 10 3 14 0M3 14c4-3 10 3 14 0" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
        </svg>
        GroupLoom
      </RouterLink>
      <UserMenu />
    </div>
  </header>
</template>
