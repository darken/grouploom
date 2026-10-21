import { test, expect } from '@playwright/test'

// Playwright starts only Vite, not the backend, so this checks the shell alone.
test('renders the app shell and welcome heading', async ({ page }) => {
  await page.goto('/')
  await expect(page).toHaveTitle('Home · GroupLoom')
  await expect(page.getByRole('banner')).toContainText('GroupLoom')
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Welcome to GroupLoom')
  await expect(page.getByRole('main')).toBeVisible()
})
