import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const conversationSidebarSource = readFileSync(
  new URL('./ConversationSidebar.vue', import.meta.url),
  'utf8'
)
const messageShareModalSource = readFileSync(
  new URL('./MessageShareModal.vue', import.meta.url),
  'utf8'
)
const chatMessageSource = readFileSync(
  new URL('./ChatMessage.vue', import.meta.url),
  'utf8'
)
const chatMessageContentSource = readFileSync(
  new URL('../composables/useChatMessageContent.js', import.meta.url),
  'utf8'
)

const findAllBalancedBlocks = (source, startPattern) => {
  const flags = startPattern.flags.includes('g') ? startPattern.flags : `${startPattern.flags}g`
  const blocks = []

  for (const match of source.matchAll(new RegExp(startPattern.source, flags))) {
    const blockStart = source.indexOf('{', match.index + match[0].length)
    if (blockStart < 0) continue

    let depth = 0
    for (let index = blockStart; index < source.length; index += 1) {
      if (source[index] === '{') depth += 1
      if (source[index] === '}') depth -= 1
      if (depth === 0) {
        blocks.push(source.slice(match.index, index + 1))
        break
      }
    }
  }

  return blocks
}

test('用户消息弹窗为空时限制占位图标尺寸', () => {
  const modalStyles = conversationSidebarSource.match(/\.user-message-modal\s*\{[\s\S]*?\n\}/)?.[0] || ''

  assert.match(modalStyles, /\.empty-placeholder\s*\{/)
  assert.match(modalStyles, /\.empty-icon\s*\{[\s\S]*?width:\s*40px;[\s\S]*?height:\s*40px;/)
  assert.match(modalStyles, /\.empty-icon[\s\S]*?svg\s*\{[\s\S]*?width:\s*100%;[\s\S]*?height:\s*100%;/)
})

test('分享卡片独立约束克隆内容和表情图片', () => {
  assert.match(messageShareModalSource, /\.share-card-body[\s\S]*?:deep\(\.emoji-img\)[\s\S]*?width:\s*1\.2em\s*!important;/)
  assert.match(messageShareModalSource, /\.share-card-body[\s\S]*?:deep\(svg\)[\s\S]*?max-width:\s*100%;/)
  assert.match(messageShareModalSource, /\.share-card-body[\s\S]*?pointer-events:\s*none;/)
})

test('用户消息分享卡片不标记为 AI 生成内容', () => {
  assert.match(messageShareModalSource, /isUser\s*\?\s*['"]用户分享内容['"]\s*:\s*['"]内容由 AI 生成 · 请注意甄别['"]/)
})

test('AI Markdown 使用轻文档排版层级', () => {
  const paragraphSource = findAllBalancedBlocks(chatMessageSource, /:deep\(p\)/)
    .find(block => /line-height:/.test(block)) || ''

  assert.match(chatMessageSource, /:deep\(h1\)\s*\{[^}]*font-size:\s*20px;/)
  assert.match(chatMessageSource, /:deep\(h2\)\s*\{[^}]*font-size:\s*17px;/)
  assert.match(paragraphSource, /margin:\s*0\s+0\s+12px;/)
  assert.match(paragraphSource, /line-height:\s*1\.72;/)
  assert.match(chatMessageSource, /:deep\(blockquote\)\s*\{[^}]*border-left:/)
})

test('Mermaid 提供放大入口并在渲染后标记宽图', () => {
  assert.match(chatMessageContentSource, /class="mermaid-zoom-btn"/)
  assert.match(chatMessageContentSource, /aria-label="放大查看流程图"/)
  assert.match(chatMessageContentSource, /block\.classList\.toggle\(['"]mermaid-wide['"],\s*isWide\)/)
  assert.match(chatMessageContentSource, /--mermaid-natural-width/)
  assert.match(chatMessageContentSource, /serializeMermaidPreviewSvg/)
  assert.match(chatMessageContentSource, /onMermaidPreview\?\./)
})

test('Mermaid 预览支持缩放拖动和关闭清理', () => {
  assert.match(chatMessageSource, /class="mermaid-preview-backdrop"/)
  assert.match(chatMessageSource, /aria-modal="true"/)
  assert.match(chatMessageSource, /@wheel\.prevent="handleMermaidPreviewWheel"/)
  assert.match(chatMessageSource, /@pointerdown="handleMermaidPointerDown"/)
  assert.match(chatMessageSource, /@keydown\.esc="closeMermaidPreview"/)
  assert.match(chatMessageSource, /const closeMermaidPreview = \(\) =>/)
  assert.match(chatMessageSource, /activePreviewPointers\.clear\(\)/)
  assert.match(chatMessageSource, /document\.body\.style\.overflow = previousBodyOverflow/)
})

test('Mermaid 预览使用 capture 键盘监听并在弹窗内循环焦点', () => {
  assert.match(chatMessageSource, /document\.addEventListener\(['"]keydown['"],\s*handleMermaidPreviewDocumentKeydown,\s*true\)/)
  assert.match(chatMessageSource, /event\.key !== ['"]Tab['"]/) 
  assert.match(chatMessageSource, /mermaidPreviewDialogRef\.value\?\.querySelectorAll/)
  assert.match(chatMessageSource, /focusableElements\[0\]\?\.focus\(\)/)
  assert.match(chatMessageSource, /previousActiveElement\?\.focus\?\.\(\)/)
})

test('聊天 Markdown 使用模块局部 Marked 实例', () => {
  assert.match(chatMessageContentSource, /import\s*\{[^}]*\bMarked\b[^}]*\}\s*from\s*['"]marked['"]/)
  assert.match(chatMessageContentSource, /const markdownParser = new Marked\(\)/)
  assert.match(chatMessageContentSource, /markdownParser\.use\(/)
  assert.match(chatMessageContentSource, /markdownParser\.parse\(/)
  assert.doesNotMatch(chatMessageContentSource, /\bmarked\.use\(/)
})

test('Mermaid 宽图按有效容器宽度判断且不强制放大到 900px', () => {
  assert.match(chatMessageContentSource, /const isWide = availableWidth > 0 && naturalWidth > availableWidth/)
  assert.match(chatMessageContentSource, /Math\.max\(naturalWidth,\s*Math\.min\(availableWidth \* 1\.15,\s*900\)\)/)
  assert.doesNotMatch(chatMessageContentSource, /Math\.max\(availableWidth,\s*720\)/)
  assert.doesNotMatch(chatMessageContentSource, /Math\.max\(900,\s*naturalWidth\)/)
})

test('宽 Mermaid 保留可读宽度且页面本身不横向溢出', () => {
  const mermaidBlockSource = findAllBalancedBlocks(chatMessageSource, /:deep\(\.mermaid-block\)/)
    .find(block => block.includes('mermaid-wide') && block.includes('.mermaid-render')) || ''
  const mobileMediaSource = findAllBalancedBlocks(chatMessageSource, /@media \(max-width: 768px\)/)
    .find(block => block.includes('.mermaid-block')) || ''

  assert.match(mermaidBlockSource, /mermaid-wide[^\{]*\{[^}]*width:\s*var\(--mermaid-natural-width\)/)
  assert.match(mermaidBlockSource, /mermaid-wide[^\{]*\{[^}]*max-width:\s*none;/)
  assert.match(mermaidBlockSource, /mermaid-render[^\{]*\{[^}]*overflow-x:\s*auto;/)
  assert.match(mobileMediaSource, /^@media \(max-width: 768px\)/)
  assert.match(mobileMediaSource, /:deep\(\.mermaid-block\)\s*\{[^}]*max-width:\s*100%;/)
})
