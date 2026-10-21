<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import BaseButton from '@/components/BaseButton.vue'
import ErrorBanner from '@/components/ErrorBanner.vue'
import PasswordField from '@/components/PasswordField.vue'
import TextField from '@/components/TextField.vue'
import { fieldErrorsFrom, isFormLevel } from '@/composables/useFieldErrors'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

type Field = 'groupName' | 'displayName' | 'email' | 'password'

const form = reactive({ groupName: '', displayName: '', email: '', password: '' })
const touched = ref<Partial<Record<Field, boolean>>>({})

const clientErrors = computed<Record<Field, string>>(() => ({
  groupName: !form.groupName.trim() ? 'Enter a name for your group.' : '',
  displayName: !form.displayName.trim() ? 'Enter your name.' : '',
  email: !form.email.trim() ? 'Enter your email address.' : '',
  password: form.password.length < 12 ? 'Use at least 12 characters.' : '',
}))

const serverErrors = computed(() => {
  const errors = fieldErrorsFrom(auth.error)
  // A duplicate email is reported as 409, which belongs under the email field.
  if (auth.error?.status === 409) errors.email = 'An account with this email already exists.'
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
  })
  if (ok) await router.push('/')
}

const showBanner = computed(
  () => auth.error !== null && isFormLevel(auth.error) && auth.error.status !== 409,
)
</script>

<template>
  <h1 class="mb-6 text-center text-3xl font-semibold tracking-tight">Create your group</h1>
  <form class="space-y-4" novalidate @submit.prevent="submit">
    <TextField
      v-model="form.groupName"
      label="Group name"
      name="groupName"
      autocomplete="organization"
      :error="errorFor('groupName')"
      :disabled="auth.loading"
      @blur="touched.groupName = true"
    />
    <TextField
      v-model="form.displayName"
      label="Your name"
      name="displayName"
      autocomplete="name"
      :error="errorFor('displayName')"
      :disabled="auth.loading"
      @blur="touched.displayName = true"
    />
    <TextField
      v-model="form.email"
      label="Email"
      name="email"
      type="email"
      autocomplete="email"
      :error="errorFor('email')"
      :disabled="auth.loading"
      @blur="touched.email = true"
    />
    <PasswordField
      v-model="form.password"
      label="Password"
      name="password"
      autocomplete="new-password"
      hint="At least 12 characters"
      :error="errorFor('password')"
      :disabled="auth.loading"
      @blur="touched.password = true"
    />
    <ErrorBanner v-if="showBanner && auth.error" :error="auth.error" />
    <BaseButton type="submit" block :loading="auth.loading">Create group</BaseButton>
  </form>
  <p class="mt-6 text-center text-[15px] text-[var(--app-muted)]">
    Already have an account?
    <RouterLink to="/login" class="text-[var(--app-accent)] hover:underline">Sign in</RouterLink>
  </p>
</template>
