<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { I18nT, useI18n } from 'vue-i18n'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import BaseButton from '@/components/BaseButton.vue'
import ErrorBanner from '@/components/ErrorBanner.vue'
import PasswordField from '@/components/PasswordField.vue'
import TextField from '@/components/TextField.vue'
import { fieldErrorsFrom, isFormLevel } from '@/composables/useFieldErrors'
import { safeRedirect } from '@/router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const form = reactive({ email: '', password: '' })
const touched = ref<Record<string, boolean>>({})

const clientErrors = computed(() => ({
  email: !form.email.trim() ? t('auth.login.enterEmail') : '',
  password: !form.password ? t('auth.login.enterPassword') : '',
}))
const serverErrors = computed(() => fieldErrorsFrom(auth.error))

function errorFor(field: 'email' | 'password') {
  return (touched.value[field] ? clientErrors.value[field] : '') || serverErrors.value[field] || ''
}

async function submit() {
  touched.value = { email: true, password: true }
  if (clientErrors.value.email || clientErrors.value.password) return
  if (await auth.login({ email: form.email, password: form.password })) {
    await router.push(safeRedirect(route.query.redirect))
  }
}
</script>

<template>
  <h1 class="mb-6 text-center text-3xl font-semibold tracking-tight">{{ t('auth.login.title') }}</h1>
  <form class="space-y-4" novalidate @submit.prevent="submit">
    <TextField
      v-model="form.email"
      :label="t('auth.email')"
      name="email"
      type="email"
      autocomplete="email"
      :error="errorFor('email')"
      :disabled="auth.loading"
      @blur="touched.email = true"
    />
    <PasswordField
      v-model="form.password"
      :label="t('auth.password')"
      name="password"
      autocomplete="current-password"
      :error="errorFor('password')"
      :disabled="auth.loading"
      @blur="touched.password = true"
    />
    <ErrorBanner v-if="auth.error && isFormLevel(auth.error)" :error="auth.error" />
    <BaseButton type="submit" block :loading="auth.loading">{{ t('auth.login.submit') }}</BaseButton>
  </form>
  <I18nT keypath="auth.login.newHere" tag="p" class="mt-6 text-center text-[15px] text-[var(--app-muted)]">
    <template #link>
      <RouterLink to="/signup" class="text-[var(--app-accent)] hover:underline">{{ t('auth.login.createGroup') }}</RouterLink>
    </template>
  </I18nT>
</template>
