import type { Middleware } from 'openapi-fetch'

interface AuthHooks {
  /** Current access token, or null when signed out. */
  getToken: () => string | null
  /** Obtains a new access token (and stores it); null when the session can't be refreshed. */
  refresh: () => Promise<string | null>
  onSessionExpired: () => void
}

let hooks: AuthHooks = {
  getToken: () => null,
  refresh: () => Promise.resolve(null),
  onSessionExpired: () => {},
}

// `src/api` never imports a store: the auth store injects these at startup.
export function configureAuth(next: AuthHooks) {
  hooks = next
}

let inFlightRefresh: Promise<string | null> | null = null

function refreshOnce(): Promise<string | null> {
  inFlightRefresh ??= hooks.refresh().finally(() => {
    inFlightRefresh = null
  })
  return inFlightRefresh
}

const isAuthPath = (schemaPath: string) => schemaPath.startsWith('/auth/')

// Request bodies are single-use, so keep a clone of each request to be able to retry it once.
const pending = new Map<string, Request>()

export const authMiddleware: Middleware = {
  onRequest({ request, schemaPath, id }) {
    if (isAuthPath(schemaPath)) return undefined
    pending.set(id, request.clone())
    const token = hooks.getToken()
    if (token) request.headers.set('Authorization', `Bearer ${token}`)
    return request
  },

  async onResponse({ response, schemaPath, id, options }) {
    const original = pending.get(id)
    pending.delete(id)
    if (response.status !== 401 || isAuthPath(schemaPath) || !original) return undefined

    const token = await refreshOnce()
    if (!token) {
      hooks.onSessionExpired()
      return undefined
    }
    original.headers.set('Authorization', `Bearer ${token}`)
    const retry = await (options.fetch ?? globalThis.fetch)(original)
    if (retry.status === 401) hooks.onSessionExpired()
    return retry
  },

  onError({ id }) {
    pending.delete(id)
  },
}
