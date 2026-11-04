import { test, expect } from '@playwright/test'

// The browser language drives everything: detection, <html lang>, the catalog and the Japanese font stack.
// Overrides the en-US default from playwright.config.ts for this file only.
test.use({ locale: 'ja-JP' })

test('shows the sign-in page in Japanese with the Japanese font stack', async ({ page }) => {
  await page.goto('/login')

  await expect(page.locator('html')).toHaveAttribute('lang', 'ja')
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('GroupLoomにログイン')
  await expect(page.getByRole('button', { name: 'ログイン' })).toBeVisible()
  await expect(page).toHaveTitle('ログイン · GroupLoom')

  const fontFamily = await page.locator('html').evaluate((el) => getComputedStyle(el).fontFamily)
  expect(fontFamily).toContain('Hiragino Sans')
  // The Latin fonts stay first, so English words inside Japanese text keep their look.
  expect(fontFamily.indexOf('Inter')).toBeLessThan(fontFamily.indexOf('Hiragino Sans'))
})
