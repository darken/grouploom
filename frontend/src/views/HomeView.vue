<script setup lang="ts">
import { onMounted } from 'vue'
import { RouterLink } from 'vue-router'

import BaseButton from '@/components/BaseButton.vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorBanner from '@/components/ErrorBanner.vue'
import EventList from '@/components/EventList.vue'
import { useAuthStore } from '@/stores/auth'
import { useEventsStore } from '@/stores/events'

const auth = useAuthStore()
const events = useEventsStore()

onMounted(() => events.fetchNextUp())
</script>

<template>
  <section>
    <h1 class="text-4xl font-semibold tracking-tight sm:text-5xl">Welcome, {{ auth.user?.displayName }}</h1>
    <p class="mt-4 text-[17px] text-[var(--app-muted)]">{{ auth.user?.group.name }}</p>

    <div class="mt-12">
      <div class="mb-4 flex items-baseline justify-between gap-4">
        <h2 class="text-2xl font-semibold tracking-tight">Next up</h2>
        <RouterLink
          v-if="events.nextUp.length"
          to="/events"
          class="rounded-md text-[15px] text-[var(--app-accent)] hover:underline"
        >
          See all events
        </RouterLink>
      </div>

      <ErrorBanner v-if="events.nextUpError" :error="events.nextUpError">
        <BaseButton variant="plain" @click="events.fetchNextUp()">Retry</BaseButton>
      </ErrorBanner>
      <EventList v-else-if="events.nextUp.length" :items="events.nextUp" />
      <EmptyState
        v-else-if="events.nextUpLoaded"
        title="Nothing scheduled"
        description="Events you schedule will show up here."
      >
        <RouterLink
          to="/events"
          class="inline-flex min-h-11 items-center rounded-full bg-[var(--app-accent)] px-5 py-2 text-[15px] font-medium text-white hover:bg-[var(--app-accent-hover)]"
        >
          Schedule an event
        </RouterLink>
      </EmptyState>
    </div>
  </section>
</template>
