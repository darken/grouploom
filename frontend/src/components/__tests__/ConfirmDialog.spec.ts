import { beforeAll, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import ConfirmDialog from '../ConfirmDialog.vue'
import { installDialogPolyfill } from './dialogPolyfill'

beforeAll(installDialogPolyfill)

const props = { open: true, title: "Delete 'Weekly practice'?", message: "This can't be undone.", confirmLabel: 'Delete' }

describe('ConfirmDialog', () => {
  it('shows the message and emits confirm / cancel from its buttons', async () => {
    const wrapper = mount(ConfirmDialog, { props })

    expect(wrapper.text()).toContain("This can't be undone.")
    await wrapper.findAll('button').find((b) => b.text() === 'Delete')!.trigger('click')
    await wrapper.findAll('button').find((b) => b.text() === 'Cancel')!.trigger('click')

    expect(wrapper.emitted('confirm')).toHaveLength(1)
    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })

  it('emits cancel when dismissed with Escape', () => {
    const wrapper = mount(ConfirmDialog, { props })

    wrapper.find('dialog').element.dispatchEvent(new Event('cancel', { cancelable: true }))

    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })

  it('styles the confirm button as destructive (red) only when asked', () => {
    const destructive = mount(ConfirmDialog, { props: { ...props, destructive: true } })
    const plain = mount(ConfirmDialog, { props })

    const confirm = (w: typeof destructive) => w.findAll('button').find((b) => b.text() === 'Delete')!
    expect(confirm(destructive).classes().join(' ')).toContain('danger')
    expect(confirm(plain).classes().join(' ')).not.toContain('danger')
  })
})
