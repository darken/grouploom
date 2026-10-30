<script setup lang="ts">
import type { MemberAttendance } from '@/stores/memberAttendance'
import { eventTypeColor } from '@/ui/eventTypeColors'
import { formatEventTime } from '@/ui/formatEventTime'
import { browserTimeZone } from '@/ui/timeZones'

withDefaults(defineProps<{ event: MemberAttendance['event']; viewerZone?: string }>(), {
  viewerZone: () => browserTimeZone(),
})
</script>

<template>
  <article class="rounded-2xl border border-[var(--app-line)]/60 bg-[var(--app-surface)] p-5">
    <p class="flex items-center gap-2 text-[15px] text-[var(--app-muted)]">
      <span data-color-dot class="size-2.5 rounded-full" :class="eventTypeColor(event.typeColor).dot" aria-hidden="true" />
      <span data-type>{{ event.typeName }}</span>
    </p>
    <h2 class="mt-1 text-2xl font-semibold tracking-tight break-words" :class="event.cancelled && 'line-through decoration-2'">
      {{ event.title }}
    </h2>
    <p data-time class="mt-2 text-[17px] tabular-nums">{{ formatEventTime(event, viewerZone) }}</p>
    <p v-if="event.location" data-location class="mt-1 text-[17px]">{{ event.location }}</p>
    <p v-if="event.description" data-description class="mt-4 text-[15px] leading-relaxed whitespace-pre-line text-[var(--app-muted)]">{{ event.description }}</p>
  </article>
</template>
