<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, type LocationQueryRaw } from 'vue-router'

import type { EventItem } from '@/stores/events'
import { eventTypeColor } from '@/ui/eventTypeColors'
import { formatClockTime } from '@/ui/formatEventTime'
import { groupByDay } from '@/ui/groupByDay'

const props = defineProps<{
  items: EventItem[]
  /** The current moment; injectable so "Today" and "Now" are testable. */
  now?: Date
  /** The list's query (when, type, page), carried to the detail page so its back link can restore the view. */
  linkQuery?: LocationQueryRaw
}>()

const sections = computed(() => groupByDay(props.items, props.now ?? new Date()))

function inProgress(event: EventItem): boolean {
  if (event.cancelledAt) return false
  const now = (props.now ?? new Date()).getTime()
  return Date.parse(event.start) <= now && now < Date.parse(event.end)
}
</script>

<template>
  <div class="space-y-6">
    <section v-for="section in sections" :key="section.key">
      <h2
        class="sticky top-14 z-[1] -mx-1 bg-[var(--app-canvas)]/85 px-1 py-2 text-sm font-semibold text-[var(--app-muted)] backdrop-blur-md"
      >
        {{ section.label }}
      </h2>
      <ul
        class="divide-y divide-[var(--app-line)]/60 overflow-hidden rounded-2xl border border-[var(--app-line)]/60 bg-[var(--app-surface)]"
      >
        <li v-for="event in section.events" :key="event.id">
          <RouterLink
            :to="{ path: `/events/${event.id}`, query: linkQuery }"
            class="flex min-h-16 items-stretch gap-3 px-4 py-3 transition-colors duration-150 hover:bg-[var(--app-fill)]/60 focus-visible:-outline-offset-2"
          >
            <span data-times class="flex w-12 shrink-0 flex-col justify-between text-sm tabular-nums">
              <span class="font-medium">{{ formatClockTime(event.start, event.timeZone) }}</span>
              <span class="text-[var(--app-muted)]">{{ formatClockTime(event.end, event.timeZone) }}</span>
            </span>
            <span
              data-color-bar
              class="w-[3px] shrink-0 rounded-full"
              :class="[eventTypeColor(event.eventType.color).dot, event.cancelledAt && 'opacity-40']"
              aria-hidden="true"
            />
            <span class="min-w-0 flex-1">
              <span class="flex items-center gap-2">
                <span data-title class="truncate font-medium" :class="event.cancelledAt && 'text-[var(--app-muted)] line-through'">{{ event.title }}</span>
                <span v-if="event.cancelledAt" data-cancelled class="shrink-0 text-sm text-[var(--app-danger)]">Cancelled</span>
                <span
                  v-if="inProgress(event)"
                  class="shrink-0 rounded-full bg-[var(--app-accent)]/12 px-2 py-0.5 text-xs font-semibold text-[var(--app-accent)]"
                >
                  Now
                </span>
              </span>
              <span class="block truncate text-sm text-[var(--app-muted)]">
                {{ event.eventType.name }}<template v-if="event.location"> · {{ event.location }}</template
                ><template v-if="event.goingCount > 0"> · <span data-going>{{ event.goingCount }} going</span></template>
              </span>
            </span>
          </RouterLink>
        </li>
      </ul>
    </section>
  </div>
</template>
