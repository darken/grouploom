import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import LinkProblem from '../LinkProblem.vue'

describe('LinkProblem', () => {
  it('explains an invalid link and what to do next', () => {
    const wrapper = mount(LinkProblem, { props: { kind: 'invalid' } })

    expect(wrapper.text()).toContain("This link isn't valid.")
    expect(wrapper.text()).toContain('ask your organizer for a new one')
  })

  it('explains an expired link', () => {
    const wrapper = mount(LinkProblem, { props: { kind: 'expired' } })

    expect(wrapper.text()).toContain('This link has expired.')
    expect(wrapper.text()).toContain('Ask your organizer for a new one.')
  })
})
