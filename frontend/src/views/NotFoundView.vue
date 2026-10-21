<script setup lang="ts">
import { RouterLink } from 'vue-router'

import BaseButton from '@/components/BaseButton.vue'
import EmptyState from '@/components/EmptyState.vue'
</script>

<template>
  <section>
    <h1 class="sr-only">Page not found</h1>
    <EmptyState title="Page not found" description="The page you’re looking for doesn’t exist.">
      <RouterLink to="/"><BaseButton variant="secondary">Back to home</BaseButton></RouterLink>
    </EmptyState>
  </section>
</template>
