import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import ColorSwatchPicker from '../ColorSwatchPicker.vue'

describe('ColorSwatchPicker', () => {
  it('renders a radio group with an accessible name per color and marks the selected one', () => {
    const wrapper = mount(ColorSwatchPicker, { props: { modelValue: 'green' } })

    expect(wrapper.find('[role=radiogroup]').attributes('aria-label')).toBe('Color')
    const radios = wrapper.findAll('[role=radio]')
    expect(radios.map((r) => r.attributes('aria-label'))).toEqual([
      'Blue', 'Green', 'Orange', 'Red', 'Purple', 'Pink', 'Teal', 'Gray',
    ])
    expect(radios.filter((r) => r.attributes('aria-checked') === 'true').map((r) => r.attributes('aria-label'))).toEqual(['Green'])
  })

  it('selects a color on click', async () => {
    const wrapper = mount(ColorSwatchPicker, { props: { modelValue: 'blue' } })

    await wrapper.find('[aria-label=Red]').trigger('click')

    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['red'])
  })

  it('moves the selection with arrow keys and wraps around', async () => {
    const wrapper = mount(ColorSwatchPicker, { props: { modelValue: 'blue' }, attachTo: document.body })

    await wrapper.find('[aria-label=Blue]').trigger('keydown', { key: 'ArrowRight' })
    await wrapper.find('[aria-label=Blue]').trigger('keydown', { key: 'ArrowLeft' })
    await wrapper.find('[aria-label=Gray]').trigger('keydown', { key: 'ArrowDown' })

    expect(wrapper.emitted('update:modelValue')).toEqual([['green'], ['gray'], ['blue']])
    wrapper.unmount()
  })

  it('only the selected swatch is in the tab order', () => {
    const wrapper = mount(ColorSwatchPicker, { props: { modelValue: 'pink' } })

    const tabbable = wrapper.findAll('[role=radio]').filter((r) => r.attributes('tabindex') === '0')
    expect(tabbable.map((r) => r.attributes('aria-label'))).toEqual(['Pink'])
  })
})
