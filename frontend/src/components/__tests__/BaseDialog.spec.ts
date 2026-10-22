import { beforeAll, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import BaseDialog from '../BaseDialog.vue'
import { installDialogPolyfill } from './dialogPolyfill'

beforeAll(installDialogPolyfill)

describe('BaseDialog', () => {
  it('is closed until open is true, then shows the title and content', async () => {
    const wrapper = mount(BaseDialog, { props: { title: 'New event type', open: false }, slots: { default: '<p>Body</p>' } })
    expect(wrapper.find('dialog').attributes('open')).toBeUndefined()
    expect(wrapper.text()).not.toContain('Body')

    await wrapper.setProps({ open: true })

    expect(wrapper.find('dialog').attributes('open')).toBeDefined()
    expect(wrapper.find('h2').text()).toBe('New event type')
    expect(wrapper.text()).toContain('Body')
    expect(wrapper.find('dialog').attributes('aria-labelledby')).toBe(wrapper.find('h2').attributes('id'))
  })

  it('emits close instead of closing itself when Escape cancels the dialog', async () => {
    const wrapper = mount(BaseDialog, { props: { title: 'T', open: true } })
    const cancel = new Event('cancel', { cancelable: true })

    wrapper.find('dialog').element.dispatchEvent(cancel)

    expect(cancel.defaultPrevented).toBe(true)
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('emits close when the backdrop is clicked but not when the content is', async () => {
    const wrapper = mount(BaseDialog, { props: { title: 'T', open: true }, slots: { default: '<button>Inside</button>' } })

    await wrapper.find('button').trigger('click')
    expect(wrapper.emitted('close')).toBeUndefined()

    await wrapper.find('dialog').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('returns focus to the previously focused element when closed', async () => {
    const trigger = document.createElement('button')
    document.body.append(trigger)
    trigger.focus()
    const wrapper = mount(BaseDialog, { props: { title: 'T', open: false }, attachTo: document.body })

    await wrapper.setProps({ open: true })
    ;(document.activeElement as HTMLElement | null)?.blur()
    await wrapper.setProps({ open: false })

    expect(document.activeElement).toBe(trigger)
    wrapper.unmount()
    trigger.remove()
  })
})
