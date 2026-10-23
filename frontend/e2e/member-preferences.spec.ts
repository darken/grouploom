import { test, expect, type Page } from '@playwright/test'

// Needs the backend (and Docker for Postgres); playwright.config.ts starts it.
test.use({ permissions: ['clipboard-read', 'clipboard-write'] })

async function createEventType(page: Page, name: string) {
  await page.getByRole('button', { name: /^(Create event type|New event type)$/ }).first().click()
  const dialog = page.getByRole('dialog', { name: 'New event type' })
  await dialog.getByLabel('Name').fill(name)
  await dialog.getByRole('button', { name: 'Save' }).click()
  await expect(page.getByRole('button', { name: new RegExp(`^${name}`) })).toBeVisible()
}

/** Generates a link for the member and returns it from the clipboard, or from the fallback dialog. */
async function copyLink(page: Page, memberName: string): Promise<string> {
  await page.getByRole('button', { name: `Actions for ${memberName}` }).click()
  await page.getByRole('menuitem', { name: 'Copy preferences link' }).click()
  const fallback = page.getByRole('dialog', { name: 'Preferences link' })
  const toast = page.getByRole('status').filter({ hasText: 'Link copied. It works for' })
  await expect(toast.or(fallback)).toBeVisible()
  if (await fallback.isVisible()) return fallback.getByRole('textbox').inputValue()
  return page.evaluate(() => navigator.clipboard.readText())
}

test('members choose their email preferences from a personal link', async ({ page, browser }) => {
  const email = `e2e-pref-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`

  // --- organizer: sign up, two event types, one member, a link ---
  await page.goto('/signup')
  await page.getByLabel('Group name').fill('E2E Club')
  await page.getByLabel('Your name').fill('Ada Lovelace')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password', { exact: true }).fill('correct horse battery')
  await page.getByRole('button', { name: 'Create group' }).click()
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Welcome, Ada Lovelace')

  const nav = page.getByRole('navigation', { name: 'Primary' })
  await nav.getByRole('link', { name: 'Event types' }).click()
  await createEventType(page, 'Practice')
  await createEventType(page, 'Game night')

  await nav.getByRole('link', { name: 'Members' }).click()
  await page.getByRole('button', { name: 'Add member' }).first().click()
  const form = page.getByRole('dialog', { name: 'Add member' })
  await form.getByLabel('Name').fill('Ana Pérez')
  await form.getByLabel('Email').fill('ana@example.com')
  await form.getByRole('button', { name: 'Save', exact: true }).click()
  await expect(page.getByRole('button', { name: /^Ana Pérez/ })).toBeVisible()

  const link = await copyLink(page, 'Ana Pérez')
  expect(link).toMatch(/\/m\/preferences#t=[\w-]{43}$/)

  // --- member: a fresh browser context that is not signed in ---
  const memberContext = await browser.newContext()
  const memberPage = await memberContext.newPage()
  await memberPage.goto(link)

  await expect(memberPage.getByRole('heading', { level: 1 })).toHaveText('Email preferences')
  await expect(memberPage.getByText('Hi Ana Pérez — choose which E2E Club events')).toBeVisible()
  await expect(memberPage.getByText('Sending to a••@example.com')).toBeVisible()
  await expect(memberPage.getByRole('navigation')).toHaveCount(0)
  // The token never stays in the address bar.
  await expect(memberPage).not.toHaveURL(/#t=/)
  expect(memberPage.url()).not.toContain('t=')

  const practice = memberPage.getByRole('switch', { name: 'Practice' })
  const game = memberPage.getByRole('switch', { name: 'Game night' })
  await expect(practice).toBeChecked()
  await expect(game).toBeChecked()

  // Turn one type off: it saves on its own and survives a reload.
  await game.click()
  await expect(game).not.toBeChecked()
  await expect(memberPage.getByText('Saved')).toBeVisible()
  await memberPage.reload()
  await expect(memberPage.getByRole('switch', { name: 'Game night' })).not.toBeChecked()
  await expect(memberPage.getByRole('switch', { name: 'Practice' })).toBeChecked()
  expect(memberPage.url()).not.toContain('t=')

  // Pause everything; the type choices are kept but disabled.
  await memberPage.getByRole('switch', { name: 'Pause all invitations' }).click()
  await expect(memberPage.getByText('Invitations are paused. Your choices below are kept.')).toBeVisible()
  await expect(memberPage.getByRole('switch', { name: 'Practice' })).toBeDisabled()
  await expect(memberPage.getByText('Saved')).toBeVisible()

  // --- organizer sees who paused ---
  await page.reload()
  await expect(
    page.getByRole('button', { name: /^Ana Pérez/ }).getByText('Paused'),
  ).toBeVisible()

  // --- changing the email invalidates the old link ---
  await page.getByRole('button', { name: /^Ana Pérez/ }).click()
  const edit = page.getByRole('dialog', { name: 'Edit member' })
  await edit.getByLabel('Email').fill('ana.perez@example.com')
  await edit.getByRole('button', { name: 'Save', exact: true }).click()
  await expect(edit).toBeHidden()

  // A fresh tab, as when the member clicks the old link in their email again.
  const staleTab = await memberContext.newPage()
  await staleTab.goto(link)
  await expect(staleTab.getByText("This link isn't valid.")).toBeVisible()
  await expect(staleTab.getByRole('switch')).toHaveCount(0)

  await memberContext.close()
})
