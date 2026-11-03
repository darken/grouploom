import { config } from '@vue/test-utils'
import { beforeEach } from 'vitest'

import { applyLocale, i18n } from '@/i18n'

config.global.plugins = [i18n]

beforeEach(() => applyLocale('en'))

// A key used in code but absent from en.json fails the test instead of rendering the key path.
i18n.global.setMissingHandler((locale, key) => {
  throw new Error(`Missing i18n key "${key}" for locale "${locale}"`)
})
