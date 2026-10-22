import { test, expect } from '@playwright/test'

// Needs the backend (and Docker for Postgres); playwright.config.ts starts it.
test('manages event types: create, rename, archive, unarchive, delete', async ({ page }) => {
  const email = `e2e-et-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`

  await page.goto('/signup')
  await page.getByLabel('Group name').fill('E2E Club')
  await page.getByLabel('Your name').fill('Ada Lovelace')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password', { exact: true }).fill('correct horse battery')
  await page.getByRole('button', { name: 'Create group' }).click()
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Welcome, Ada Lovelace')

  await page.getByRole('navigation', { name: 'Primary' }).getByRole('link', { name: 'Event types' }).click()
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Event types')
  await expect(page.getByText('No event types yet')).toBeVisible()

  // Create
  await page.getByRole('button', { name: 'Create event type' }).click()
  const dialog = page.getByRole('dialog', { name: 'New event type' })
  await dialog.getByLabel('Name').fill('Practice')
  await dialog.getByRole('radio', { name: 'Green' }).click()
  await dialog.getByRole('button', { name: 'Save' }).click()
  await expect(page.getByRole('status')).toContainText('Event type created')
  await expect(page.getByRole('button', { name: /^Practice/ })).toBeVisible()

  // Duplicate name is rejected under the field
  await page.getByRole('button', { name: 'New event type' }).click()
  await page.getByRole('dialog').getByLabel('Name').fill('practice')
  await page.getByRole('dialog').getByRole('button', { name: 'Save' }).click()
  await expect(page.getByRole('dialog')).toContainText('An event type with this name already exists.')
  await page.getByRole('dialog').getByRole('button', { name: 'Cancel' }).click()

  // Rename
  await page.getByRole('button', { name: /^Practice/ }).click()
  await page.getByRole('dialog', { name: 'Edit event type' }).getByLabel('Name').fill('Rehearsal')
  await page.getByRole('dialog').getByRole('button', { name: 'Save' }).click()
  await expect(page.getByRole('button', { name: /^Rehearsal/ })).toBeVisible()

  // Archive: leaves the Active list, appears under Archived
  await page.getByRole('button', { name: 'Actions for Rehearsal' }).click()
  await page.getByRole('menuitem', { name: 'Archive' }).click()
  await expect(page.getByText('No event types yet')).toBeVisible()
  await page.getByRole('radio', { name: 'Archived' }).click()
  await expect(page).toHaveURL(/archived=1/)
  await expect(page.getByRole('button', { name: /^Rehearsal/ })).toBeVisible()

  // Unarchive
  await page.getByRole('button', { name: 'Actions for Rehearsal' }).click()
  await page.getByRole('menuitem', { name: 'Unarchive' }).click()
  await expect(page.getByText('No archived event types.')).toBeVisible()
  await page.getByRole('radio', { name: 'Active' }).click()
  await expect(page.getByRole('button', { name: /^Rehearsal/ })).toBeVisible()

  // Delete needs confirmation
  await page.getByRole('button', { name: 'Actions for Rehearsal' }).click()
  await page.getByRole('menuitem', { name: 'Delete' }).click()
  const confirm = page.getByRole('dialog', { name: "Delete 'Rehearsal'?" })
  await expect(confirm).toBeVisible()
  await confirm.getByRole('button', { name: 'Delete' }).click()
  await expect(page.getByText('No event types yet')).toBeVisible()
})
