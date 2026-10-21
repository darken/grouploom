import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import { ApiError } from '@/api/errors'
import ErrorBanner from '../ErrorBanner.vue'

describe('ErrorBanner', () => {
  it('shows the problem title and detail', () => {
    const error = new ApiError({ status: 404, title: 'Not Found', detail: 'Group missing' })
    const wrapper = mount(ErrorBanner, { props: { error } })

    expect(wrapper.attributes('role')).toBe('alert')
    expect(wrapper.text()).toContain('Not Found')
    expect(wrapper.text()).toContain('Group missing')
  })

  it('lists field errors for validation failures', () => {
    const error = new ApiError({
      status: 400,
      title: 'Bad Request',
      errors: [{ field: 'name', message: 'must not be blank' }],
    })
    const wrapper = mount(ErrorBanner, { props: { error } })

    expect(wrapper.text()).toContain('name: must not be blank')
  })
})
