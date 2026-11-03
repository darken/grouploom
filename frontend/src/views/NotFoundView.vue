<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'

import BaseButton from '@/components/BaseButton.vue'
import EmptyState from '@/components/EmptyState.vue'

const { t } = useI18n()
</script>

<template>
  <section>
    <h1 class="sr-only">{{ t('page.title.notFound') }}</h1>
    <EmptyState :title="t('page.title.notFound')" :description="t('page.notFound.description')">
      <RouterLink to="/"><BaseButton variant="secondary">{{ t('page.notFound.back') }}</BaseButton></RouterLink>
    </EmptyState>
  </section>
</template>
