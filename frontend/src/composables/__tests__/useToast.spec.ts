import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'

import ToastHost from '@/components/ToastHost.vue'
import { useToast } from '../useToast'

describe('useToast + ToastHost', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })
  afterEach(() => {
    useToast().dismiss()
    vi.useRealTimers()
  })

  it('shows the message in a polite live region and dismisses it after 4 seconds', async () => {
    const wrapper = mount(ToastHost)
    expect(wrapper.find('[aria-live=polite]').exists()).toBe(true)

    useToast().show('Event type created')
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[aria-live=polite]').text()).toContain('Event type created')

    vi.advanceTimersByTime(4000)
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[aria-live=polite]').text()).toBe('')
  })

  it('runs the action (Undo) and dismisses the toast', async () => {
    const wrapper = mount(ToastHost)
    const undo = vi.fn<() => void>()

    useToast().show('Archived', { label: 'Undo', run: undo })
    await wrapper.vm.$nextTick()
    await wrapper.find('button').trigger('click')

    expect(undo).toHaveBeenCalledOnce()
    expect(wrapper.find('[aria-live=polite]').text()).toBe('')
  })

  it('replaces an earlier toast and restarts the timer', async () => {
    const wrapper = mount(ToastHost)

    useToast().show('First')
    vi.advanceTimersByTime(3000)
    useToast().show('Second')
    vi.advanceTimersByTime(3000)
    await wrapper.vm.$nextTick()

    expect(wrapper.find('[aria-live=polite]').text()).toContain('Second')
  })
})
