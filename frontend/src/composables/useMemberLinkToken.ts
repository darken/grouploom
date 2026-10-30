import { ref } from 'vue'

function readSaved(storageKey: string): string | null {
  try {
    return sessionStorage.getItem(storageKey)
  } catch {
    return null
  }
}

function writeSaved(storageKey: string, token: string) {
  try {
    sessionStorage.setItem(storageKey, token)
  } catch {
    // Private mode or blocked storage: the page works, it just won't survive a reload.
  }
}

/**
 * Takes a member link's token from the URL fragment (`#t=…`), keeps it for this tab under `storageKey` and removes
 * the fragment from the address bar. Each kind of link uses its own key, so opening one never replaces another's.
 * The other fragment values (such as `r`) come back in `params`.
 */
export function useMemberLinkToken(storageKey: string) {
  const token = ref<string | null>(null)
  const params = ref<Record<string, string>>({})

  function open(hash: string) {
    const fragment = new URLSearchParams(hash.replace(/^#/, ''))
    const fromFragment = fragment.get('t')
    fragment.delete('t')
    params.value = Object.fromEntries(fragment)
    if (fromFragment) {
      writeSaved(storageKey, fromFragment)
      // Keep the router's history state; only the fragment goes.
      window.history.replaceState(window.history.state, '', window.location.pathname + window.location.search)
    }
    token.value = fromFragment || readSaved(storageKey) || null
  }

  return { token, params, open }
}
