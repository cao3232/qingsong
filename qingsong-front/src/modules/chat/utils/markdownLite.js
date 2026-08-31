import { Marked } from 'marked'
import DOMPurify from 'dompurify'

// 轻量 markdown 渲染：供收藏页等只读场景使用。
// 不加载 katex/mermaid/emoji（聊天页完整渲染见 useChatMessageContent），
// 代码块以 pre/code 原样展示，体积小、渲染快。
const marked = new Marked({
  gfm: true,
  breaks: true
})

export const renderMarkdownLite = content => {
  const text = String(content || '').trim()
  if (!text) return ''
  const html = marked.parse(text, { async: false })
  return DOMPurify.sanitize(html)
}
