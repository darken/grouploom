<script setup lang="ts">
import type { AttendanceAnswer, AttendanceRow } from '@/stores/attendance'
import { relativeTime } from '@/ui/relativeTime'

import MemberAvatar from './MemberAvatar.vue'
import OverflowMenu, { type OverflowMenuItem } from './OverflowMenu.vue'

const props = defineProps<{
  items: AttendanceRow[]
  /** A cancelled event's attendance can't be changed. */
  readOnly?: boolean
  /** The current moment; injectable so relative times are testable. */
  now?: Date
}>()

const emit = defineEmits<{ set: [memberId: string, response: AttendanceAnswer]; clear: [memberId: string] }>()

const ANSWERS = {
  GOING: { label: 'Going', class: 'font-medium text-green-700 dark:text-green-400' },
  NOT_GOING: { label: "Can't go", class: 'font-medium text-[var(--app-muted)]' },
} as const

function menuItems(row: AttendanceRow): OverflowMenuItem[] {
  const items: OverflowMenuItem[] = [
    { id: 'GOING', label: 'Mark as going', disabled: row.response === 'GOING' },
    { id: 'NOT_GOING', label: "Mark as can't go", disabled: row.response === 'NOT_GOING' },
  ]
  if (row.response) items.push({ id: 'clear', label: 'Clear answer', destructive: true })
  return items
}

function choose(row: AttendanceRow, id: string) {
  if (id === 'clear') emit('clear', row.member.id)
  else emit('set', row.member.id, id as AttendanceAnswer)
}

const when = (iso: string) => relativeTime(iso, props.now)
</script>

<template>
  <ul class="divide-y divide-[var(--app-line)]/60 rounded-2xl border border-[var(--app-line)]/60 bg-[var(--app-surface)]">
    <li v-for="row in items" :key="row.member.id" class="group flex min-h-16 items-center gap-4 px-5 py-3">
      <MemberAvatar :id="row.member.id" :name="row.member.name" />
      <div class="min-w-0 flex-1">
        <p class="truncate font-medium">{{ row.member.name }}</p>
        <p class="text-sm text-[var(--app-muted)]">
          <span data-answer :class="row.response ? ANSWERS[row.response].class : ''">{{ row.response ? ANSWERS[row.response].label : 'No reply' }}</span>
          <template v-if="row.note">
            <span data-note class="whitespace-pre-line"> “{{ row.note }}”</span>
          </template>
          <template v-if="row.source === 'ORGANIZER'"> · by organizer</template>
          <template v-if="row.respondedAt"> · <time :datetime="row.respondedAt">{{ when(row.respondedAt) }}</time></template>
        </p>
      </div>
      <OverflowMenu
        v-if="!readOnly"
        :label="`Actions for ${row.member.name}`"
        :items="menuItems(row)"
        trigger-class="focus-visible:opacity-100 group-focus-within:opacity-100 group-hover:opacity-100 sm:opacity-0 [@media(hover:none)]:opacity-100"
        @select="choose(row, $event)"
      />
    </li>
  </ul>
</template>
