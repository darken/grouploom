import { beforeAll, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import type { EventItem } from '@/stores/events'
import { browserTimeZone } from '@/ui/timeZones'
import ScheduleNotificationDialog from '../ScheduleNotificationDialog.vue'
import { installDialogPolyfill } from './dialogPolyfill'

beforeAll(installDialogPolyfill)

// Starts Mon 2 Nov 2026 10:00 Madrid.
const event: EventItem = {
  id: 'e1',
  title: 'Rehearsal',
  start: '2026-11-02T09:00:00Z',
  end: '2026-11-02T11:00:00Z',
  localStart: '2026-11-02T10:00:00',
  localEnd: '2026-11-02T12:00:00',
  timeZone: 'Europe/Madrid',
  eventType: { id: 't1', name: 'Practice', color: 'green', archived: false },
  version: 0,
  goingCount: 0,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

// A week and a bit before the start: every option is available.
const EARLY = new Date('2026-10-20T08:00:00Z')

function mountDialog(props: Record<string, unknown> = {}) {
  return mount(ScheduleNotificationDialog, {
    props: { open: true, event, audience: { eligible: 18, total: 24 }, now: EARLY, ...props },
    attachTo: document.body,
  })
}

type Wrapper = ReturnType<typeof mountDialog>
const radio = (w: Wrapper, label: string) =>
  w.findAll('input[type=radio][name=timing]').find((r) => r.element.closest('label')?.textContent?.includes(label))!
const option = (w: Wrapper, label: string) => radio(w, label).element.closest('label')!
const submit = (w: Wrapper) => w.findAll('button').find((b) => /^(Send now|Schedule)$/.test(b.text()))!

describe('ScheduleNotificationDialog', () => {
  it('offers Invitation and Reminder as a segmented control, defaulting to Invitation', () => {
    const wrapper = mountDialog()

    const kinds = wrapper.findAll('[role=radiogroup] [role=radio]')
    expect(kinds.map((k) => k.text())).toEqual(['Invitation', 'Reminder'])
    expect(kinds[0]!.attributes('aria-checked')).toBe('true')
  })

  it('lists Now, 2 hours before, 1 day before, 1 week before and Custom…, with Now selected', () => {
    const wrapper = mountDialog()

    const labels = wrapper.findAll('input[name=timing]').map((r) => r.element.closest('label')!.textContent!.trim())
    expect(labels).toEqual(['Now', '2 hours before', '1 day before', '1 week before', 'Custom…'])
    expect((radio(wrapper, 'Now').element as HTMLInputElement).checked).toBe(true)
  })

  it('disables options that would already be past, with "Too late" as the reason', () => {
    // 4 hours before the start: "2 hours before" still works; "1 day before" and "1 week before" are gone.
    const wrapper = mountDialog({ now: new Date('2026-11-02T05:00:00Z') })

    expect(radio(wrapper, '2 hours before').attributes('disabled')).toBeUndefined()
    for (const label of ['1 day before', '1 week before']) {
      expect(radio(wrapper, label).attributes('disabled')).toBeDefined()
      expect(option(wrapper, label).textContent).toContain('Too late')
    }
    expect(option(wrapper, '2 hours before').textContent).not.toContain('Too late')
    expect(radio(wrapper, 'Now').attributes('disabled')).toBeUndefined()
    expect(radio(wrapper, 'Custom…').attributes('disabled')).toBeUndefined()
  })

  it('keeps an option available within the one-minute allowance', () => {
    const wrapper = mountDialog({ now: new Date('2026-11-02T06:59:30Z') })

    expect(radio(wrapper, '2 hours before').attributes('disabled')).toBeUndefined()
  })

  it('reveals the date and time inputs only for Custom…', async () => {
    const wrapper = mountDialog()
    expect(wrapper.find('input[type=date]').exists()).toBe(false)

    await radio(wrapper, 'Custom…').setValue(true)

    expect(wrapper.find('input[aria-label="Start date"]').attributes('type')).toBe('date')
    expect(wrapper.find('input[aria-label="Start time"]').attributes('type')).toBe('time')
    expect(wrapper.find('input[aria-label="End date"]').exists()).toBe(false)
  })

  it('names the event zone next to the custom time only when it differs from the viewer\'s', async () => {
    const wrapper = mountDialog()
    await radio(wrapper, 'Custom…').setValue(true)

    expect(wrapper.text().includes('Times are in Europe/Madrid')).toBe(browserTimeZone() !== 'Europe/Madrid')
  })

  it('says who the invitation will reach, and why some members are left out', () => {
    const wrapper = mountDialog()

    expect(wrapper.find('[data-audience]').text()).toBe(
      'Will be sent to 18 of 24 members. 6 have paused invitations or turned off Practice.',
    )
  })

  it('uses the singular when one member is left out', () => {
    const wrapper = mountDialog({ audience: { eligible: 23, total: 24 } })

    expect(wrapper.find('[data-audience]').text()).toBe(
      'Will be sent to 23 of 24 members. 1 has paused invitations or turned off Practice.',
    )
  })

  it('uses the singular "member" when the group has one member', () => {
    const wrapper = mountDialog({ audience: { eligible: 1, total: 1 } })

    expect(wrapper.find('[data-audience]').text()).toBe('Will be sent to 1 of 1 member.')
  })

  it('does not explain exclusions when everyone is eligible', () => {
    const wrapper = mountDialog({ audience: { eligible: 24, total: 24 } })

    expect(wrapper.find('[data-audience]').text()).toBe('Will be sent to 24 of 24 members.')
  })

  it('warns in orange when no member is eligible, yet still lets the organizer continue', () => {
    const wrapper = mountDialog({ audience: { eligible: 0, total: 5 } })

    const line = wrapper.find('[data-audience]')
    expect(line.text()).toBe('No members will receive this right now.')
    expect(line.classes().join(' ')).toContain('orange')
    expect(submit(wrapper).attributes('disabled')).toBeUndefined()
  })

  it('shows no audience line while the audience is unknown', () => {
    expect(mountDialog({ audience: null }).find('[data-audience]').exists()).toBe(false)
  })

  it('reads "Send now" for Now and "Schedule" for anything else', async () => {
    const wrapper = mountDialog()
    expect(submit(wrapper).text()).toBe('Send now')

    await radio(wrapper, '1 day before').setValue(true)
    expect(submit(wrapper).text()).toBe('Schedule')

    await radio(wrapper, 'Custom…').setValue(true)
    expect(submit(wrapper).text()).toBe('Schedule')

    await radio(wrapper, 'Now').setValue(true)
    expect(submit(wrapper).text()).toBe('Send now')
  })

  it('submits an invitation now, without a note', async () => {
    const wrapper = mountDialog()

    await submit(wrapper).trigger('click')

    expect(wrapper.emitted('submit')).toEqual([[{ kind: 'INVITATION', timing: 'NOW' }]])
  })

  it('submits a reminder a number of minutes before the start', async () => {
    const wrapper = mountDialog()
    await wrapper.findAll('[role=radio]')[1]!.trigger('click')

    await radio(wrapper, '1 day before').setValue(true)
    await submit(wrapper).trigger('click')

    expect(wrapper.emitted('submit')).toEqual([[{ kind: 'REMINDER', timing: 'BEFORE_START', offsetMinutes: 1440 }]])
  })

  it.each([
    ['2 hours before', 120],
    ['1 week before', 10080],
  ])('submits %s as %i minutes', async (label, minutes) => {
    const wrapper = mountDialog()

    await radio(wrapper, label).setValue(true)
    await submit(wrapper).trigger('click')

    expect(wrapper.emitted('submit')![0]![0]).toMatchObject({ timing: 'BEFORE_START', offsetMinutes: minutes })
  })

  it('submits a custom time as the wall-clock time in the event zone', async () => {
    const wrapper = mountDialog()

    await radio(wrapper, 'Custom…').setValue(true)
    await wrapper.find('input[aria-label="Start date"]').setValue('2026-10-30')
    await wrapper.find('input[aria-label="Start time"]').setValue('18:30')
    await submit(wrapper).trigger('click')

    expect(wrapper.emitted('submit')).toEqual([[{ kind: 'INVITATION', timing: 'AT', localSendAt: '2026-10-30T18:30:00' }]])
  })

  it('holds back a custom time that has no date yet', async () => {
    const wrapper = mountDialog()

    await radio(wrapper, 'Custom…').setValue(true)
    await wrapper.find('input[aria-label="Start date"]').setValue('')
    await submit(wrapper).trigger('click')

    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('includes the note, trimmed, and leaves it out when blank', async () => {
    const wrapper = mountDialog()

    await wrapper.find('textarea').setValue('  Bring a board  ')
    await submit(wrapper).trigger('click')
    await wrapper.find('textarea').setValue('   ')
    await submit(wrapper).trigger('click')

    expect(wrapper.emitted('submit')).toEqual([
      [{ kind: 'INVITATION', timing: 'NOW', message: 'Bring a board' }],
      [{ kind: 'INVITATION', timing: 'NOW' }],
    ])
  })

  it('limits the note to 1000 characters and shows a counter near the limit', async () => {
    const wrapper = mountDialog()
    const textarea = wrapper.find('textarea')

    expect(wrapper.find('label[for]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Add a note (optional)')
    expect(textarea.attributes('maxlength')).toBe('1000')
    await textarea.setValue('x'.repeat(850))
    expect(wrapper.text()).toContain('850/1000')
  })

  it('submits with Cmd+Enter and Ctrl+Enter', async () => {
    const wrapper = mountDialog()

    await wrapper.find('textarea').trigger('keydown', { key: 'Enter', metaKey: true })
    await wrapper.find('textarea').trigger('keydown', { key: 'Enter', ctrlKey: true })
    await wrapper.find('textarea').trigger('keydown', { key: 'Enter' })

    expect(wrapper.emitted('submit')).toHaveLength(2)
  })

  it('shows server field errors under the matching field', async () => {
    const wrapper = mountDialog({ fieldErrors: { localSendAt: 'Pick a time before the event starts.' } })
    await radio(wrapper, 'Custom…').setValue(true)

    expect(wrapper.text()).toContain('Pick a time before the event starts.')
  })

  it('disables the button while saving and closes from Cancel', async () => {
    const wrapper = mountDialog({ saving: true })

    expect(submit(wrapper).attributes('disabled')).toBeDefined()
    await wrapper.findAll('button').find((b) => b.text() === 'Cancel')!.trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  describe('audience by kind', () => {
    const reminder = (w: Wrapper) => w.findAll('[role=radiogroup] [role=radio]').find((k) => k.text() === 'Reminder')!

    it('asks for the reminder audience when switching to Reminder, and for the invitation audience when switching back', async () => {
      const wrapper = mountDialog()

      await reminder(wrapper).trigger('click')
      await wrapper.findAll('[role=radiogroup] [role=radio]')[0]!.trigger('click')

      expect(wrapper.emitted('kind-change')).toEqual([['REMINDER'], ['INVITATION']])
    })

    it('says that members who cannot go are skipped, for reminders only', async () => {
      const wrapper = mountDialog({ audience: { eligible: 15, total: 24 } })
      expect(wrapper.find('[data-audience]').text()).not.toContain("can't go")

      await reminder(wrapper).trigger('click')

      expect(wrapper.find('[data-audience]').text()).toBe(
        "Will be sent to 15 of 24 members. 9 have paused invitations or turned off Practice. Members who said they can't go are skipped.",
      )
    })

    it('leaves the skipped note off the warning when nobody will receive it', async () => {
      const wrapper = mountDialog({ audience: { eligible: 0, total: 5 } })

      await reminder(wrapper).trigger('click')

      expect(wrapper.find('[data-audience]').text()).toBe('No members will receive this right now.')
    })
  })
})
