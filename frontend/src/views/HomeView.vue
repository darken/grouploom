<script setup lang="ts">
import EmptyState from '@/components/EmptyState.vue'
</script>

<template>
  <section>
    <h1 class="text-4xl font-semibold tracking-tight sm:text-5xl">Welcome to GroupLoom</h1>
    <p class="mt-4 max-w-xl text-[17px] text-[var(--app-muted)]">
      Plan events, keep your members in the loop, and see who’s coming.
    </p>
    <div class="mt-12">
      <EmptyState title="Nothing here yet" description="Your groups and events will show up here." />
    </div>
  </section>
</template>
