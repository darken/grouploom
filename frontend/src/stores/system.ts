import { ref } from 'vue'
import { defineStore } from 'pinia'

import { api } from '@/api/client'
import { ApiError, unwrap } from '@/api/errors'
import type { components } from '@/api/schema'

export type SystemInfo = components['schemas']['SystemInfoResponse']

export const useSystemStore = defineStore('system', () => {
  const info = ref<SystemInfo | null>(null)
  const loading = ref(false)
  const error = ref<ApiError | null>(null)

  async function load() {
    loading.value = true
    error.value = null
    try {
      info.value = unwrap(await api.GET('/system/info'))
    } catch (e) {
      error.value = e instanceof ApiError ? e : new ApiError({ title: 'Network error', detail: 'Could not reach the server.' }, 0)
    } finally {
      loading.value = false
    }
  }

  return { info, loading, error, load }
})
