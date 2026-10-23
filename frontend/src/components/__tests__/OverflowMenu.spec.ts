import { afterEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import OverflowMenu from '../OverflowMenu.vue'

const items = [
  { id: 'email', label: 'Email link' },
  { id: 'copy', label: 'Copy link' },
  { id: 'remove', label: 'Remove', destructive: true },
]

function mountMenu(props: Partial<InstanceType<typeof OverflowMenu>['$props']> = {}) {
  return mount(OverflowMenu, { props: { label: 'More actions', items, ...props }, attachTo: document.body })
}

const trigger = (w: ReturnType<typeof mountMenu>) => w.get('button[aria-haspopup=menu]')
const menuItems = (w: ReturnType<typeof mountMenu>) => w.findAll('[role=menuitem]')

describe('OverflowMenu', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('renders a labelled "…" button with the menu closed', () => {
    const wrapper = mountMenu()

    expect(trigger(wrapper).attributes('aria-label')).toBe('More actions')
    expect(trigger(wrapper).attributes('aria-expanded')).toBe('false')
    expect(wrapper.find('[role=menu]').exists()).toBe(false)
  })

  it('opens on click, lists the items in order and focuses the first one', async () => {
    const wrapper = mountMenu()

    await trigger(wrapper).trigger('click')

    expect(trigger(wrapper).attributes('aria-expanded')).toBe('true')
    expect(menuItems(wrapper).map((i) => i.text())).toEqual(['Email link', 'Copy link', 'Remove'])
    expect(document.activeElement).toBe(menuItems(wrapper)[0]!.element)
  })

  it('closes when the button is clicked again', async () => {
    const wrapper = mountMenu()
    await trigger(wrapper).trigger('click')

    await trigger(wrapper).trigger('click')

    expect(wrapper.find('[role=menu]').exists()).toBe(false)
  })

  it('moves focus with the arrow keys and wraps around at both ends', async () => {
    const wrapper = mountMenu()
    await trigger(wrapper).trigger('click')
    const menu = wrapper.get('[role=menu]')
    const rows = menuItems(wrapper).map((i) => i.element)

    await menu.trigger('keydown', { key: 'ArrowDown' })
    expect(document.activeElement).toBe(rows[1])
    await menu.trigger('keydown', { key: 'ArrowDown' })
    await menu.trigger('keydown', { key: 'ArrowDown' })
    expect(document.activeElement).toBe(rows[0])
    await menu.trigger('keydown', { key: 'ArrowUp' })
    expect(document.activeElement).toBe(rows[2])
  })

  it('closes on Escape and returns focus to the button', async () => {
    const wrapper = mountMenu()
    await trigger(wrapper).trigger('click')

    await wrapper.get('[role=menu]').trigger('keydown', { key: 'Escape' })

    expect(wrapper.find('[role=menu]').exists()).toBe(false)
    expect(document.activeElement).toBe(trigger(wrapper).element)
  })

  it('emits select with the item id and closes', async () => {
    const wrapper = mountMenu()
    await trigger(wrapper).trigger('click')

    await menuItems(wrapper)[1]!.trigger('click')

    expect(wrapper.emitted('select')).toEqual([['copy']])
    expect(wrapper.find('[role=menu]').exists()).toBe(false)
  })

  it('closes when the pointer goes down outside of it', async () => {
    const wrapper = mountMenu()
    await trigger(wrapper).trigger('click')

    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }))
    await wrapper.vm.$nextTick()

    expect(wrapper.find('[role=menu]').exists()).toBe(false)
  })

  it('keeps the menu open when the pointer goes down inside of it', async () => {
    const wrapper = mountMenu()
    await trigger(wrapper).trigger('click')

    menuItems(wrapper)[0]!.element.dispatchEvent(new Event('pointerdown', { bubbles: true }))
    await wrapper.vm.$nextTick()

    expect(wrapper.find('[role=menu]').exists()).toBe(true)
  })

  it('shows disabled items as disabled, skips them with the arrow keys and never selects them', async () => {
    const wrapper = mountMenu({
      items: [
        { id: 'a', label: 'First' },
        { id: 'b', label: 'Unavailable', disabled: true },
        { id: 'c', label: 'Last' },
      ],
    })
    await trigger(wrapper).trigger('click')
    const rows = menuItems(wrapper)

    expect(rows[1]!.attributes('disabled')).toBeDefined()
    await wrapper.get('[role=menu]').trigger('keydown', { key: 'ArrowDown' })
    expect(document.activeElement).toBe(rows[2]!.element)
    await rows[1]!.trigger('click')
    expect(wrapper.emitted('select')).toBeUndefined()
  })

  it('opens with nothing focusable inside when every item is disabled, without errors', async () => {
    const wrapper = mountMenu({ items: [{ id: 'a', label: 'Only', disabled: true }] })

    await trigger(wrapper).trigger('click')
    await wrapper.get('[role=menu]').trigger('keydown', { key: 'ArrowDown' })

    expect(wrapper.find('[role=menu]').exists()).toBe(true)
  })

  it('styles destructive items with the danger colour', async () => {
    const wrapper = mountMenu()
    await trigger(wrapper).trigger('click')

    expect(menuItems(wrapper)[2]!.classes().join(' ')).toContain('app-danger')
    expect(menuItems(wrapper)[0]!.classes().join(' ')).not.toContain('app-danger')
  })

  it('applies triggerClass to the button', () => {
    const wrapper = mountMenu({ triggerClass: 'sm:opacity-0' })

    expect(trigger(wrapper).classes()).toContain('sm:opacity-0')
  })
})
