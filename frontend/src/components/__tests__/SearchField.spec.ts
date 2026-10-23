import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'

import SearchField from '../SearchField.vue'

const clearButton = (w: ReturnType<typeof mount>) => w.find('button[aria-label="Clear search"]')

describe('SearchField', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => {
    vi.useRealTimers()
    document.body.innerHTML = ''
  })

  it('renders a labelled search input', () => {
    const wrapper = mount(SearchField, { props: { modelValue: '' } })

    const input = wrapper.find('input')
    expect(input.attributes('type')).toBe('search')
    expect(input.attributes('aria-label')).toBe('Search members')
  })

  it('emits only the final value, 250ms after the last keystroke', async () => {
    const wrapper = mount(SearchField, { props: { modelValue: '' } })
    const input = wrapper.find('input')

    await input.setValue('a')
    vi.advanceTimersByTime(200)
    await input.setValue('an')
    vi.advanceTimersByTime(249)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()

    vi.advanceTimersByTime(1)
    expect(wrapper.emitted('update:modelValue')).toEqual([['an']])
  })

  it('shows the clear button only when there is text', async () => {
    const wrapper = mount(SearchField, { props: { modelValue: '' } })
    expect(clearButton(wrapper).exists()).toBe(false)

    await wrapper.find('input').setValue('ana')

    expect(clearButton(wrapper).exists()).toBe(true)
  })

  it('clears immediately and cancels a pending emit when the clear button is clicked', async () => {
    const wrapper = mount(SearchField, { props: { modelValue: 'ana' } })
    await wrapper.find('input').setValue('anab')

    await clearButton(wrapper).trigger('click')
    vi.advanceTimersByTime(500)

    expect((wrapper.find('input').element as HTMLInputElement).value).toBe('')
    expect(wrapper.emitted('update:modelValue')).toEqual([['']])
  })

  it('clears on Escape', async () => {
    const wrapper = mount(SearchField, { props: { modelValue: 'ana' } })

    await wrapper.find('input').trigger('keydown', { key: 'Escape' })

    expect((wrapper.find('input').element as HTMLInputElement).value).toBe('')
    expect(wrapper.emitted('update:modelValue')).toEqual([['']])
  })

  it('follows the model value when it changes from outside', async () => {
    const wrapper = mount(SearchField, { props: { modelValue: 'ana' } })

    await wrapper.setProps({ modelValue: '' })

    expect((wrapper.find('input').element as HTMLInputElement).value).toBe('')
    expect(clearButton(wrapper).exists()).toBe(false)
  })

  it('focuses the input when "/" is pressed anywhere on the page', () => {
    const wrapper = mount(SearchField, { props: { modelValue: '' }, attachTo: document.body })

    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: '/', bubbles: true }))

    expect(document.activeElement).toBe(wrapper.find('input').element)
    wrapper.unmount()
  })

  it('does not steal "/" while typing in another field', () => {
    const wrapper = mount(SearchField, { props: { modelValue: '' }, attachTo: document.body })
    const other = document.createElement('input')
    document.body.appendChild(other)
    other.focus()

    other.dispatchEvent(new KeyboardEvent('keydown', { key: '/', bubbles: true }))

    expect(document.activeElement).toBe(other)
    wrapper.unmount()
  })
})
