import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import type { MemberAttendance } from '@/stores/memberAttendance'
import EventSummaryCard from '../EventSummaryCard.vue'

type PublicEvent = MemberAttendance['event']

const base: PublicEvent = {
  title: 'Practice night',
  location: 'Main hall',
  description: 'Bring a board.\nBring a clock.',
  start: '2026-11-07T09:00:00Z',
  end: '2026-11-07T11:00:00Z',
  localStart: '2026-11-07T10:00:00',
  localEnd: '2026-11-07T12:00:00',
  timeZone: 'Europe/Madrid',
  typeName: 'Practice',
  typeColor: 'green',
  cancelled: false,
}

function mountCard(event: Partial<PublicEvent> = {}, viewerZone = 'Europe/Madrid') {
  return mount(EventSummaryCard, { props: { event: { ...base, ...event }, viewerZone } })
}

describe('EventSummaryCard', () => {
  it('shows the type with its color dot, the title, the time, the location and the description', () => {
    const wrapper = mountCard()

    expect(wrapper.get('[data-type]').text()).toBe('Practice')
    expect(wrapper.get('[data-color-dot]').classes().join(' ')).toMatch(/bg-green-/)
    expect(wrapper.get('h2').text()).toBe('Practice night')
    expect(wrapper.get('[data-time]').text()).toBe('Sat, Nov 7 · 10:00 – 12:00')
    expect(wrapper.get('[data-location]').text()).toBe('Main hall')
    expect(wrapper.get('[data-description]').text()).toContain('Bring a clock.')
  })

  it("adds the event's zone abbreviation when the viewer is in another zone", () => {
    const wrapper = mountCard({}, 'America/New_York')

    expect(wrapper.get('[data-time]').text()).toBe('Sat, Nov 7 · 10:00 – 12:00 CET')
  })

  it('keeps line breaks in the description', () => {
    expect(mountCard().get('[data-description]').classes()).toContain('whitespace-pre-line')
  })

  it('omits the location and description when there are none', () => {
    const wrapper = mountCard({ location: undefined, description: undefined })

    expect(wrapper.find('[data-location]').exists()).toBe(false)
    expect(wrapper.find('[data-description]').exists()).toBe(false)
  })

  it('renders organizer-written text as text, never as markup', () => {
    const wrapper = mountCard({ title: '<img src=x onerror=alert(1)>', description: '<b>bold</b>' })

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.find('b').exists()).toBe(false)
    expect(wrapper.get('h2').text()).toBe('<img src=x onerror=alert(1)>')
  })

  it('strikes the title through for a cancelled event', () => {
    expect(mountCard({ cancelled: true }).get('h2').classes()).toContain('line-through')
    expect(mountCard().get('h2').classes()).not.toContain('line-through')
  })
})
