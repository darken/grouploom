import { test, expect } from '@playwright/test'

// Needs the backend, Postgres and Mailpit (compose.yaml starts both; inbox at http://localhost:8025).
const MAILPIT = 'http://localhost:8025'

test.use({ permissions: ['clipboard-read', 'clipboard-write'] })

test('notifies members about an event, honors paused members, unschedules and cancels', async ({ page, request, browser }) => {
  test.setTimeout(180_000)
  const stamp = `${Date.now()}-${Math.floor(Math.random() * 1e6)}`
  const anaEmail = `ana-${stamp}@example.com`
  const bobEmail = `bob-${stamp}@example.com`
  const nav = page.getByRole('navigation', { name: 'Primary' })

  const messagesTo = async (address: string) =>
    (await (await request.get(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${address}`)}`)).json()).messages as {
      ID: string
      Subject: string
    }[]

  // --- organizer: sign up, a type, two members ---
  await page.goto('/signup')
  await page.getByLabel('Group name').fill('E2E Notify Club')
  await page.getByLabel('Your name').fill('Ada Lovelace')
  await page.getByLabel('Email').fill(`e2e-notify-${stamp}@example.com`)
  await page.getByLabel('Password', { exact: true }).fill('correct horse battery')
  await page.getByRole('button', { name: 'Create group' }).click()
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Welcome, Ada Lovelace')

  await nav.getByRole('link', { name: 'Event types' }).click()
  await page.getByRole('button', { name: 'Create event type' }).click()
  await page.getByRole('dialog', { name: 'New event type' }).getByLabel('Name').fill('Practice')
  await page.getByRole('dialog').getByRole('button', { name: 'Save' }).click()
  await expect(page.getByRole('button', { name: /^Practice/ })).toBeVisible()

  await nav.getByRole('link', { name: 'Members' }).click()
  for (const [name, email] of [['Ana Pérez', anaEmail], ['Bob Stone', bobEmail]]) {
    await page.getByRole('button', { name: 'Add member' }).first().click()
    const form = page.getByRole('dialog', { name: 'Add member' })
    await form.getByLabel('Name').fill(name!)
    await form.getByLabel('Email').fill(email!)
    await form.getByRole('button', { name: 'Save', exact: true }).click()
    await expect(page.getByRole('button', { name: new RegExp(`^${name}`) })).toBeVisible()
  }

  // Bob pauses invitations from his own preferences link.
  await page.getByRole('button', { name: 'Actions for Bob Stone' }).click()
  await page.getByRole('menuitem', { name: 'Copy preferences link' }).click()
  const fallback = page.getByRole('dialog', { name: 'Preferences link' })
  const copied = page.getByRole('status').filter({ hasText: 'Link copied. It works for' })
  await expect(copied.or(fallback)).toBeVisible()
  const link = (await fallback.isVisible())
    ? await fallback.getByRole('textbox').inputValue()
    : await page.evaluate(() => navigator.clipboard.readText())
  const bobContext = await browser.newContext()
  const bobPage = await bobContext.newPage()
  await bobPage.goto(link)
  await bobPage.getByRole('switch', { name: 'Pause all invitations' }).click()
  await expect(bobPage.getByText('Saved')).toBeVisible()
  await bobContext.close()

  // --- an event ---
  await nav.getByRole('link', { name: 'Events' }).click()
  await page.getByRole('button', { name: 'New event' }).first().click()
  const eventDialog = page.getByRole('dialog', { name: 'New event' })
  await eventDialog.getByLabel('Title').fill('Rehearsal')
  await eventDialog.getByLabel('Type').selectOption({ label: 'Practice' })
  await eventDialog.getByLabel('Start date').fill('2099-03-10')
  await eventDialog.getByLabel('Start time').fill('10:00')
  await eventDialog.getByLabel('End date').fill('2099-03-10')
  await eventDialog.getByLabel('End time').fill('11:00')
  await eventDialog.getByRole('button', { name: 'Save' }).click()
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Rehearsal')
  const notifications = page.locator('[data-notifications]')
  await expect(notifications).toContainText('No notifications yet.')

  // --- invitation now: the audience skips the paused member ---
  await page.getByRole('button', { name: 'Schedule…' }).click()
  const schedule = page.getByRole('dialog', { name: 'Notify members' })
  await expect(schedule.getByText(/^Will be sent to 1 of 2 members\./)).toBeVisible()
  await schedule.getByRole('button', { name: 'Send now' }).click()
  await expect(page.getByRole('status')).toContainText('Sending invitation…')
  await expect(async () => {
    await page.reload()
    await expect(notifications.getByRole('listitem')).toContainText('1 recipient', { timeout: 3000 })
  }).toPass({ timeout: 90_000 })
  await expect(notifications.getByRole('listitem')).toContainText('Invitation')

  await expect.poll(async () => (await messagesTo(anaEmail)).length, { timeout: 60_000 }).toBe(1)
  expect((await messagesTo(anaEmail))[0]!.Subject).toContain('Rehearsal')
  expect(await messagesTo(bobEmail)).toHaveLength(0)

  // --- a reminder a day before, then unscheduled ---
  await page.getByRole('button', { name: 'Schedule…' }).click()
  await schedule.getByRole('radio', { name: 'Reminder' }).click()
  await schedule.getByLabel('1 day before').check()
  await schedule.getByRole('button', { name: 'Schedule', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('Reminder scheduled for')
  const reminder = notifications.getByRole('listitem').filter({ hasText: 'Reminder' })
  await expect(reminder).toContainText('Scheduled')
  await reminder.getByRole('button', { name: 'Actions for reminder' }).click()
  await page.getByRole('menuitem', { name: 'Unschedule' }).click()
  await expect(page.getByRole('status')).toContainText('Unscheduled')
  await expect(reminder).toHaveCount(0)

  // --- cancel with a message ---
  await page.getByRole('button', { name: 'More actions' }).click()
  await page.getByRole('menuitem', { name: 'Cancel event…' }).click()
  const cancel = page.getByRole('dialog', { name: "Cancel 'Rehearsal'?" })
  await expect(cancel).toContainText('Members who were notified will get a cancellation email.')
  await cancel.getByLabel('Add a message').fill('The gym is closed')
  await cancel.getByRole('button', { name: 'Cancel event' }).click()
  await expect(page.getByRole('status')).toContainText('Event cancelled')
  await expect(page.locator('[data-cancelled-banner]')).toContainText('The gym is closed')
  await expect(page.getByRole('button', { name: 'Edit' })).toHaveCount(0)

  await expect
    .poll(async () => (await messagesTo(anaEmail)).map((m) => m.Subject).filter((s) => s.startsWith('Cancelled:')).length, {
      timeout: 60_000,
    })
    .toBe(1)
  expect(await messagesTo(bobEmail)).toHaveLength(0)

  // --- a notified event can't be deleted ---
  await page.getByRole('button', { name: 'More actions' }).click()
  await page.getByRole('menuitem', { name: 'Delete' }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Delete' }).click()
  const blocked = page.getByRole('dialog', { name: "Can't delete 'Rehearsal'" })
  await expect(blocked).toContainText("it can't be deleted")
  await blocked.getByRole('button', { name: 'OK' }).click()
  await expect(blocked).toBeHidden()
})
