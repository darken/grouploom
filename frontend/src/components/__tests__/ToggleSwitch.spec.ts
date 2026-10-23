import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import ToggleSwitch from '../ToggleSwitch.vue'

describe('ToggleSwitch', () => {
  it('is a switch button that reflects its state in aria-checked', async () => {
    const wrapper = mount(ToggleSwitch, { props: { modelValue: false } })
    const button = wrapper.get('button')

    expect(button.attributes('role')).toBe('switch')
    expect(button.attributes('type')).toBe('button')
    expect(button.attributes('aria-checked')).toBe('false')

    await wrapper.setProps({ modelValue: true })
    expect(button.attributes('aria-checked')).toBe('true')
  })

  it('names the switch with the label slot', () => {
    const wrapper = mount(ToggleSwitch, { props: { modelValue: false }, slots: { default: 'Practice' } })

    expect(wrapper.get('button').text()).toBe('Practice')
  })

  it('lets the caller name it with aria-labelledby instead', () => {
    const wrapper = mount(ToggleSwitch, { props: { modelValue: false }, attrs: { 'aria-labelledby': 'x' } })

    expect(wrapper.get('button').attributes('aria-labelledby')).toBe('x')
  })

  it('emits the opposite value when clicked', async () => {
    const wrapper = mount(ToggleSwitch, { props: { modelValue: false } })
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('update:modelValue')).toEqual([[true]])

    await wrapper.setProps({ modelValue: true })
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('update:modelValue')?.[1]).toEqual([false])
  })

  it.each(['space', 'enter'] as const)('toggles once with the %s key', async (key) => {
    const wrapper = mount(ToggleSwitch, { props: { modelValue: false } })

    await wrapper.get('button').trigger(`keydown.${key}`)
    // The browser's own click on keyup (Space) is suppressed, so it can't toggle a second time.
    await wrapper.get('button').trigger('keyup.space')

    expect(wrapper.emitted('update:modelValue')).toEqual([[true]])
  })

  it('does not toggle when disabled', async () => {
    const wrapper = mount(ToggleSwitch, { props: { modelValue: false, disabled: true } })
    const button = wrapper.get('button')

    expect(button.attributes('disabled')).toBeDefined()
    await button.trigger('click')
    await button.trigger('keydown.space')
    await button.trigger('keydown.enter')

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('slides the thumb with a short transition that reduced motion switches off', () => {
    const wrapper = mount(ToggleSwitch, { props: { modelValue: true } })
    const thumb = wrapper.get('[data-thumb]')

    expect(thumb.classes()).toContain('duration-150')
    expect(thumb.classes()).toContain('motion-reduce:transition-none')
  })
})
