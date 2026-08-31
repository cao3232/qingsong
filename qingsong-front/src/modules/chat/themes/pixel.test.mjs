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
const themeStyleUrl = new URL('./pixel.scss', import.meta.url)

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

test('主题 store 注册像素完整皮肤（0 圆角硬质感）', () => {
  const preset = extractPreset('pixel')
  assert.match(preset, /label:\s*['"]像素['"]/)
  assert.match(preset, /isDark:\s*false/)
  assert.match(preset, /--chat-radius['"]\s*:\s*['"]0px['"]/)
  assert.deepEqual(extractTokens(preset), extractTokens(extractPreset('retro')))
})

test('聊天页仅在像素主题启用场景层', () => {
  assert.match(chatPageSource, /chat-skin-pixel/)
  assert.match(chatPageSource, /isPixel/)
  assert.match(chatPageSource, /v-if="isPixel"[\s\S]*?class="pixel-scene"/)
  assert.match(chatPageSource, /v-if="isPixel"[\s\S]*?aria-hidden="true"/)
  assert.match(chatPageSource, /pixel\.scss/)
})

test('像素场景装饰齐全、限定作用域且装饰层不拦截交互', () => {
  assert.equal(existsSync(themeStyleUrl), true)
  const source = readFileSync(themeStyleUrl, 'utf8')

  const decorations = ['pixel-scanlines', 'pixel-star']
  for (const className of decorations) {
    assert.match(chatPageSource, new RegExp(className), `模板缺少 ${className}`)
    assert.match(source, new RegExp(`\\.chat-skin-pixel\\s+\\.${className}`), `样式缺少 ${className}`)
  }
  assert.match(source, /\.chat-skin-pixel\s+\.pixel-scene[\s\S]*?pointer-events:\s*none/)
  // CRT 扫描线纹样
  assert.match(source, /\.pixel-scanlines[\s\S]*?repeating-linear-gradient/)
  // 头像像素化
  assert.match(source, /image-rendering:\s*pixelated/)
})

test('像素提供标题栏闪烁光标、弹层配色与响应式/减少动效规则', () => {
  const source = readFileSync(themeStyleUrl, 'utf8')

  assert.match(source, /\.chat-skin-pixel\s+\.chat-header::after[\s\S]*?content:\s*['"][^'"]+['"][\s\S]*?position:\s*absolute/)
  // steps(1) 硬闪烁
  assert.match(source, /animation:[^;]*steps\(1\)/)
  assert.match(chatPageSource, /document\.body\.setAttribute\(['"]data-chat-skin['"],\s*['"]pixel['"]\)/)
  assert.match(source, /body\[data-chat-skin=['"]pixel['"]\]\s+\.n-popover\.chat-workspace-popover[\s\S]*?--n-color:/)
  assert.match(source, /body\[data-chat-skin=['"]pixel['"]\]\s+\.chat-workspace-popover-content/)
  assert.match(source, /@media\s*\(max-width:\s*768px\)/)
  assert.match(source, /@media\s*\(prefers-reduced-motion:\s*reduce\)/)
})
