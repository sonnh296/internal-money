import type { Page } from '@playwright/test'
import { e2eEnv } from './env'

export async function loginCustomer(page: Page, email: string, password: string): Promise<void> {
  await page.goto('/app/login')
  await page.getByRole('heading', { name: 'Đăng nhập' }).waitFor()
  await page.locator('label:has-text("Email") input').fill(email)
  await page.locator('label:has-text("Mật khẩu") input').fill(password)
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
  await page.waitForURL('**/app/dashboard', { timeout: 30_000 })
}

export async function loginAdmin(page: Page, email: string, password: string): Promise<void> {
  await page.goto('/admin/login')
  await page.getByRole('heading', { name: 'Đăng nhập Admin' }).waitFor()
  await page.locator('label:has-text("Email") input').fill(email)
  await page.locator('label:has-text("Password") input').fill(password)
  await page.getByRole('button', { name: 'Đăng nhập admin' }).click()
  await page.waitForURL('**/admin/dashboard', { timeout: 30_000 })
}

export async function logoutCustomer(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Đăng xuất' }).click()
  await page.waitForURL('**/app/login')
}

export async function goToNav(page: Page, label: string): Promise<void> {
  await page.getByRole('navigation', { name: 'App nav' }).getByRole('link', { name: label }).click()
}

export async function waitForToast(page: Page, titlePart: string): Promise<void> {
  await page.locator('.toast').filter({ hasText: titlePart }).first().waitFor({ timeout: 15_000 })
}

export async function fillTransferForm(
  page: Page,
  toAccountNumber: string,
  amount: number,
  reason = 'E2E transfer'
): Promise<void> {
  await page.locator('label:has-text("Số tài khoản người nhận") input').fill(toAccountNumber)
  await page.waitForTimeout(1_200)
  const moneyInput = page.locator('label:has-text("Số tiền") input')
  await moneyInput.click()
  await moneyInput.fill('')
  await moneyInput.fill(String(amount))
  await moneyInput.blur()
  await page.locator('label:has-text("Lý do") input').fill(reason)
}

export function annotateTc(testInfo: { annotations: { type: string; description?: string }[] }, tcId: string): void {
  testInfo.annotations.push({ type: 'tc-id', description: tcId })
}

export { e2eEnv }
