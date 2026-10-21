import { test, expect } from '@playwright/test'

// Needs the backend (and Docker for Postgres); playwright.config.ts starts it.
test('signs up, signs out and signs in again', async ({ page }) => {
  const email = `e2e-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`
  const password = 'correct horse battery'

  await page.goto('/signup')
  await page.getByLabel('Group name').fill('E2E Club')
  await page.getByLabel('Your name').fill('Ada Lovelace')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Create group' }).click()

  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Welcome, Ada Lovelace')
  await expect(page.getByRole('main')).toContainText('E2E Club')

  // The session survives a reload thanks to the refresh cookie.
  await page.reload()
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Welcome, Ada Lovelace')

  await page.getByRole('button', { name: 'Account' }).click()
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page).toHaveURL(/\/login$/)

  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Welcome, Ada Lovelace')
})
