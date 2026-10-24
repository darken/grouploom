<script setup lang="ts">
defineProps<{ kind: 'invalid' | 'expired' }>()
</script>

<template>
  <p v-if="kind === 'invalid'" class="mt-4 text-[17px]">
    This link isn't valid. Use the latest link from your email, or ask your organizer for a new one.
  </p>
  <p v-else class="mt-4 text-[17px]">This link has expired. Ask your organizer for a new one.</p>
</template>
