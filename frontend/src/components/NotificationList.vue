<script setup lang="ts">
import { computed } from 'vue'

import OverflowMenu from '@/components/OverflowMenu.vue'
import { sortNotifications, type EventNotification } from '@/stores/eventNotifications'
import { describeFailures, describeStatusBase, kindLabel } from '@/ui/notificationText'

const props = defineProps<{ items: EventNotification[] }>()
const emit = defineEmits<{ unschedule: [id: string] }>()

const sorted = computed(() => sortNotifications(props.items))
</script>

<template>
  <ul class="divide-y divide-[var(--app-line)]/60 overflow-hidden rounded-2xl border border-[var(--app-line)]/60 bg-[var(--app-surface)]">
    <li v-for="n in sorted" :key="n.id" :data-id="n.id" class="flex min-h-14 items-center gap-3 px-4 py-3">
      <div class="min-w-0 flex-1">
        <p class="flex flex-wrap items-baseline gap-x-2">
          <span data-kind class="font-medium">{{ kindLabel(n.kind) }}</span>
          <span data-status class="text-[15px] text-[var(--app-muted)]">
            {{ describeStatusBase(n) }}<template v-if="describeFailures(n)">
              ·
              <span data-failed class="text-[var(--app-danger)]">{{ describeFailures(n) }}</span>
            </template>
          </span>
        </p>
        <p v-if="n.message" data-note class="mt-0.5 truncate text-sm text-[var(--app-muted)]">“{{ n.message }}”</p>
      </div>
      <OverflowMenu
        v-if="n.status === 'SCHEDULED'"
        :label="`Actions for ${kindLabel(n.kind).toLowerCase()}`"
        :items="[{ id: 'unschedule', label: 'Unschedule' }]"
        @select="emit('unschedule', n.id)"
      />
    </li>
  </ul>
</template>
