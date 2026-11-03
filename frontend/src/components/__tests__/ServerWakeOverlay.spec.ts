import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick, ref } from 'vue'

import { applyLocale } from '@/i18n'
import type { ServerStatus } from '@/api/wake-middleware'

const status = ref<ServerStatus>('awake')
const retryWake = vi.fn<() => void>()

vi.mock('@/api/wake-middleware', () => ({ serverStatus: status, retryWake }))

const { default: ServerWakeOverlay } = await import('../ServerWakeOverlay.vue')

describe('ServerWakeOverlay', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    status.value = 'awake'
    retryWake.mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('shows nothing while the server is awake', () => {
    const wrapper = mount(ServerWakeOverlay)

    expect(wrapper.text()).toBe('')
  })

  it('explains the wait and counts seconds while the server wakes up', async () => {
    const wrapper = mount(ServerWakeOverlay)
    status.value = 'waking'
    await nextTick()

    expect(wrapper.text()).toContain('Waking up the server')
    expect(wrapper.text()).toContain('It usually takes around two minutes')
    expect(wrapper.text()).not.toContain('スリープ')

    await vi.advanceTimersByTimeAsync(12_000)
    expect(wrapper.text()).toContain('12 s')
  })

  it('shows the Japanese text under the ja locale', async () => {
    applyLocale('ja')
    const wrapper = mount(ServerWakeOverlay)
    status.value = 'waking'
    await nextTick()

    expect(wrapper.text()).toContain('通常2分ほどかかります')
    expect(wrapper.text()).not.toContain('Waking up the server')
  })

  it('offers to try again when the server does not respond', async () => {
    const wrapper = mount(ServerWakeOverlay)
    status.value = 'unreachable'
    await nextTick()

    expect(wrapper.text()).toContain('isn’t responding')
    await wrapper.get('button').trigger('click')

    expect(retryWake).toHaveBeenCalledOnce()
  })

  it('hides again once the server is awake', async () => {
    const wrapper = mount(ServerWakeOverlay)
    status.value = 'waking'
    await nextTick()
    status.value = 'awake'
    await nextTick()

    expect(wrapper.text()).toBe('')
  })
})
