import { describe, expect, it, vi } from 'vitest'

import { applyLocale, i18n } from '../index'

describe('applyLocale', () => {
  it('sets the active language and <html lang>', () => {
    applyLocale('ja')

    expect(i18n.global.locale.value).toBe('ja')
    expect(document.documentElement.lang).toBe('ja')
  })

  it('follows a languagechange event', () => {
    vi.spyOn(navigator, 'languages', 'get').mockReturnValue(['ja-JP', 'en'])
    window.dispatchEvent(new Event('languagechange'))

    expect(i18n.global.locale.value).toBe('ja')
    expect(document.documentElement.lang).toBe('ja')

    vi.spyOn(navigator, 'languages', 'get').mockReturnValue(['fr'])
    window.dispatchEvent(new Event('languagechange'))

    expect(i18n.global.locale.value).toBe('en')
    expect(document.documentElement.lang).toBe('en')
  })
})
