import { test, expect } from '@playwright/test'

test('sends a guest to the sign-in page', async ({ page }) => {
  await page.goto('/')
  await expect(page).toHaveURL(/\/login$/)
  await expect(page).toHaveTitle('Sign in · GroupLoom')
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Sign in to GroupLoom')
})
