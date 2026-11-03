import { createI18n } from 'vue-i18n'

import en from './locales/en.json'
import ja from './locales/ja.json'

export const SUPPORTED_LOCALES = ['en', 'ja'] as const
export type Locale = (typeof SUPPORTED_LOCALES)[number]

export type MessageSchema = typeof en

const isSupported = (tag: string): tag is Locale => (SUPPORTED_LOCALES as readonly string[]).includes(tag)

/** The first browser language whose base tag is supported (`ja-JP` becomes `ja`), otherwise `en`. */
export function detectLocale(languages: readonly string[]): Locale {
  for (const language of languages) {
    const base = language.split('-')[0]?.toLowerCase() ?? ''
    if (isSupported(base)) return base
  }
  return 'en'
}

export const i18n = createI18n<[MessageSchema], Locale, false>({
  legacy: false,
  locale: detectLocale(navigator.languages),
  fallbackLocale: 'en',
  messages: { en, ja },
})

export function applyLocale(locale: Locale): void {
  i18n.global.locale.value = locale
  document.documentElement.lang = locale
}

window.addEventListener('languagechange', () => applyLocale(detectLocale(navigator.languages)))
