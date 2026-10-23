import { test, expect } from '@playwright/test'

// Needs the backend, Postgres and Mailpit (compose.yaml starts both; inbox at http://localhost:8025).
const MAILPIT = 'http://localhost:8025'

test('an organizer emails a member their preferences link and the link works', async ({ page, request }) => {
  test.setTimeout(60_000)
  const stamp = `${Date.now()}-${Math.floor(Math.random() * 1e6)}`
  const memberEmail = `ana-${stamp}@example.com`

  await page.goto('/signup')
  await page.getByLabel('Group name').fill('E2E Mail Club')
  await page.getByLabel('Your name').fill('Ada Lovelace')
  await page.getByLabel('Email').fill(`e2e-mail-${stamp}@example.com`)
  await page.getByLabel('Password', { exact: true }).fill('correct horse battery')
  await page.getByRole('button', { name: 'Create group' }).click()
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Welcome, Ada Lovelace')

  await page.getByRole('navigation', { name: 'Primary' }).getByRole('link', { name: 'Members' }).click()
  await page.getByRole('button', { name: 'Add member' }).first().click()
  const form = page.getByRole('dialog', { name: 'Add member' })
  await form.getByLabel('Name').fill('Ana Pérez')
  await form.getByLabel('Email').fill(memberEmail)
  await form.getByRole('button', { name: 'Save', exact: true }).click()
  const row = page.getByRole('listitem').filter({ hasText: 'Ana Pérez' })
  await expect(row).toBeVisible()

  // Queued: the row says so right away, then settles once the dispatcher has sent it.
  await page.getByRole('button', { name: 'Actions for Ana Pérez' }).click()
  await page.getByRole('menuitem', { name: 'Email preferences link' }).click()
  await expect(page.getByText('Sending link to Ana Pérez')).toBeVisible()
  const status = row.getByRole('status')
  await expect(status).toHaveText('Sending link…')
  await expect(status).toContainText('Link sent ·', { timeout: 30_000 })

  // Sending again right away is held back by the cooldown.
  await page.getByRole('button', { name: 'Actions for Ana Pérez' }).click()
  await page.getByRole('menuitem', { name: 'Email preferences link' }).click()
  await expect(page.getByText('A link was sent to Ana Pérez a moment ago. Try again in a few minutes.')).toBeVisible()

  // The message really arrived, with both parts and the organizer as Reply-To.
  const query = encodeURIComponent(`to:${memberEmail}`)
  await expect
    .poll(async () => (await (await request.get(`${MAILPIT}/api/v1/search?query=${query}`)).json()).messages.length)
    .toBe(1)
  const { messages } = await (await request.get(`${MAILPIT}/api/v1/search?query=${query}`)).json()
  const message = await (await request.get(`${MAILPIT}/api/v1/message/${messages[0].ID}`)).json()
  expect(message.Subject).toBe('Choose which E2E Mail Club events you hear about')
  expect(message.From.Name).toBe('E2E Mail Club via GroupLoom')
  expect(message.HTML).toContain('Set your preferences')
  const link = /http:\/\/localhost:5173\/m\/preferences#t=[\w-]+/.exec(message.Text)?.[0]
  expect(link).toBeTruthy()

  // The member opens the link, signed out, in their own browser context.
  const memberPage = await (await page.context().browser()!.newContext()).newPage()
  await memberPage.goto(link!)
  await expect(memberPage.getByRole('heading', { level: 1 })).toHaveText('Email preferences')
  await expect(memberPage.getByText('Hi Ana Pérez — choose which E2E Mail Club events')).toBeVisible()
  await memberPage.context().close()
})
