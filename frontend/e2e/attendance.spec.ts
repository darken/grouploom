import { test, expect } from '@playwright/test'

// Needs the backend, Postgres and Mailpit (compose.yaml starts both; inbox at http://localhost:8025).
const MAILPIT = 'http://localhost:8025'

test('members reply from their emails, the organizer sees and corrects the answers, and reminders reflect them', async ({
  page,
  request,
  browser,
}) => {
  test.setTimeout(240_000)
  const stamp = `${Date.now()}-${Math.floor(Math.random() * 1e6)}`
  const anaEmail = `ana-${stamp}@example.com`
  const bobEmail = `bob-${stamp}@example.com`
  const nav = page.getByRole('navigation', { name: 'Primary' })

  const messagesTo = async (address: string) =>
    (await (await request.get(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${address}`)}`)).json()).messages as {
      ID: string
      Subject: string
    }[]

  /** The plain-text part of the one email to `address` whose subject starts with `prefix`. */
  const textOf = async (address: string, prefix: string) => {
    let id = ''
    await expect
      .poll(async () => (id = (await messagesTo(address)).find((m) => m.Subject.startsWith(prefix))?.ID ?? ''), { timeout: 60_000 })
      .not.toBe('')
    return (await (await request.get(`${MAILPIT}/api/v1/message/${id}`)).json()).Text as string
  }

  const linkIn = (text: string, label: string) => {
    const link = new RegExp(`${label}: (\\S+)`).exec(text)?.[1]
    expect(link, `a "${label}" link in the email`).toBeTruthy()
    return link!
  }

  // --- organizer: sign up, a type, two members, an event ---
  await page.goto('/signup')
  await page.getByLabel('Group name').fill('E2E Attend Club')
  await page.getByLabel('Your name').fill('Ada Lovelace')
  await page.getByLabel('Email').fill(`e2e-attend-${stamp}@example.com`)
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

  const attendance = page.locator('[data-attendance]')
  await expect(attendance).toContainText('0 Going')
  await expect(attendance).toContainText('2 No reply')

  // --- invitation now ---
  await page.getByRole('button', { name: 'Schedule…' }).click()
  const schedule = page.getByRole('dialog', { name: 'Notify members' })
  await expect(schedule.getByText(/^Will be sent to 2 of 2 members\./)).toBeVisible()
  await schedule.getByRole('button', { name: 'Send now' }).click()
  await expect(page.getByRole('status')).toContainText('Sending invitation…')

  const anaInvite = await textOf(anaEmail, 'Rehearsal')
  const bobInvite = await textOf(bobEmail, 'Rehearsal')

  // --- Ana opens her "Going" link while signed out: preselected, nothing saved yet ---
  const anaContext = await browser.newContext()
  const anaPage = await anaContext.newPage()
  const writes: string[] = []
  anaPage.on('request', (r) => {
    if (r.method() === 'PUT') writes.push(r.url())
  })
  const pageLoaded = anaPage.waitForResponse((r) => r.url().includes('/api/public/attendance') && r.request().method() === 'GET')
  await anaPage.goto(linkIn(anaInvite, 'Going'))
  await pageLoaded
  await expect(anaPage.getByRole('heading', { level: 1 })).toHaveText('Hi Ana Pérez')
  await expect(anaPage.getByRole('radio', { name: 'Going' })).toBeChecked()
  expect(writes).toEqual([])
  await anaPage.getByRole('button', { name: 'Send reply' }).click()
  await expect(anaPage.getByText("Thanks, Ana Pérez. You're going.")).toBeVisible()
  expect(writes).toHaveLength(1)
  await anaContext.close()

  // --- Bob opens "Can't go", adds a note and sends ---
  const bobContext = await browser.newContext()
  const bobPage = await bobContext.newPage()
  await bobPage.goto(linkIn(bobInvite, "Can't go"))
  await expect(bobPage.getByRole('radio', { name: "Can't go" })).toBeChecked()
  await bobPage.getByRole('button', { name: '+ Add a note' }).click()
  await bobPage.getByLabel('Add a note for the organizer (optional)').fill('Away that week')
  await bobPage.getByRole('button', { name: 'Send reply' }).click()
  await expect(bobPage.getByText('Thanks for letting us know.')).toBeVisible()
  await bobContext.close()

  // --- organizer: the numbers and Bob's note ---
  await page.reload()
  await expect(attendance).toContainText('1 Going')
  await expect(attendance).toContainText("1 Can't go")
  await expect(attendance).toContainText('0 No reply')
  await expect(attendance.getByRole('listitem').filter({ hasText: 'Bob Stone' })).toContainText('“Away that week”')

  // --- mark Bob as going from the list ---
  await page.getByRole('button', { name: 'Actions for Bob Stone' }).click()
  await page.getByRole('menuitem', { name: 'Mark as going' }).click()
  const bobRow = attendance.getByRole('listitem').filter({ hasText: 'Bob Stone' })
  await expect(bobRow).toContainText('by organizer')
  await expect(attendance).toContainText('2 Going')

  // --- a reminder now: the audience is 2 of 2, and both emails show the answer ---
  await page.getByRole('button', { name: 'Schedule…' }).click()
  await schedule.getByRole('radio', { name: 'Reminder' }).click()
  await expect(schedule.getByText(/^Will be sent to 2 of 2 members\./)).toBeVisible()
  await schedule.getByRole('button', { name: 'Send now' }).click()
  await expect(page.getByRole('status')).toContainText('Sending reminder…')

  for (const address of [anaEmail, bobEmail]) {
    const reminder = await textOf(address, 'Reminder:')
    expect(reminder).toContain("You're going")
    expect(reminder).toContain('Change answer')
  }
})
