import { beforeEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

import { useMemberAttendanceStore } from '@/stores/memberAttendance'
import { useMemberPreferencesStore } from '@/stores/memberPreferences'
import MemberLayout from '../MemberLayout.vue'

describe('MemberLayout', () => {
  beforeEach(() => setActivePinia(createPinia()))

  function mountLayout() {
    return mount(MemberLayout, { slots: { default: '<p>content</p>' }, global: { plugins: [] } })
  }

  it('has no app header or navigation and renders the page in a single narrow column', () => {
    const wrapper = mountLayout()

    expect(wrapper.find('header nav, nav').exists()).toBe(false)
    expect(wrapper.get('main').text()).toBe('content')
    expect(wrapper.get('main').classes()).toContain('max-w-lg')
  })

  it('shows the group name as a quiet wordmark and in the footer once it is known', () => {
    useMemberPreferencesStore().data = {
      memberName: 'Ana',
      maskedEmail: 'a••@example.com',
      groupName: 'Chess Club',
      invitationsPaused: false,
      eventTypes: [],
    }

    const wrapper = mountLayout()

    expect(wrapper.get('[data-wordmark]').text()).toBe('Chess Club')
    expect(wrapper.get('footer').text()).toBe('Sent by Chess Club via GroupLoom')
  })

  it('shows the group name on the reply page too', () => {
    useMemberAttendanceStore().data = {
      groupName: 'Chess Club',
      memberName: 'Ana',
      open: true,
      event: {
        title: 'Practice',
        start: '2026-11-07T09:00:00Z',
        end: '2026-11-07T11:00:00Z',
        localStart: '2026-11-07T10:00:00',
        localEnd: '2026-11-07T12:00:00',
        timeZone: 'UTC',
        typeName: 'Practice',
        typeColor: 'green',
        cancelled: false,
      },
    }

    const wrapper = mountLayout()

    expect(wrapper.get('[data-wordmark]').text()).toBe('Chess Club')
    expect(wrapper.get('footer').text()).toBe('Sent by Chess Club via GroupLoom')
  })

  it('stays neutral before the group name is known', () => {
    const wrapper = mountLayout()

    expect(wrapper.find('[data-wordmark]').exists()).toBe(false)
    expect(wrapper.get('footer').text()).toBe('GroupLoom')
  })
})
