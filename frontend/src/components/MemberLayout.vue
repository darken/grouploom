<script setup lang="ts">
import { computed } from 'vue'

import { useMemberPreferencesStore } from '@/stores/memberPreferences'

// Members arrive from an email: no app header, navigation or sign-up hints, just the group they know.
const groupName = computed(() => useMemberPreferencesStore().data?.groupName)
</script>

<template>
  <div class="flex min-h-dvh flex-col items-center px-4 pt-10 pb-8 sm:pt-16">
    <p v-if="groupName" data-wordmark class="mb-8 max-w-lg truncate text-[15px] font-semibold tracking-tight text-[var(--app-muted)]">
      {{ groupName }}
    </p>
    <main class="w-full max-w-lg flex-1">
      <slot />
    </main>
    <footer class="mt-12 text-center text-[13px] text-[var(--app-muted)]">
      <template v-if="groupName">Sent by {{ groupName }} via GroupLoom</template>
      <template v-else>GroupLoom</template>
    </footer>
  </div>
</template>
