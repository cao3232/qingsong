import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import test from 'node:test'

const themeStoreSource = readFileSync(
  new URL('../../../stores/theme.js', import.meta.url),
  'utf8'
)
const chatPageSource = readFileSync(
  new URL('../pages/AIChatPage.vue', import.meta.url),
  'utf8'
)
const themeStyleUrl = new URL('./deepSea.scss', import.meta.url)

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

test('主题 store 注册深海完整皮肤', () => {
  const preset = extractPreset('deep-sea')
  assert.match(preset, /label:\s*['"]深海['"]/)
  assert.match(preset, /isDark:\s*true/)
  assert.deepEqual(extractTokens(preset), extractTokens(extractPreset('retro')))
})

test('聊天页仅在深海主题启用场景层', () => {
  assert.match(chatPageSource, /chat-skin-deep-sea/)
  assert.match(chatPageSource, /isDeepSea/)
  assert.match(chatPageSource, /v-if="isDeepSea"[\s\S]*?class="deep-sea-scene"/)
  assert.match(chatPageSource, /v-if="isDeepSea"[\s\S]*?aria-hidden="true"/)
  assert.match(chatPageSource, /deepSea\.scss/)
})

test('深海场景装饰齐全、限定作用域且装饰层不拦截交互', () => {
  assert.equal(existsSync(themeStyleUrl), true)
  const source = readFileSync(themeStyleUrl, 'utf8')

  const decorations = ['deep-sea-ray', 'deep-sea-bubbles', 'deep-sea-porthole']
  for (const className of decorations) {
    assert.match(chatPageSource, new RegExp(className), `模板缺少 ${className}`)
    assert.match(source, new RegExp(`\\.chat-skin-deep-sea\\s+\\.${className}`), `样式缺少 ${className}`)
  }
  assert.match(source, /\.chat-skin-deep-sea\s+\.deep-sea-scene[\s\S]*?pointer-events:\s*none/)
  assert.match(source, /@keyframes\s+deep-sea-bubble-rise/)
  assert.match(source, /translate3d/)
})

test('深海提供标题栏深度计、弹层配色与响应式/减少动效规则', () => {
  const source = readFileSync(themeStyleUrl, 'utf8')

  assert.match(source, /\.chat-skin-deep-sea\s+\.chat-header::after[\s\S]*?content:\s*['"][^'"]+['"][\s\S]*?position:\s*absolute/)
  assert.match(chatPageSource, /document\.body\.setAttribute\(['"]data-chat-skin['"],\s*['"]deep-sea['"]\)/)
  assert.match(source, /body\[data-chat-skin=['"]deep-sea['"]\]\s+\.n-popover\.chat-workspace-popover[\s\S]*?--n-color:/)
  assert.match(source, /body\[data-chat-skin=['"]deep-sea['"]\]\s+\.chat-workspace-popover-content/)
  assert.match(source, /@media\s*\(max-width:\s*768px\)/)
  assert.match(source, /@media\s*\(prefers-reduced-motion:\s*reduce\)/)
})
