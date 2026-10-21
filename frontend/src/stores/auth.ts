import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { api } from '@/api/client'
import { configureAuth } from '@/api/auth-middleware'
import { ApiError, unwrap } from '@/api/errors'
import type { components } from '@/api/schema'

export type MeResponse = components['schemas']['MeResponse']
type AuthResponse = components['schemas']['AuthResponse']
export type SignUpInput = components['schemas']['SignUpRequest']
export type LoginInput = components['schemas']['LoginRequest']

export const useAuthStore = defineStore('auth', () => {
  const user = ref<MeResponse | null>(null)
  // Held in memory only; the HttpOnly refresh cookie restores the session after a reload.
  const accessToken = ref<string | null>(null)
  const status = ref<'unknown' | 'authenticated' | 'anonymous'>('unknown')
  const loading = ref(false)
  const error = ref<ApiError | null>(null)

  const isAuthenticated = computed(() => status.value === 'authenticated')

  function accept(response: AuthResponse) {
    user.value = response.user
    accessToken.value = response.accessToken
    status.value = 'authenticated'
  }

  function clear() {
    user.value = null
    accessToken.value = null
    status.value = 'anonymous'
  }

  function toApiError(e: unknown): ApiError {
    return e instanceof ApiError
      ? e
      : new ApiError({ title: 'Network error', detail: 'Could not reach the server.' }, 0)
  }

  async function submit(request: () => Promise<AuthResponse>): Promise<boolean> {
    loading.value = true
    error.value = null
    try {
      accept(await request())
      return true
    } catch (e) {
      error.value = toApiError(e)
      return false
    } finally {
      loading.value = false
    }
  }

  const signUp = (input: SignUpInput) =>
    submit(async () => unwrap(await api.POST('/auth/signup', { body: input })))

  const login = (input: LoginInput) =>
    submit(async () => unwrap(await api.POST('/auth/login', { body: input })))

  /** Exchanges the refresh cookie for a new access token. Returns it, or null if there is no session. */
  async function refresh(): Promise<string | null> {
    try {
      const response = unwrap(await api.POST('/auth/refresh'))
      accept(response)
      return response.accessToken
    } catch {
      return null
    }
  }

  let restoring: Promise<void> | null = null

  /** Called once on app start (by the router guard) to find out whether a session exists. */
  function restore(): Promise<void> {
    if (status.value !== 'unknown') return Promise.resolve()
    restoring ??= refresh().then((token) => {
      if (!token) clear()
    })
    return restoring
  }

  async function logout() {
    try {
      await api.POST('/auth/logout')
    } catch {
      // The local session is cleared regardless of whether the server could be reached.
    } finally {
      clear()
    }
  }

  configureAuth({
    getToken: () => accessToken.value,
    refresh,
    onSessionExpired: clear,
  })

  return { user, accessToken, status, loading, error, isAuthenticated, signUp, login, logout, restore, refresh }
})
