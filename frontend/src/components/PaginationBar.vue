<script setup lang="ts">
import { computed } from 'vue'

import BaseButton from './BaseButton.vue'

const props = defineProps<{ page: number; size: number; total: number }>()
const emit = defineEmits<{ 'update:page': [value: number] }>()

const from = computed(() => props.page * props.size + 1)
const to = computed(() => Math.min((props.page + 1) * props.size, props.total))
const hasNext = computed(() => to.value < props.total)
</script>

<template>
  <nav v-if="total > size" aria-label="Pagination" class="flex items-center justify-between text-sm text-[var(--app-muted)]">
    <span class="tabular-nums">{{ from }}–{{ to }} of {{ total }}</span>
    <div class="flex gap-1">
      <BaseButton variant="plain" :disabled="page === 0" @click="emit('update:page', page - 1)">Previous</BaseButton>
      <BaseButton variant="plain" :disabled="!hasNext" @click="emit('update:page', page + 1)">Next</BaseButton>
    </div>
  </nav>
</template>
