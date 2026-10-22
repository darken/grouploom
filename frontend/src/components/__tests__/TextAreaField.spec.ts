import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import TextAreaField from '../TextAreaField.vue'

describe('TextAreaField', () => {
  it('links the label to the textarea and supports v-model', async () => {
    const wrapper = mount(TextAreaField, { props: { label: 'Description', name: 'description', modelValue: '' } })

    const textarea = wrapper.find('textarea')
    expect(wrapper.find('label').attributes('for')).toBe(textarea.attributes('id'))
    await textarea.setValue('Weekly')

    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['Weekly'])
  })

  it('exposes the error with aria-invalid and aria-describedby', () => {
    const wrapper = mount(TextAreaField, { props: { label: 'Description', name: 'description', error: 'Too long' } })

    const textarea = wrapper.find('textarea')
    expect(textarea.attributes('aria-invalid')).toBe('true')
    expect(wrapper.find(`#${textarea.attributes('aria-describedby')}`).text()).toBe('Too long')
  })

  it('shows a character counter only near the limit', async () => {
    const wrapper = mount(TextAreaField, { props: { label: 'D', name: 'd', maxlength: 100, modelValue: 'short' } })
    expect(wrapper.text()).not.toContain('/100')

    await wrapper.setProps({ modelValue: 'x'.repeat(85) })

    expect(wrapper.text()).toContain('85/100')
  })
})
