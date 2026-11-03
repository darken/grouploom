<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
defineProps<{ kind: 'invalid' | 'expired' }>()
</script>

<template>
  <p v-if="kind === 'invalid'" class="mt-4 text-[17px]">
    {{ t('memberPages.link.invalid') }}
  </p>
  <p v-else class="mt-4 text-[17px]">{{ t('memberPages.link.expired') }}</p>
</template>
