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
const themeStyleUrl = new URL('./sky.scss', import.meta.url)

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

test('主题 store 注册天空完整皮肤', () => {
  const preset = extractPreset('sky')
  assert.match(preset, /label:\s*['"]天空['"]/)
  assert.match(preset, /isDark:\s*false/)
  assert.deepEqual(extractTokens(preset), extractTokens(extractPreset('retro')))
})

test('聊天页仅在天空主题启用场景层', () => {
  assert.match(chatPageSource, /chat-skin-sky/)
  assert.match(chatPageSource, /isSky/)
  assert.match(chatPageSource, /v-if="isSky"[\s\S]*?class="sky-scene"/)
  assert.match(chatPageSource, /v-if="isSky"[\s\S]*?aria-hidden="true"/)
  assert.match(chatPageSource, /sky\.scss/)
})

test('天空场景装饰齐全、限定作用域且装饰层不拦截交互', () => {
  assert.equal(existsSync(themeStyleUrl), true)
  const source = readFileSync(themeStyleUrl, 'utf8')

  const decorations = ['sky-sun', 'sky-cloud']
  for (const className of decorations) {
    assert.match(chatPageSource, new RegExp(className), `模板缺少 ${className}`)
    assert.match(source, new RegExp(`\\.chat-skin-sky\\s+\\.${className}`), `样式缺少 ${className}`)
  }
  assert.match(source, /\.chat-skin-sky\s+\.sky-scene[\s\S]*?pointer-events:\s*none/)
  assert.match(source, /@keyframes\s+sky-cloud-drift/)
})

test('天空提供标题栏天气牌、弹层配色与响应式/减少动效规则', () => {
  const source = readFileSync(themeStyleUrl, 'utf8')

  assert.match(source, /\.chat-skin-sky\s+\.chat-header::after[\s\S]*?content:\s*['"][^'"]+['"][\s\S]*?position:\s*absolute/)
  assert.match(chatPageSource, /document\.body\.setAttribute\(['"]data-chat-skin['"],\s*['"]sky['"]\)/)
  assert.match(source, /body\[data-chat-skin=['"]sky['"]\]\s+\.n-popover\.chat-workspace-popover[\s\S]*?--n-color:/)
  assert.match(source, /body\[data-chat-skin=['"]sky['"]\]\s+\.chat-workspace-popover-content/)
  assert.match(source, /@media\s*\(max-width:\s*768px\)/)
  assert.match(source, /@media\s*\(prefers-reduced-motion:\s*reduce\)/)
})
