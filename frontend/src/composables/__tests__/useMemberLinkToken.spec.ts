import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useMemberLinkToken } from '../useMemberLinkToken'

describe('useMemberLinkToken', () => {
  beforeEach(() => {
    sessionStorage.clear()
    window.history.replaceState(null, '', '/m/attend')
  })

  it('reads the token and the other fragment parameters', () => {
    const link = useMemberLinkToken('k')

    link.open('#t=abc-_9&r=going')

    expect(link.token.value).toBe('abc-_9')
    expect(link.params.value).toEqual({ r: 'going' })
  })

  it('keeps the token under its own storage key, so other link types are not replaced', () => {
    sessionStorage.setItem('prefs', 'prefs-token')
    const attendance = useMemberLinkToken('attend')

    attendance.open('#t=attend-token')

    expect(sessionStorage.getItem('attend')).toBe('attend-token')
    expect(sessionStorage.getItem('prefs')).toBe('prefs-token')
  })

  it('removes the fragment from the address bar and keeps path, query and history state', () => {
    window.history.replaceState({ back: '/x' }, '', '/m/attend?x=1#t=abc&r=going')
    const link = useMemberLinkToken('k')

    link.open(window.location.hash)

    expect(window.location.hash).toBe('')
    expect(window.location.pathname + window.location.search).toBe('/m/attend?x=1')
    expect(window.history.state).toEqual({ back: '/x' })
  })

  it('falls back to the token saved for this tab when the fragment is gone', () => {
    sessionStorage.setItem('k', 'saved')
    const link = useMemberLinkToken('k')

    link.open('')

    expect(link.token.value).toBe('saved')
    expect(link.params.value).toEqual({})
  })

  it('prefers a new fragment token over the saved one and saves it', () => {
    sessionStorage.setItem('k', 'old')
    const link = useMemberLinkToken('k')

    link.open('#t=new')

    expect(link.token.value).toBe('new')
    expect(sessionStorage.getItem('k')).toBe('new')
  })

  it('has no token when there is neither a fragment nor a saved one', () => {
    const link = useMemberLinkToken('k')

    link.open('')

    expect(link.token.value).toBeNull()
  })

  it('still works when sessionStorage is unavailable', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    const link = useMemberLinkToken('k')

    link.open('#t=abc')
    expect(link.token.value).toBe('abc')

    link.open('')
    expect(link.token.value).toBeNull()
    vi.restoreAllMocks()
  })
})
