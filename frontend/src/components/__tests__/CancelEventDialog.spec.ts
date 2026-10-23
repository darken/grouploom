import { beforeAll, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import CancelEventDialog from '../CancelEventDialog.vue'
import { installDialogPolyfill } from './dialogPolyfill'

beforeAll(installDialogPolyfill)

const props = { open: true, title: 'Rehearsal', notified: false }
const button = (w: ReturnType<typeof mount>, label: string) => w.findAll('button').find((b) => b.text() === label)!

describe('CancelEventDialog', () => {
  it('asks to cancel the event by title', () => {
    const wrapper = mount(CancelEventDialog, { props })

    expect(wrapper.find('h2').text()).toBe("Cancel 'Rehearsal'?")
  })

  it('says members will get a cancellation email when something was sent', () => {
    const wrapper = mount(CancelEventDialog, { props: { ...props, notified: true } })

    expect(wrapper.text()).toContain('Members who were notified will get a cancellation email.')
    expect(wrapper.text()).not.toContain('No one has been notified yet.')
  })

  it('says no one has been notified yet otherwise', () => {
    const wrapper = mount(CancelEventDialog, { props })

    expect(wrapper.text()).toContain('No one has been notified yet.')
  })

  it('submits the message with the red "Cancel event" button', async () => {
    const wrapper = mount(CancelEventDialog, { props: { ...props, notified: true } })

    await wrapper.find('textarea').setValue('The gym is closed')
    await button(wrapper, 'Cancel event').trigger('click')

    expect(button(wrapper, 'Cancel event').classes().join(' ')).toContain('danger')
    expect(wrapper.emitted('submit')).toEqual([['The gym is closed']])
  })

  it('submits an empty message when none was typed', async () => {
    const wrapper = mount(CancelEventDialog, { props })

    await button(wrapper, 'Cancel event').trigger('click')

    expect(wrapper.emitted('submit')).toEqual([['']])
  })

  it('closes with "Keep event" and with Escape without submitting', async () => {
    const wrapper = mount(CancelEventDialog, { props })

    await button(wrapper, 'Keep event').trigger('click')
    wrapper.find('dialog').element.dispatchEvent(new Event('cancel', { cancelable: true }))

    expect(wrapper.emitted('close')).toHaveLength(2)
    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('limits the message to 1000 characters', () => {
    const wrapper = mount(CancelEventDialog, { props })

    expect(wrapper.find('label').text()).toBe('Add a message')
    expect(wrapper.find('textarea').attributes('maxlength')).toBe('1000')
  })

  it('shows the server error for the message and disables the button while saving', () => {
    const wrapper = mount(CancelEventDialog, { props: { ...props, saving: true, error: 'Too long' } })

    expect(wrapper.text()).toContain('Too long')
    expect(button(wrapper, 'Cancel event').attributes('disabled')).toBeDefined()
  })
})
