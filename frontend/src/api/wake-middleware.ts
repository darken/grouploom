import { readonly, ref } from 'vue'
import type { Middleware } from 'openapi-fetch'

export type ServerStatus = 'awake' | 'waking' | 'unreachable'

interface WakeOptions {
  /** Health check, resolved against the request URL. */
  healthPath?: string
  /** Render's free tier sleeps after 15 minutes without traffic, so check first after 10. */
  idleMs?: number
  pollMs?: number
  /** After this long without an answer the status becomes unreachable until `retry()`. */
  giveUpMs?: number
  /** A check that takes longer than this shows as waking, so a healthy server never flashes the overlay. */
  showAfterMs?: number
  attemptTimeoutMs?: number
}

// Render answers for a sleeping or still-starting service with these.
const ASLEEP = new Set([502, 503, 504])

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

/**
 * Waits out a sleeping backend (free hosting): requests after a quiet spell wait until the health check
 * answers, and a GET that still hits a sleeping server is retried once it is up. Other methods are never
 * retried, since the server may have handled them.
 */
export function createWake(options: WakeOptions = {}) {
  const {
    healthPath = '/api/system/info',
    idleMs = 10 * 60_000,
    pollMs = 2000,
    giveUpMs = 3 * 60_000,
    showAfterMs = 1000,
    attemptTimeoutMs = 10_000,
  } = options

  const status = ref<ServerStatus>('awake')
  let lastContact = -Infinity
  let checking: Promise<void> | null = null
  let resume: (() => void) | null = null

  async function ping(fetchFn: typeof fetch, url: string): Promise<boolean> {
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), attemptTimeoutMs)
    try {
      return (await fetchFn(url, { signal: controller.signal, cache: 'no-store' })).ok
    } catch {
      return false
    } finally {
      clearTimeout(timer)
    }
  }

  async function poll(fetchFn: typeof fetch, url: string) {
    let started = Date.now()
    const show = setTimeout(() => {
      if (status.value === 'awake') status.value = 'waking'
    }, showAfterMs)
    try {
      while (!(await ping(fetchFn, url))) {
        status.value = 'waking'
        if (Date.now() - started >= giveUpMs) {
          status.value = 'unreachable'
          await new Promise<void>((r) => (resume = r))
          started = Date.now()
          status.value = 'waking'
          continue
        }
        await sleep(pollMs)
      }
      lastContact = Date.now()
      status.value = 'awake'
    } finally {
      clearTimeout(show)
    }
  }

  // One check at a time, shared by every request waiting on it.
  function awake(fetchFn: typeof fetch, requestUrl: string): Promise<void> {
    checking ??= poll(fetchFn, new URL(healthPath, requestUrl).toString()).finally(() => {
      checking = null
    })
    return checking
  }

  // GETs have no body, but a sent Request can't be fetched again, so keep a clone to retry.
  const pending = new Map<string, Request>()

  async function retryWhenAwake(id: string, fetchFn: typeof fetch, requestUrl: string): Promise<Response | undefined> {
    const original = pending.get(id)
    pending.delete(id)
    lastContact = -Infinity
    const ready = awake(fetchFn, requestUrl)
    if (!original) return undefined
    await ready
    return fetchFn(original)
  }

  const middleware: Middleware = {
    async onRequest({ request, id, options: { fetch: fetchFn = globalThis.fetch } }) {
      if (Date.now() - lastContact > idleMs) await awake(fetchFn, request.url)
      if (request.method === 'GET') pending.set(id, request.clone())
      return undefined
    },

    async onResponse({ request, response, id, options: { fetch: fetchFn = globalThis.fetch } }) {
      if (!ASLEEP.has(response.status)) {
        pending.delete(id)
        lastContact = Date.now()
        return undefined
      }
      return retryWhenAwake(id, fetchFn, request.url)
    },

    async onError({ request, error, id, options: { fetch: fetchFn = globalThis.fetch } }) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        pending.delete(id)
        return undefined
      }
      return retryWhenAwake(id, fetchFn, request.url)
    },
  }

  function retry() {
    resume?.()
    resume = null
  }

  return { middleware, status: readonly(status), retry }
}

export const { middleware: wakeMiddleware, status: serverStatus, retry: retryWake } = createWake()
