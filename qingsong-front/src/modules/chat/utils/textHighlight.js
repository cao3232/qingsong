// 关键词字符级高亮 + 定位（CSS Custom Highlight API）：
// 不改动 DOM（避免与 Vue 渲染/markdown 结构冲突），通过全局高亮注册表绘制，
// 样式见 AIChatPage.vue 的非 scoped 样式块 ::highlight(chat-search-hit)。
// 浏览器不支持（CSS.highlights 缺失）时静默降级：仅保留消息级整卡高亮。
const HIGHLIGHT_NAME = 'chat-search-hit'
let clearTimer = null

export const clearKeywordHighlight = () => {
  if (typeof CSS === 'undefined' || !CSS.highlights) return
  CSS.highlights.delete(HIGHLIGHT_NAME)
  if (clearTimer) {
    clearTimeout(clearTimer)
    clearTimer = null
  }
}

// 在 rootEl 的所有文本节点里查找 keyword（大小写不敏感），逐处注册 Range 高亮，
// 并把第一个命中处滚动到可视区中央（长消息内定位到具体字符）
export const highlightKeywordInElement = (rootEl, keyword, { autoClearMs = 8000 } = {}) => {
  if (!rootEl || !keyword) return false
  if (typeof CSS === 'undefined' || !CSS.highlights || typeof Highlight === 'undefined') return false

  clearKeywordHighlight()

  const needle = String(keyword).toLowerCase()
  const ranges = []
  const walker = document.createTreeWalker(rootEl, NodeFilter.SHOW_TEXT)
  let node
  while ((node = walker.nextNode())) {
    const text = node.nodeValue || ''
    const lower = text.toLowerCase()
    let from = 0
    while (from < text.length) {
      const hit = lower.indexOf(needle, from)
      if (hit === -1) break
      const range = document.createRange()
      range.setStart(node, hit)
      range.setEnd(node, hit + needle.length)
      ranges.push(range)
      from = hit + needle.length
    }
  }

  if (ranges.length === 0) return false

  CSS.highlights.set(HIGHLIGHT_NAME, new Highlight(...ranges))
  ranges[0].startContainer.parentElement?.scrollIntoView({ block: 'center', behavior: 'auto' })

  if (autoClearMs > 0) {
    clearTimer = setTimeout(clearKeywordHighlight, autoClearMs)
  }
  return true
}

// 虚拟列表场景：目标消息可能尚未渲染（估算高度漂移），轮询等待其出现后做字符级高亮
export const waitForMessageAndHighlight = (messageIndex, keyword, { maxTries = 30 } = {}) => {
  let tries = 0
  const attempt = () => {
    const slot = document.querySelector(`[data-index="${messageIndex}"]`)
    const root = slot?.querySelector('.message-content') || slot
    if (root && highlightKeywordInElement(root, keyword)) {
      return
    }
    if (tries++ >= maxTries) return
    requestAnimationFrame(attempt)
  }
  requestAnimationFrame(attempt)
}
