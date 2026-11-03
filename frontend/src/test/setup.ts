import { config } from '@vue/test-utils'
import { beforeEach } from 'vitest'

import { applyLocale, i18n } from '@/i18n'

config.global.plugins = [i18n]

beforeEach(() => applyLocale('en'))
