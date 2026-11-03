<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { I18nT, useI18n } from 'vue-i18n'
import { RouterLink, useRouter } from 'vue-router'

import BaseButton from '@/components/BaseButton.vue'
import ErrorBanner from '@/components/ErrorBanner.vue'
import PasswordField from '@/components/PasswordField.vue'
import TextField from '@/components/TextField.vue'
import { fieldErrorsFrom, isFormLevel } from '@/composables/useFieldErrors'
import { i18n } from '@/i18n'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const { t } = useI18n()
const router = useRouter()

type Field = 'groupName' | 'displayName' | 'email' | 'password'

const form = reactive({ groupName: '', displayName: '', email: '', password: '' })
const touched = ref<Partial<Record<Field, boolean>>>({})

const clientErrors = computed<Record<Field, string>>(() => ({
  groupName: !form.groupName.trim() ? t('auth.signup.enterGroupName') : '',
  displayName: !form.displayName.trim() ? t('auth.signup.enterName') : '',
  email: !form.email.trim() ? t('auth.login.enterEmail') : '',
  password: form.password.length < 12 ? t('auth.signup.passwordTooShort') : '',
}))

const serverErrors = computed(() => {
  const errors = fieldErrorsFrom(auth.error)
  // A duplicate email is reported as 409, which belongs under the email field.
  if (auth.error?.status === 409) errors.email = t('auth.signup.emailTaken')
  return errors
})

function errorFor(field: Field) {
  return (touched.value[field] ? clientErrors.value[field] : '') || serverErrors.value[field] || ''
}

async function submit() {
  touched.value = { groupName: true, displayName: true, email: true, password: true }
  if (Object.values(clientErrors.value).some(Boolean)) return
  const ok = await auth.signUp({
    groupName: form.groupName,
    displayName: form.displayName,
    email: form.email,
    password: form.password,
    // A group created from a Japanese browser starts in Japanese.
    language: i18n.global.locale.value,
  })
  if (ok) await router.push('/')
}

const showBanner = computed(
  () => auth.error !== null && isFormLevel(auth.error) && auth.error.status !== 409,
)
</script>

<template>
  <h1 class="mb-6 text-center text-3xl font-semibold tracking-tight">{{ t('auth.signup.title') }}</h1>
  <form class="space-y-4" novalidate @submit.prevent="submit">
    <TextField
      v-model="form.groupName"
      :label="t('auth.signup.groupName')"
      name="groupName"
      autocomplete="organization"
      :error="errorFor('groupName')"
      :disabled="auth.loading"
      @blur="touched.groupName = true"
    />
    <TextField
      v-model="form.displayName"
      :label="t('auth.signup.yourName')"
      name="displayName"
      autocomplete="name"
      :error="errorFor('displayName')"
      :disabled="auth.loading"
      @blur="touched.displayName = true"
    />
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
      autocomplete="new-password"
      :hint="t('auth.signup.passwordHint')"
      :error="errorFor('password')"
      :disabled="auth.loading"
      @blur="touched.password = true"
    />
    <ErrorBanner v-if="showBanner && auth.error" :error="auth.error" />
    <BaseButton type="submit" block :loading="auth.loading">{{ t('auth.signup.submit') }}</BaseButton>
  </form>
  <I18nT keypath="auth.signup.haveAccount" tag="p" class="mt-6 text-center text-[15px] text-[var(--app-muted)]">
    <template #link>
      <RouterLink to="/login" class="text-[var(--app-accent)] hover:underline">{{ t('auth.signup.signIn') }}</RouterLink>
    </template>
  </I18nT>
</template>
