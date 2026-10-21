<script setup lang="ts">
import EmptyState from '@/components/EmptyState.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
</script>

<template>
  <section>
    <h1 class="text-4xl font-semibold tracking-tight sm:text-5xl">Welcome, {{ auth.user?.displayName }}</h1>
    <p class="mt-4 text-[17px] text-[var(--app-muted)]">{{ auth.user?.group.name }}</p>
    <div class="mt-12">
      <EmptyState title="No events yet" description="Events you create will appear here." />
    </div>
  </section>
</template>
