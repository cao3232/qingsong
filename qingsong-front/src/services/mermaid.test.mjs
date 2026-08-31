import assert from 'node:assert/strict'
import test from 'node:test'
import { createPinia, setActivePinia } from 'pinia'

import { resolveMermaidTheme, applyMermaidConfig } from './mermaid.js'
import { useThemeStore } from '../stores/theme.js'

// resolveMermaidTheme：'auto' 跟随页面/皮肤明暗，显式主题原样透传
test('resolveMermaidTheme: auto 在暗色下映射为 dark', () => {
  assert.equal(resolveMermaidTheme('auto', true), 'dark')
})

test('resolveMermaidTheme: auto 在亮色下映射为 neutral', () => {
  assert.equal(resolveMermaidTheme('auto', false), 'neutral')
})

test('resolveMermaidTheme: 显式主题不受明暗影响', () => {
  assert.equal(resolveMermaidTheme('forest', true), 'forest')
  assert.equal(resolveMermaidTheme('base', false), 'base')
})

test('resolveMermaidTheme: 空值兜底为 auto 逻辑', () => {
  assert.equal(resolveMermaidTheme(undefined, false), 'neutral')
  assert.equal(resolveMermaidTheme(null, true), 'dark')
})

// applyMermaidConfig：从 themeStore 读取配置并幂等 initialize
// 注意：明暗取 themeStore.effectiveIsDark（皮肤固定背景用皮肤 isDark，自定义背景按亮度推导），
// 不再读可独立改写的 config.isThemeDark，因此测试通过切皮肤表达明暗。
test('applyMermaidConfig: 按当前用户配置初始化 mermaid', () => {
  setActivePinia(createPinia())
  const themeStore = useThemeStore()
  themeStore.config.mermaidTheme = 'auto'
  themeStore.config.chatSkin = 'modern-dark' // 暗色皮肤 → effectiveIsDark = true
  themeStore.config.mermaidLook = 'handDrawn'

  let captured = null
  const fakeMermaid = { initialize: (cfg) => { captured = cfg } }
  applyMermaidConfig(fakeMermaid)

  assert.equal(captured.theme, 'dark')
  assert.equal(captured.look, 'handDrawn')
  assert.equal(captured.startOnLoad, false)
  assert.equal(captured.securityLevel, 'strict')
  assert.equal(captured.suppressErrorRendering, true)
  assert.equal(captured.fontFamily, 'var(--code-font-family, system-ui)')
})

test('applyMermaidConfig: 显式主题 + 亮色皮肤映射 neutral', () => {
  setActivePinia(createPinia())
  const themeStore = useThemeStore()
  themeStore.config.mermaidTheme = 'auto'
  themeStore.config.chatSkin = 'retro' // 亮色皮肤（默认）→ effectiveIsDark = false
  themeStore.config.mermaidLook = 'classic'

  let captured = null
  applyMermaidConfig({ initialize: (cfg) => { captured = cfg } })

  assert.equal(captured.theme, 'neutral')
  assert.equal(captured.look, 'classic')
})

test('applyMermaidConfig: overrides 优先于用户配置（GraphRunner 固定外观）', () => {
  setActivePinia(createPinia())
  const themeStore = useThemeStore()
  themeStore.config.mermaidTheme = 'forest'
  themeStore.config.mermaidLook = 'handDrawn'

  let captured = null
  applyMermaidConfig(
    { initialize: (cfg) => { captured = cfg } },
    { theme: 'neutral', look: 'classic', fontFamily: 'system-ui, sans-serif' }
  )

  assert.equal(captured.theme, 'neutral')
  assert.equal(captured.look, 'classic')
  assert.equal(captured.fontFamily, 'system-ui, sans-serif')
})

test('applyMermaidConfig: 非法 look 值兜底为 classic', () => {
  setActivePinia(createPinia())
  const themeStore = useThemeStore()
  themeStore.config.mermaidLook = 'sketchy'

  let captured = null
  applyMermaidConfig({ initialize: (cfg) => { captured = cfg } })

  assert.equal(captured.look, 'classic')
})

test('themeStore 默认配置包含 mermaid 项', () => {
  setActivePinia(createPinia())
  const themeStore = useThemeStore()
  assert.equal(themeStore.config.mermaidTheme, 'auto')
  assert.equal(themeStore.config.mermaidLook, 'classic')
})
