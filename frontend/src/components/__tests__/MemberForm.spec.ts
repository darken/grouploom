import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import type { Member } from '@/stores/members'
import MemberForm from '../MemberForm.vue'

const existing: Member = {
  id: 'abc',
  name: 'Ana Pérez',
  email: 'ana@example.com',
  version: 3,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
  invitationsPaused: false,
}

const save = (w: ReturnType<typeof mount>) => w.find('button[type=submit]')
const another = (w: ReturnType<typeof mount>) => w.findAll('button').find((b) => b.text() === 'Save and add another')

describe('MemberForm', () => {
  it('renders an email input that browsers should not autofill', () => {
    const wrapper = mount(MemberForm)

    const email = wrapper.find('input[name=email]')
    expect(email.attributes('type')).toBe('email')
    expect(email.attributes('autocomplete')).toBe('off')
    expect(wrapper.find('input[name=name]').attributes('autocomplete')).toBe('off')
  })

  it('shows an error on blur when the name is blank', async () => {
    const wrapper = mount(MemberForm)

    await wrapper.find('input[name=name]').trigger('blur')

    expect(wrapper.text()).toContain('Enter a name.')
    expect(wrapper.find('input[name=name]').attributes('aria-invalid')).toBe('true')
  })

  it('shows an error on blur when the email is blank', async () => {
    const wrapper = mount(MemberForm)

    await wrapper.find('input[name=email]').trigger('blur')

    expect(wrapper.text()).toContain('Enter an email address.')
  })

  it.each(['a@b', 'plain', '@example.com', 'a b@example.com'])('shows an error on blur for the malformed email %s', async (email) => {
    const wrapper = mount(MemberForm)

    await wrapper.find('input[name=email]').setValue(email)
    await wrapper.find('input[name=email]').trigger('blur')

    expect(wrapper.text()).toContain('Enter a valid email address.')
  })

  it('shows length errors for a long name and email', async () => {
    const wrapper = mount(MemberForm)

    await wrapper.find('input[name=name]').setValue('x'.repeat(121))
    await wrapper.find('input[name=name]').trigger('blur')
    await wrapper.find('input[name=email]').setValue(`${'a'.repeat(250)}@b.co`)
    await wrapper.find('input[name=email]').trigger('blur')

    expect(wrapper.text()).toContain('Use 120 characters or fewer.')
    expect(wrapper.text()).toContain('Use 254 characters or fewer.')
  })

  it('keeps Save disabled until name and email are valid', async () => {
    const wrapper = mount(MemberForm)
    expect(save(wrapper).attributes('disabled')).toBeDefined()

    await wrapper.find('input[name=name]').setValue('Ana')
    await wrapper.find('input[name=email]').setValue('ana@example.com')

    expect(save(wrapper).attributes('disabled')).toBeUndefined()
  })

  it('submits a trimmed create payload', async () => {
    const wrapper = mount(MemberForm)

    await wrapper.find('input[name=name]').setValue('  Ana Pérez ')
    await wrapper.find('input[name=email]').setValue(' Ana@Example.com ')
    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('submit')?.[0]).toEqual([{ name: 'Ana Pérez', email: 'Ana@Example.com' }])
  })

  it('does not submit an invalid form and shows the errors instead', async () => {
    const wrapper = mount(MemberForm)

    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('submit')).toBeUndefined()
    expect(wrapper.text()).toContain('Enter a name.')
    expect(wrapper.text()).toContain('Enter an email address.')
  })

  it('submits with Ctrl+Enter or Cmd+Enter when valid', async () => {
    const wrapper = mount(MemberForm)
    await wrapper.find('input[name=name]').setValue('Ana')
    await wrapper.find('input[name=email]').setValue('ana@example.com')

    await wrapper.find('input[name=name]').trigger('keydown', { key: 'Enter', ctrlKey: true })
    await wrapper.find('input[name=name]').trigger('keydown', { key: 'Enter', metaKey: true })

    expect(wrapper.emitted('submit')).toHaveLength(2)
  })

  it('prefills edit mode and includes the version in the payload', async () => {
    const wrapper = mount(MemberForm, { props: { member: existing } })

    expect((wrapper.find('input[name=name]').element as HTMLInputElement).value).toBe('Ana Pérez')
    expect((wrapper.find('input[name=email]').element as HTMLInputElement).value).toBe('ana@example.com')

    await wrapper.find('input[name=name]').setValue('Ana María Pérez')
    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('submit')?.[0]).toEqual([{ name: 'Ana María Pérez', email: 'ana@example.com', version: 3 }])
  })

  it('keeps Save disabled in edit mode while nothing has changed', async () => {
    const wrapper = mount(MemberForm, { props: { member: existing } })
    expect(save(wrapper).attributes('disabled')).toBeDefined()

    await wrapper.find('input[name=email]').setValue('ana.perez@example.com')

    expect(save(wrapper).attributes('disabled')).toBeUndefined()
  })

  it('shows server field errors under the matching field', () => {
    const wrapper = mount(MemberForm, { props: { fieldErrors: { email: 'A member with this email already exists.' } } })

    expect(wrapper.text()).toContain('A member with this email already exists.')
    expect(wrapper.find('input[name=email]').attributes('aria-invalid')).toBe('true')
  })

  it('offers "Save and add another" only when creating', () => {
    expect(another(mount(MemberForm))).toBeDefined()
    expect(another(mount(MemberForm, { props: { member: existing } }))).toBeUndefined()
  })

  it('emits submit-another with the payload from "Save and add another"', async () => {
    const wrapper = mount(MemberForm)
    expect(another(wrapper)!.attributes('disabled')).toBeDefined()
    await wrapper.find('input[name=name]').setValue('Ana')
    await wrapper.find('input[name=email]').setValue('ana@example.com')

    await another(wrapper)!.trigger('click')

    expect(wrapper.emitted('submit-another')?.[0]).toEqual([{ name: 'Ana', email: 'ana@example.com' }])
    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('reset clears the form and focuses the name field', async () => {
    const wrapper = mount(MemberForm, { attachTo: document.body })
    await wrapper.find('input[name=name]').setValue('Ana')
    await wrapper.find('input[name=email]').setValue('ana@example.com')
    await wrapper.find('input[name=email]').trigger('blur')

    wrapper.vm.reset()
    await wrapper.vm.$nextTick()

    expect((wrapper.find('input[name=name]').element as HTMLInputElement).value).toBe('')
    expect((wrapper.find('input[name=email]').element as HTMLInputElement).value).toBe('')
    expect(document.activeElement).toBe(wrapper.find('input[name=name]').element)
    expect(wrapper.text()).not.toContain('Enter an email address.')
    wrapper.unmount()
  })

  it('emits cancel from the Cancel button', async () => {
    const wrapper = mount(MemberForm)

    await wrapper.findAll('button').find((b) => b.text() === 'Cancel')!.trigger('click')

    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })
})
