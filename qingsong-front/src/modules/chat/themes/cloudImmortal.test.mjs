import assert from 'node:assert/strict'
import { existsSync, readFileSync, statSync } from 'node:fs'
import test from 'node:test'

const themeStoreSource = readFileSync(
  new URL('../../../stores/theme.js', import.meta.url),
  'utf8'
)
const chatPageSource = readFileSync(
  new URL('../pages/AIChatPage.vue', import.meta.url),
  'utf8'
)
const chatHeaderSource = readFileSync(
  new URL('../components/ChatWorkspaceHeader.vue', import.meta.url),
  'utf8'
)
const themeStyleUrl = new URL('./cloudImmortal.scss', import.meta.url)

const extractObjectAt = (source, objectStart) => {
  let depth = 0
  let quote = ''
  let escaped = false

  for (let index = objectStart; index < source.length; index += 1) {
    const character = source[index]
    if (quote) {
      if (escaped) escaped = false
      else if (character === '\\') escaped = true
      else if (character === quote) quote = ''
      continue
    }
    if (character === "'" || character === '"' || character === '`') {
      quote = character
      continue
    }
    if (character === '{') depth += 1
    if (character === '}') {
      depth -= 1
      if (depth === 0) return source.slice(objectStart, index + 1)
    }
  }
  throw new Error('未找到完整对象')
}

const extractPreset = name => {
  const nameIndex = themeStoreSource.indexOf(`name: '${name}'`)
  assert.notEqual(nameIndex, -1, `未找到 ${name} preset`)
  const objectStart = themeStoreSource.lastIndexOf('{', nameIndex)
  return extractObjectAt(themeStoreSource, objectStart)
}

const extractTokens = presetSource => {
  const varsIndex = presetSource.indexOf('vars:')
  assert.notEqual(varsIndex, -1, 'preset 缺少 vars')
  const varsStart = presetSource.indexOf('{', varsIndex)
  return [...extractObjectAt(presetSource, varsStart).matchAll(/['"](--chat-[^'"]+)['"]\s*:/g)]
    .map(match => match[1])
    .sort()
}

const assetNames = [
  'cloud-mountains-desktop.webp',
  'cloud-mountains-mobile.webp',
  'mist-far.webp',
  'mist-near.webp',
  'jade-texture.webp',
  'paper-texture.webp'
]

