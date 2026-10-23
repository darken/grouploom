import { test, expect } from '@playwright/test'

// Needs the backend (and Docker for Postgres); playwright.config.ts starts it.
test('manages members: add two, search, duplicate email, edit, remove', async ({ page }) => {
  const email = `e2e-m-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`

  await page.goto('/signup')
  await page.getByLabel('Group name').fill('E2E Club')
  await page.getByLabel('Your name').fill('Ada Lovelace')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password', { exact: true }).fill('correct horse battery')
  await page.getByRole('button', { name: 'Create group' }).click()
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Welcome, Ada Lovelace')

  await page.getByRole('navigation', { name: 'Primary' }).getByRole('link', { name: 'Members' }).click()
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Members')
  await expect(page.getByText('No members yet.')).toBeVisible()

  // Add two members without closing the dialog
  await page.getByRole('button', { name: 'Add member' }).first().click()
  const dialog = page.getByRole('dialog', { name: 'Add member' })
  await dialog.getByLabel('Name').fill('Ana Pérez')
  await dialog.getByLabel('Email').fill('ana@example.com')
  await dialog.getByRole('button', { name: 'Save and add another' }).click()
  await expect(page.getByRole('status')).toContainText('Ana Pérez added')
  await expect(dialog.getByLabel('Name')).toBeFocused()
  await expect(dialog.getByLabel('Name')).toHaveValue('')
  await dialog.getByLabel('Name').fill('Bob Smith')
  await dialog.getByLabel('Email').fill('bob@example.com')
  await dialog.getByRole('button', { name: 'Save', exact: true }).click()
  await expect(dialog).toBeHidden()
  await expect(page.getByText('2 members')).toBeVisible()

  // Search narrows the list and lands in the URL
  await page.getByRole('searchbox', { name: 'Search members' }).fill('bob')
  await expect(page).toHaveURL(/q=bob/)
  await expect(page.getByRole('button', { name: /^Bob Smith/ })).toBeVisible()
  await expect(page.getByRole('button', { name: /^Ana Pérez/ })).toHaveCount(0)
  await page.getByRole('button', { name: 'Clear search' }).click()
  await expect(page.getByRole('button', { name: /^Ana Pérez/ })).toBeVisible()

  // A duplicate email (any case) is rejected under the field
  await page.getByRole('button', { name: 'Add member' }).click()
  await page.getByRole('dialog').getByLabel('Name').fill('Another Ana')
  await page.getByRole('dialog').getByLabel('Email').fill('ANA@example.com')
  await page.getByRole('dialog').getByRole('button', { name: 'Save', exact: true }).click()
  await expect(page.getByRole('dialog')).toContainText('A member with this email already exists.')
  await page.getByRole('dialog').getByRole('button', { name: 'Cancel' }).click()

  // Edit
  await page.getByRole('button', { name: /^Ana Pérez/ }).click()
  await page.getByRole('dialog', { name: 'Edit member' }).getByLabel('Name').fill('Ana María Pérez')
  await page.getByRole('dialog').getByRole('button', { name: 'Save', exact: true }).click()
  await expect(page.getByRole('button', { name: /^Ana María Pérez/ })).toBeVisible()

  // Remove needs confirmation
  await page.getByRole('button', { name: 'Actions for Bob Smith' }).click()
  await page.getByRole('menuitem', { name: 'Remove' }).click()
  const confirm = page.getByRole('dialog', { name: 'Remove Bob Smith?' })
  await expect(confirm).toBeVisible()
  await confirm.getByRole('button', { name: 'Remove' }).click()
  await expect(page.getByRole('button', { name: /^Bob Smith/ })).toHaveCount(0)

  await page.getByRole('button', { name: 'Actions for Ana María Pérez' }).click()
  await page.getByRole('menuitem', { name: 'Remove' }).click()
  await page.getByRole('dialog', { name: 'Remove Ana María Pérez?' }).getByRole('button', { name: 'Remove' }).click()
  await expect(page.getByText('No members yet.')).toBeVisible()
})
