import { test, expect } from '@playwright/test'

// Needs the backend (and Docker for Postgres); playwright.config.ts starts it.
test('schedules events: create with an auto-filled end, find, edit, archive-instead, delete', async ({ page }) => {
  const email = `e2e-ev-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`
  const nav = page.getByRole('navigation', { name: 'Primary' })

  await page.goto('/signup')
  await page.getByLabel('Group name').fill('E2E Club')
  await page.getByLabel('Your name').fill('Ada Lovelace')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password', { exact: true }).fill('correct horse battery')
  await page.getByRole('button', { name: 'Create group' }).click()
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Welcome, Ada Lovelace')
  await expect(page.getByText('Nothing scheduled')).toBeVisible()

  // An event type with a 90-minute default duration
  await nav.getByRole('link', { name: 'Event types' }).click()
  await page.getByRole('button', { name: 'Create event type' }).click()
  const typeDialog = page.getByRole('dialog', { name: 'New event type' })
  await typeDialog.getByLabel('Name').fill('Practice')
  await typeDialog.getByLabel('Default duration').selectOption({ label: '1.5 hr' })
  await typeDialog.getByRole('button', { name: 'Save' }).click()
  await expect(page.getByRole('button', { name: /^Practice/ })).toBeVisible()

  // Create an event: choosing the type fills the end 90 minutes after the start
  await nav.getByRole('link', { name: 'Events' }).click()
  await expect(page.getByText('Nothing scheduled.')).toBeVisible()
  await page.getByRole('button', { name: 'New event' }).first().click()
  const dialog = page.getByRole('dialog', { name: 'New event' })
  await dialog.getByLabel('Title').fill('Rehearsal')
  await dialog.getByLabel('Start date').fill('2099-03-10')
  await dialog.getByLabel('Start time').fill('10:00')
  await dialog.getByLabel('Type').selectOption({ label: 'Practice' })
  // The type's 90-minute default fills the end
  await expect(dialog.getByLabel('End date')).toHaveValue('2099-03-10')
  await expect(dialog.getByLabel('End time')).toHaveValue('11:30')
  await dialog.getByLabel('Location').fill('Main gym')
  await dialog.getByRole('button', { name: 'Save' }).click()

  // It opens the detail page
  await expect(page.getByRole('status')).toContainText('Event created')
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Rehearsal')
  await expect(page.getByText('Main gym')).toBeVisible()
  await expect(page).toHaveURL(/\/events\/[0-9a-f-]+/)

  // And shows under Upcoming in the right day section
  await page.getByRole('link', { name: 'Events' }).first().click()
  await expect(page.getByRole('heading', { level: 2, name: /Mar 10, 2099/ })).toBeVisible()
  await expect(page.getByRole('link', { name: /Rehearsal/ })).toBeVisible()

  // Edit the title
  await page.getByRole('link', { name: /Rehearsal/ }).click()
  await page.getByRole('button', { name: 'Edit' }).click()
  await page.getByRole('dialog', { name: 'Edit event' }).getByLabel('Title').fill('Dress rehearsal')
  await page.getByRole('dialog').getByRole('button', { name: 'Save' }).click()
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Dress rehearsal')

  // The event type is in use: delete is refused and archiving is offered
  await nav.getByRole('link', { name: 'Event types' }).click()
  await page.getByRole('button', { name: 'Actions for Practice' }).click()
  await page.getByRole('menuitem', { name: 'Delete' }).click()
  await page.getByRole('dialog', { name: "Delete 'Practice'?" }).getByRole('button', { name: 'Delete' }).click()
  const archive = page.getByRole('dialog', { name: "Can't delete 'Practice'" })
  await expect(archive).toContainText('Archive it instead?')
  await archive.getByRole('button', { name: 'Archive' }).click()
  await expect(page.getByText('No event types yet')).toBeVisible()

  // Delete the event
  await nav.getByRole('link', { name: 'Events' }).click()
  await page.getByRole('link', { name: /Dress rehearsal/ }).click()
  await page.getByRole('button', { name: 'More actions' }).click()
  await page.getByRole('menuitem', { name: 'Delete' }).click()
  await page.getByRole('dialog', { name: "Delete 'Dress rehearsal'?" }).getByRole('button', { name: 'Delete' }).click()
  await expect(page.getByText('Nothing scheduled.')).toBeVisible()
})
