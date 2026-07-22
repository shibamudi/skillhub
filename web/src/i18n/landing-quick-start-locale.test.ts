import { describe, expect, it } from 'vitest'
import en from './locales/en.json'
import zh from './locales/zh.json'

describe('landing quick start locales', () => {
  it('uses localized agent setup prompts for chinese and english', () => {
    expect(zh.landing.quickStart.agent.command).toBe('阅读 https://www.example.com/registry/skill.md，并按照说明完成 SkillHub Registry 的配置')
    expect(en.landing.quickStart.agent.command).toBe('Read https://www.example.com/registry/skill.md and follow the instructions to setup SkillHub Registry')
  })

  it('provides command templates with url placeholder for dynamic rendering', () => {
    expect(zh.landing.quickStart.agent.commandTemplate).toBe('阅读 {{url}}，并按照说明完成 SkillHub Registry 的配置')
    expect(en.landing.quickStart.agent.commandTemplate).toBe('Read {{url}} and follow the instructions to setup SkillHub Registry')
  })

  it('exposes CLI install command and template in both locales', () => {
    expect(zh.landing.quickStart.tabs.cli).toBe('CLI')
    expect(zh.landing.quickStart.cli.command).toBe('npm i -g @astron-team/skillhub')
    expect(zh.landing.quickStart.cli.description).toBe('两种方式安装 SkillHub CLI，通过 --registry 参数连接你的注册中心')
    expect(zh.landing.quickStart.cli.commandTemplate).toContain('{{url}}')
    expect(zh.landing.quickStart.cli.commandTemplate).toContain('npm install -g @astron-team/skillhub')
    expect(zh.landing.quickStart.cli.commandTemplate).toContain('npx @astron-team/skillhub@latest')
    expect(zh.landing.quickStart.cli.commandTemplate).toContain('--registry {{url}}')
    expect(en.landing.quickStart.tabs.cli).toBe('CLI')
    expect(en.landing.quickStart.cli.command).toBe('npm i -g @astron-team/skillhub')
    expect(en.landing.quickStart.cli.description).toBe('Two ways to install SkillHub CLI, connect to your registry via --registry')
    expect(en.landing.quickStart.cli.commandTemplate).toContain('{{url}}')
    expect(en.landing.quickStart.cli.commandTemplate).toContain('npm install -g @astron-team/skillhub')
    expect(en.landing.quickStart.cli.commandTemplate).toContain('npx @astron-team/skillhub@latest')
    expect(en.landing.quickStart.cli.commandTemplate).toContain('--registry {{url}}')
  })

  it('exposes human (ClawHub) command template with registry setup in both locales', () => {
    expect(zh.landing.quickStart.human.commandTemplate).toContain('{{url}}')
    expect(zh.landing.quickStart.human.commandTemplate).toContain('CLAWHUB_REGISTRY')
    expect(zh.landing.quickStart.human.commandTemplate).toContain('clawhub search')
    expect(zh.landing.quickStart.human.commandTemplate).toContain('clawhub install')
    expect(en.landing.quickStart.human.commandTemplate).toContain('{{url}}')
    expect(en.landing.quickStart.human.commandTemplate).toContain('CLAWHUB_REGISTRY')
    expect(en.landing.quickStart.human.commandTemplate).toContain('clawhub search')
    expect(en.landing.quickStart.human.commandTemplate).toContain('clawhub install')
  })
})
