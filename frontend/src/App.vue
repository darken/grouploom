<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'

import AppShell from '@/components/AppShell.vue'
import AuthLayout from '@/components/AuthLayout.vue'
import ToastHost from '@/components/ToastHost.vue'
import { loginLocation } from '@/router'
import { useAuthStore } from '@/stores/auth'
import { useSystemStore } from '@/stores/system'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const system = useSystemStore()

onMounted(() => system.load())

// Signing out, or a session that can't be refreshed, sends the user to the sign-in page.
watch(
  () => auth.status,
  (status) => {
    if (status === 'anonymous' && route.meta.requiresAuth) router.replace(loginLocation(route.fullPath))
  },
)
</script>

<template>
  <component :is="route.meta.layout === 'auth' ? AuthLayout : AppShell">
    <RouterView v-slot="{ Component }">
      <Transition name="page" mode="out-in">
        <component :is="Component" />
      </Transition>
    </RouterView>
  </component>
  <ToastHost />
</template>
