import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import ResponseChoice from '../ResponseChoice.vue'

// Wired like v-model: the parent stores what the component emits.
function mountChoice(modelValue: 'GOING' | 'NOT_GOING' | null = null) {
  const wrapper = mount(ResponseChoice, {
    props: { modelValue, 'onUpdate:modelValue': (value: unknown) => wrapper.setProps({ modelValue: value as 'GOING' }) },
    attachTo: document.body,
  })
  return wrapper
}

const lastEmitted = (w: ReturnType<typeof mountChoice>) => w.emitted('update:modelValue')?.slice(-1)[0]
const radios = (w: ReturnType<typeof mountChoice>) => w.findAll('[role=radio]')

describe('ResponseChoice', () => {
  it('is a radio group with a Going and a Can\'t go choice', () => {
    const wrapper = mountChoice()

    expect(wrapper.get('[role=radiogroup]').attributes('aria-label')).toBe('Will you come?')
    expect(radios(wrapper).map((r) => r.text())).toEqual(['Going', "Can't go"])
  })

  it('marks the selected choice and nothing else', () => {
    const wrapper = mountChoice('NOT_GOING')

    expect(radios(wrapper).map((r) => r.attributes('aria-checked'))).toEqual(['false', 'true'])
  })

  it('has no checked choice before the member picks one', () => {
    const wrapper = mountChoice()

    expect(radios(wrapper).map((r) => r.attributes('aria-checked'))).toEqual(['false', 'false'])
  })

  it('keeps one tab stop: the selected choice, or the first when nothing is selected', () => {
    expect(radios(mountChoice('NOT_GOING')).map((r) => r.attributes('tabindex'))).toEqual(['-1', '0'])
    expect(radios(mountChoice()).map((r) => r.attributes('tabindex'))).toEqual(['0', '-1'])
  })

  it('selects a choice when it is tapped', async () => {
    const wrapper = mountChoice()

    await radios(wrapper)[1]!.trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([['NOT_GOING']])
  })

  it('moves the selection and focus with the arrow keys, wrapping around', async () => {
    const wrapper = mountChoice('GOING')

    await radios(wrapper)[0]!.trigger('keydown', { key: 'ArrowRight' })
    expect(lastEmitted(wrapper)).toEqual(['NOT_GOING'])
    expect(document.activeElement).toBe(radios(wrapper)[1]!.element)

    await radios(wrapper)[1]!.trigger('keydown', { key: 'ArrowDown' })
    expect(lastEmitted(wrapper)).toEqual(['GOING'])

    await radios(wrapper)[0]!.trigger('keydown', { key: 'ArrowLeft' })
    expect(lastEmitted(wrapper)).toEqual(['NOT_GOING'])
    await radios(wrapper)[1]!.trigger('keydown', { key: 'ArrowUp' })
    expect(lastEmitted(wrapper)).toEqual(['GOING'])
  })

  it('starts at Going when an arrow key is pressed with nothing selected', async () => {
    const wrapper = mountChoice()

    await radios(wrapper)[0]!.trigger('keydown', { key: 'ArrowRight' })

    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['GOING'])
  })

  it('gives the selected card an accent ring and, for Going, a soft green tint', () => {
    const going = radios(mountChoice('GOING'))
    const notGoing = radios(mountChoice('NOT_GOING'))

    expect(going[0]!.classes()).toContain('ring-2')
    expect(going[0]!.classes().join(' ')).toMatch(/bg-green-/)
    expect(going[1]!.classes()).not.toContain('ring-2')
    expect(notGoing[1]!.classes()).toContain('ring-2')
    expect(notGoing[1]!.classes().join(' ')).not.toMatch(/bg-green-/)
  })

  it('stacks the cards below 360px and puts them side by side from there', () => {
    const classes = mountChoice().get('[role=radiogroup]').classes()

    expect(classes).toContain('grid-cols-1')
    expect(classes).toContain('min-[360px]:grid-cols-2')
  })

  it('draws a check and a cross as inline icons hidden from assistive tech', () => {
    const wrapper = mountChoice()

    const icons = wrapper.findAll('[role=radio] svg')
    expect(icons).toHaveLength(2)
    for (const icon of icons) expect(icon.attributes('aria-hidden')).toBe('true')
  })

  it('has cards at least 44px tall', () => {
    for (const radio of radios(mountChoice())) expect(radio.classes().join(' ')).toMatch(/min-h-(1[1-9]|[2-9]\d|\[)/)
  })
})
