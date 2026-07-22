import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'

test.describe('Landing Quick Start CLI Tab (Real API)', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('renders three peer tabs and exposes the CLI install commands', async ({ page }) => {
    await page.goto('/')

    const agentTab = page.getByRole('button', { name: 'I am Agent', exact: true })
    const humanTab = page.getByRole('button', { name: 'I am Human', exact: true })
    const cliTab = page.getByRole('button', { name: 'CLI', exact: true })

    await expect(agentTab).toBeVisible()
    await expect(humanTab).toBeVisible()
    await expect(cliTab).toBeVisible()

    await expect(agentTab).toHaveAttribute('aria-pressed', 'true')

    await cliTab.click()
    await expect(cliTab).toHaveAttribute('aria-pressed', 'true')
    await expect(agentTab).toHaveAttribute('aria-pressed', 'false')
    await expect(humanTab).toHaveAttribute('aria-pressed', 'false')

    await expect(
      page.getByText('Two ways to install SkillHub CLI, connect to your registry via --registry'),
    ).toBeVisible()
    await expect(page.getByText('npm install -g @astron-team/skillhub')).toBeVisible()
    await expect(page.getByText('skillhub search email --registry')).toBeVisible()
    await expect(page.getByText('npx @astron-team/skillhub@latest')).toBeVisible()
    await expect(page.getByText('Full guide →')).toBeVisible()
  })

  test('agent and human tabs expose their commands', async ({ page }) => {
    await page.goto('/')

    const agentTab = page.getByRole('button', { name: 'I am Agent', exact: true })
    const humanTab = page.getByRole('button', { name: 'I am Human', exact: true })

    await expect(
      page.getByText(/Read .+\/registry\/skill\.md and follow the instructions/),
    ).toBeVisible()

    await humanTab.click()
    await expect(humanTab).toHaveAttribute('aria-pressed', 'true')
    await expect(page.getByText('export CLAWHUB_REGISTRY=')).toBeVisible()
    await expect(page.getByText('clawhub search email')).toBeVisible()
    await expect(page.getByText('clawhub install my-skill')).toBeVisible()

    await agentTab.click()
    await expect(agentTab).toHaveAttribute('aria-pressed', 'true')
  })
})
