<script setup lang="ts">
import { RouterLink, useRoute } from 'vue-router'

import UserMenu from './UserMenu.vue'

const route = useRoute()

const links = [
  { to: '/', label: 'Home' },
  { to: '/events', label: 'Events' },
  { to: '/event-types', label: 'Event types' },
  { to: '/members', label: 'Members' },
]

// A section stays current on its sub-pages (/events/:id), which RouterLink's exact match doesn't do.
function isCurrent(to: string) {
  return route.path === to || (to !== '/' && route.path.startsWith(`${to}/`))
}
</script>

<template>
  <header
    class="sticky top-0 z-10 border-b border-[var(--app-line)]/60 bg-[var(--app-surface)]/72 backdrop-blur-xl"
  >
    <div class="mx-auto flex h-14 max-w-5xl items-center justify-between px-4 sm:px-6">
      <RouterLink to="/" class="flex items-center gap-2 rounded-md text-[17px] font-semibold tracking-tight">
        <svg class="size-5 text-[var(--app-accent)]" viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <path d="M3 6c4-3 10 3 14 0M3 10c4-3 10 3 14 0M3 14c4-3 10 3 14 0" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
        </svg>
        <span class="max-sm:sr-only">GroupLoom</span>
      </RouterLink>
      <nav aria-label="Primary" class="mr-auto ml-3 flex gap-3.5 text-[14px] whitespace-nowrap sm:ml-8 sm:gap-6 sm:text-[15px]">
        <RouterLink v-for="link in links" :key="link.to" v-slot="{ href, navigate }" :to="link.to" custom>
          <a :href="href" class="nav-link py-1" :aria-current="isCurrent(link.to) ? 'page' : undefined" @click="navigate">{{ link.label }}</a>
        </RouterLink>
      </nav>
      <UserMenu />
    </div>
  </header>
</template>
