import { describe, expect, it } from 'vitest'

import { applyLocale } from '@/i18n'
import { ApiError, networkError } from '../errors'

describe('networkError', () => {
  it('is an ApiError with status 0 and the English message', () => {
    const error = networkError()

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(0)
    expect(error.problem.title).toBe('Network error')
    expect(error.message).toBe('Could not reach the server.')
  })

  it('speaks the active language', () => {
    applyLocale('ja')

    expect(networkError().message).toBe('サーバーに接続できませんでした。')
  })
})
