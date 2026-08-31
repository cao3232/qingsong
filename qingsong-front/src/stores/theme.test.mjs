import assert from 'node:assert/strict'
import test from 'node:test'
import { createPinia, setActivePinia } from 'pinia'
import { useThemeStore } from './theme.js'

test('行内代码背景配置覆盖当前聊天皮肤的默认值', async () => {
  const cssVariables = new Map()
  globalThis.document = {
    documentElement: {
      style: {
        setProperty: (property, value) => cssVariables.set(property, value)
      }
    }
  }
  globalThis.localStorage = {
    getItem: () => null,
    setItem: () => {}
  }

  setActivePinia(createPinia())
  const themeStore = useThemeStore()
  themeStore.init()
  themeStore.config.inlineCodeBackground = '#123456'

  await new Promise(resolve => setTimeout(resolve, 150))

  assert.equal(cssVariables.get('--chat-inline-code-bg'), '#123456')
})

test('切换皮肤不覆盖用户选择的页面背景，切回跟随型皮肤时还原', async () => {
  const cssVariables = new Map()
  globalThis.document = {
    documentElement: {
      style: {
        setProperty: (property, value) => cssVariables.set(property, value)
      }
    }
  }
  globalThis.localStorage = {
    getItem: () => null,
    setItem: () => {}
  }

  setActivePinia(createPinia())
  const themeStore = useThemeStore()
  themeStore.init()

  // 用户自己在页面外观里选的背景
  themeStore.config.pageBackground = '#123456'

  // 切到强制背景的皮肤（retro 固定 #c0c0c0）：生效背景用皮肤的，用户选择不被改写
  themeStore.setChatSkin('retro')
  assert.equal(themeStore.config.pageBackground, '#123456')
  assert.equal(themeStore.effectivePageBackground, '#c0c0c0')

  // 切回跟随型皮肤（modern-light followPageBackground）：生效背景还原为用户选择
  themeStore.setChatSkin('modern-light')
  assert.equal(themeStore.effectivePageBackground, '#123456')

  await new Promise(resolve => setTimeout(resolve, 150))
  assert.equal(cssVariables.get('--app-background'), '#123456')
})

test('切换暗色皮肤时面板随明暗智能匹配，首页卡片不再白字白底', async () => {
  const cssVariables = new Map()
  globalThis.document = {
    documentElement: {
      style: {
        setProperty: (property, value) => cssVariables.set(property, value)
      }
    }
  }
  globalThis.localStorage = {
    getItem: () => null,
    setItem: () => {}
  }

  setActivePinia(createPinia())
  const themeStore = useThemeStore()
  themeStore.init()

  // 浅色皮肤 → 暗色皮肤：面板必须从白玻璃切到深色玻璃，文字切浅色
  themeStore.setChatSkin('modern-dark')
  assert.equal(themeStore.effectiveIsDark, true)
  assert.equal(themeStore.config.panelBackground, 'rgba(0, 0, 0, 0.3)')

  await new Promise(resolve => setTimeout(resolve, 150))
  assert.equal(cssVariables.get('--app-text-primary'), '#f8f9fa')
  assert.equal(cssVariables.get('--app-panel-background-rgb'), '0, 0, 0')
  assert.equal(cssVariables.get('--app-background'), '#101717')

  // 切回浅色皮肤：面板/文字还原
  themeStore.setChatSkin('retro')
  assert.equal(themeStore.effectiveIsDark, false)
  assert.equal(themeStore.config.panelBackground, 'rgba(255, 255, 255, 0.5)')
})

test('自定义页面背景的明暗按亮度推导（effectiveIsDark），无需手动同步 isThemeDark', () => {
  globalThis.document = {
    documentElement: { style: { setProperty: () => {} } }
  }
  globalThis.localStorage = {
    getItem: () => null,
    setItem: () => {}
  }

  setActivePinia(createPinia())
  const themeStore = useThemeStore()

  // 跟随型皮肤下，页面背景取用户自选
  themeStore.config.chatSkin = 'modern-light'

  // 自定义浅色 → 深色文字
  themeStore.config.pageBackground = '#ffffff'
  assert.equal(themeStore.effectiveIsDark, false)

  // 自定义深色（非预设值，走亮度推导）→ 浅色文字
  themeStore.config.pageBackground = '#123456'
  assert.equal(themeStore.effectiveIsDark, true)

  // 命中预设时用预设的 isDark 标志（与对比度审计口径一致）
  themeStore.config.pageBackground = '#f8f9fa' // 浅灰色预设
  assert.equal(themeStore.effectiveIsDark, false)
  themeStore.config.pageBackground = '#1a1a2e' // 午夜黑预设
  assert.equal(themeStore.effectiveIsDark, true)

  // 固定背景皮肤（retro）始终以皮肤 isDark 为准，与用户背景无关
  themeStore.config.chatSkin = 'retro'
  themeStore.config.pageBackground = '#123456'
  assert.equal(themeStore.effectiveIsDark, false)
})

test('固定背景皮肤激活时再选浅色背景：面板仍按皮肤深色匹配，不产生白字白底冲突', () => {
  const cssVariables = new Map()
  globalThis.document = {
    documentElement: {
      style: {
        setProperty: (property, value) => cssVariables.set(property, value)
      }
    }
  }
  globalThis.localStorage = {
    getItem: () => null,
    setItem: () => {}
  }

  setActivePinia(createPinia())
  const themeStore = useThemeStore()
  themeStore.init()

  // 1) 切到暗色皮肤：深色生效背景 + 深色玻璃面板
  themeStore.setChatSkin('modern-dark')
  assert.equal(themeStore.effectiveIsDark, true)
  assert.equal(themeStore.config.panelBackground, 'rgba(0, 0, 0, 0.3)')

  // 2) 模拟 selectBackground 点浅色预设（纯白色）：背景选择被存储，但生效背景/明暗/面板都由皮肤接管
  themeStore.config.pageBackground = '#ffffff'
  themeStore.matchPanelToTheme()
  assert.equal(themeStore.effectivePageBackground, '#101717') // 皮肤固定背景仍然生效
  assert.equal(themeStore.effectiveIsDark, true)
  assert.equal(themeStore.config.panelBackground, 'rgba(0, 0, 0, 0.3)') // 不得被切成白玻璃

  // 3) 切回跟随型皮肤：存储的浅色背景还原，面板随生效明暗转浅
  themeStore.setChatSkin('modern-light')
  assert.equal(themeStore.effectivePageBackground, '#ffffff')
  assert.equal(themeStore.effectiveIsDark, false)
  assert.equal(themeStore.config.panelBackground, 'rgba(255, 255, 255, 0.5)')
})

test('跟随型皮肤 + 深色用户背景：setChatSkin 后面板按推导的生效明暗匹配', () => {
  globalThis.document = {
    documentElement: { style: { setProperty: () => {} } }
  }
  globalThis.localStorage = {
    getItem: () => null,
    setItem: () => {}
  }

  setActivePinia(createPinia())
  const themeStore = useThemeStore()

  // 用户背景为深色自定义色时切到跟随型皮肤：
  // 生效明暗由用户背景推导为深色（而非 skin.isDark=false），面板必须是深色玻璃
  themeStore.config.pageBackground = '#123456'
  themeStore.setChatSkin('modern-light')
  assert.equal(themeStore.effectiveIsDark, true)
  assert.equal(themeStore.config.panelBackground, 'rgba(0, 0, 0, 0.3)')
})
