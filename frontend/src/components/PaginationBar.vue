<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import BaseButton from './BaseButton.vue'

const { t } = useI18n()
const props = defineProps<{ page: number; size: number; total: number }>()
const emit = defineEmits<{ 'update:page': [value: number] }>()

const from = computed(() => props.page * props.size + 1)
const to = computed(() => Math.min((props.page + 1) * props.size, props.total))
const hasNext = computed(() => to.value < props.total)
</script>

<template>
  <nav v-if="total > size" :aria-label="t('common.pagination.label')" class="flex items-center justify-between text-sm text-[var(--app-muted)]">
    <span class="tabular-nums">{{ t('common.pagination.range', { from, to, total }) }}</span>
    <div class="flex gap-1">
      <BaseButton variant="plain" :disabled="page === 0" @click="emit('update:page', page - 1)">{{ t('common.pagination.previous') }}</BaseButton>
      <BaseButton variant="plain" :disabled="!hasNext" @click="emit('update:page', page + 1)">{{ t('common.pagination.next') }}</BaseButton>
    </div>
  </nav>
</template>
