import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'

import AppHeader from '../AppHeader.vue'

async function mountHeader(path: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<i/>' } },
      { path: '/event-types', component: { template: '<i/>' } },
    ],
  })
  await router.push(path)
  await router.isReady()
  return mount(AppHeader, { global: { plugins: [createPinia(), router] } })
}

describe('AppHeader navigation', () => {
  it('has Home and Event types links in a labelled nav', async () => {
    const wrapper = await mountHeader('/')

    const nav = wrapper.find('nav[aria-label=Primary]')
    expect(nav.findAll('a').map((a) => a.text())).toEqual(['Home', 'Event types'])
  })

  it('marks only the current page link with aria-current', async () => {
    const wrapper = await mountHeader('/event-types')

    const current = wrapper.findAll('nav a').filter((a) => a.attributes('aria-current') === 'page')
    expect(current.map((a) => a.text())).toEqual(['Event types'])
  })
})
