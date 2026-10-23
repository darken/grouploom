import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'

import SaveStatus from '../SaveStatus.vue'

describe('SaveStatus', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('keeps an empty polite live region when idle', () => {
    const wrapper = mount(SaveStatus, { props: { status: 'idle' } })

    expect(wrapper.attributes('aria-live')).toBe('polite')
    expect(wrapper.text()).toBe('')
  })

  it('says Saving… while saving', () => {
    expect(mount(SaveStatus, { props: { status: 'saving' } }).text()).toBe('Saving…')
  })

  it('says Saved, then fades after two seconds', async () => {
    const wrapper = mount(SaveStatus, { props: { status: 'saving' } })

    await wrapper.setProps({ status: 'saved' })
    expect(wrapper.text()).toBe('Saved')

    await vi.advanceTimersByTimeAsync(1900)
    expect(wrapper.text()).toBe('Saved')
    await vi.advanceTimersByTimeAsync(200)
    expect(wrapper.text()).toBe('')
  })

  it('starts the fade again after a later save', async () => {
    const wrapper = mount(SaveStatus, { props: { status: 'saved' } })
    await vi.advanceTimersByTimeAsync(2100)
    expect(wrapper.text()).toBe('')

    await wrapper.setProps({ status: 'saving' })
    await wrapper.setProps({ status: 'saved' })

    expect(wrapper.text()).toBe('Saved')
  })

  it('offers a retry when saving failed', async () => {
    const wrapper = mount(SaveStatus, { props: { status: 'error' } })

    expect(wrapper.text()).toBe("Couldn't save. Try again.")
    await wrapper.get('button').trigger('click')

    expect(wrapper.emitted('retry')).toHaveLength(1)
  })
})