test('主题 store 注册云海仙门完整皮肤', () => {
  const cloudPreset = extractPreset('cloud-immortal')
  assert.match(cloudPreset, /label:\s*['"]云海仙门['"]/) 
  assert.match(cloudPreset, /isDark:\s*false/)
  assert.deepEqual(extractTokens(cloudPreset), extractTokens(extractPreset('retro')))
})

test('聊天页仅在云海仙门主题启用装饰层', () => {
  assert.match(chatPageSource, /chat-skin-cloud-immortal/)
  assert.match(chatPageSource, /isCloudImmortal/)
  assert.match(chatPageSource, /v-if="isCloudImmortal"[\s\S]*?class="immortal-scene"/)
  assert.match(chatPageSource, /aria-hidden="true"/)
  assert.match(chatPageSource, /cloudImmortal\.scss/)
})

test('云海仙门位图资产完整且非空', () => {
  for (const name of assetNames) {
    const assetUrl = new URL(`../../../assets/chat-themes/cloud-immortal/${name}`, import.meta.url)
    assert.equal(existsSync(assetUrl), true, `${name} 不存在`)
    assert.ok(statSync(assetUrl).size > 1024, `${name} 文件过小`)
  }
})

test('主题样式限定作用域并提供响应式和减少动效规则', () => {
  assert.equal(existsSync(themeStyleUrl), true)
  const source = readFileSync(themeStyleUrl, 'utf8')

  assert.match(source, /\.chat-skin-cloud-immortal/)
  assert.match(source, /cloud-mountains-desktop\.webp/)
  assert.match(source, /cloud-mountains-mobile\.webp/)
  assert.match(source, /\.ai-chat-view\.chat-skin-cloud-immortal\s*\{[\s\S]*?cloud-mountains-desktop\.webp/)
  assert.match(source, /@media\s*\(max-width:\s*768px\)[\s\S]*?\.ai-chat-view\.chat-skin-cloud-immortal\s*\{[\s\S]*?cloud-mountains-mobile\.webp/)
  assert.match(source, /background-blend-mode:/)
  assert.match(source, /\.right-actions\.send-feedback::after[\s\S]*?pointer-events:\s*none/)
  assert.match(source, /@media\s*\(max-width:\s*768px\)/)
  assert.match(source, /@media\s*\(prefers-reduced-motion:\s*reduce\)/)
  assert.match(source, /translate3d/)
})

test('正统仙门主题固定头像并提供高对比下拉状态', () => {
  const source = readFileSync(themeStyleUrl, 'utf8')

  assert.match(source, /\.chat-skin-cloud-immortal\s+\.message-avatar\s*\{[\s\S]*?flex:\s*0\s+0\s+44px[\s\S]*?width:\s*44px[\s\S]*?min-width:\s*44px[\s\S]*?align-self:\s*flex-start/)
  assert.match(source, /\.chat-skin-cloud-immortal\s+\.message-avatar\s*>\s*\.avatar-container\s*\{[\s\S]*?width:\s*44px[\s\S]*?height:\s*44px[\s\S]*?min-width:\s*44px[\s\S]*?min-height:\s*44px[\s\S]*?max-width:\s*44px[\s\S]*?max-height:\s*44px[\s\S]*?aspect-ratio:\s*1[\s\S]*?overflow:\s*hidden/)
  assert.match(source, /\.chat-skin-cloud-immortal\s+\.avatar-container\s*>\s*\.avatar-image\s*\{[\s\S]*?width:\s*100%[\s\S]*?height:\s*100%[\s\S]*?object-fit:\s*cover[\s\S]*?display:\s*block/)

  assert.match(source, /\.chat-skin-cloud-immortal\s+\.chat-header\s+\.model-select\s*\{[\s\S]*?background:\s*#(?:173c42|244e55)[\s\S]*?color:\s*#f8f1d7[\s\S]*?border:\s*1px\s+solid\s+#e2cf91/)
  assert.match(source, /\.chat-skin-cloud-immortal\s+\.chat-header\s+\.model-select:disabled\s*\{[\s\S]*?background:[\s\S]*?color:/)
  assert.match(source, /\.chat-skin-cloud-immortal\s+\.chat-header\s+\.model-select\s+option\s*\{[\s\S]*?background:\s*#173c42[\s\S]*?color:\s*#f8f1d7/)
  assert.match(source, /\.model-select\s+option:(?:checked|hover)[\s\S]*?background:\s*#e2cf91[\s\S]*?color:\s*#173c42/)

  assert.match(chatPageSource, /document\.body\.setAttribute\(['"]data-chat-skin['"],\s*['"]cloud-immortal['"]\)/)
  assert.match(chatHeaderSource, /<NPopover[\s\S]*?class="chat-workspace-popover"[\s\S]*?content-class="chat-workspace-popover-content"/)
  assert.match(source, /body\[data-chat-skin=['"]cloud-immortal['"]\]\s+\.n-popover\.chat-workspace-popover[\s\S]*?--n-color:\s*#173c42[\s\S]*?--n-text-color:\s*#f8f1d7[\s\S]*?border:\s*1px\s+solid\s+#e2cf91[\s\S]*?box-shadow:/)
  assert.match(source, /body\[data-chat-skin=['"]cloud-immortal['"]\]\s+\.chat-workspace-popover-content/)
  assert.doesNotMatch(source, /body(?:\.[\w-]+|\[[^\]]+\])[\s\S]{0,160}\.n-dropdown-menu/)
  assert.doesNotMatch(source, /body(?:\.[\w-]+|\[[^\]]+\])[\s\S]{0,160}\.n-base-select-menu/)
})

test('正统仙门装饰仅在云海主题内渲染并适配移动端和减少动效', () => {
  const source = readFileSync(themeStyleUrl, 'utf8')
  const decorations = [
    'immortal-gate-watermark',
    'immortal-taiji-mark',
    'immortal-bagua',
    'immortal-cloud-seal',
    'immortal-rune'
  ]

  for (const className of decorations) {
    assert.match(chatPageSource, new RegExp(`class="${className}"`))
    assert.match(source, new RegExp(`\\.chat-skin-cloud-immortal\\s+\\.${className}`))
  }
  assert.match(chatPageSource, /v-if="isCloudImmortal"[\s\S]*?aria-hidden="true"[\s\S]*?immortal-gate-watermark/)
  assert.match(source, /\.chat-skin-cloud-immortal\s+\.immortal-scene[\s\S]*?pointer-events:\s*none/)
  assert.match(source, /\.chat-skin-cloud-immortal\s+\.chat-header::after[\s\S]*?content:\s*['"][^'"]+['"][\s\S]*?position:\s*absolute/)
  assert.match(source, /@media\s*\(max-width:\s*768px\)[\s\S]*?\.immortal-gate-watermark/)
  assert.match(source, /@media\s*\(prefers-reduced-motion:\s*reduce\)[\s\S]*?\.immortal-(?:gate-watermark|taiji-mark|bagua|cloud-seal|rune)/)
})
