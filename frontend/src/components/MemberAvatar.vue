<script setup lang="ts">
import { computed } from 'vue'

import { initials } from '@/ui/initials'

const props = defineProps<{ id: string; name: string }>()

// Full class names so Tailwind can see them.
const TINTS = [
  'bg-slate-200 text-slate-700 dark:bg-slate-700 dark:text-slate-100',
  'bg-stone-200 text-stone-700 dark:bg-stone-700 dark:text-stone-100',
  'bg-zinc-200 text-zinc-700 dark:bg-zinc-700 dark:text-zinc-100',
  'bg-gray-200 text-gray-700 dark:bg-gray-700 dark:text-gray-100',
  'bg-neutral-200 text-neutral-700 dark:bg-neutral-700 dark:text-neutral-100',
  'bg-sky-100 text-sky-800 dark:bg-sky-900 dark:text-sky-100',
]

/** Same id, same tint: a stable string hash picks the entry. */
const tint = computed(() => {
  let hash = 0
  for (const char of props.id) hash = (hash * 31 + char.charCodeAt(0)) >>> 0
  return TINTS[hash % TINTS.length]
})
</script>

<template>
  <span
    data-avatar
    class="grid size-10 shrink-0 place-items-center rounded-full text-[15px] font-medium select-none"
    :class="tint"
    aria-hidden="true"
  >
    {{ initials(name) }}
  </span>
</template>
