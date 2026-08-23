import { computed, ref, watch } from 'vue'
import { Marked, Renderer } from 'marked'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js'
import { useThrottleFn } from '@vueuse/core'
import katex from 'katex'
import 'katex/dist/katex.min.css'
import { replaceEmojis } from '../utils/emoji.js'
import { extractReasoning } from '../utils/chatBehavior.js'
import { useEmojiStore } from '@/stores/emojiStore'
import { useThemeStore } from '@/stores/theme'

const SANITIZE_OPTIONS = {
  ADD_TAGS: ['div', 'code', 'pre', 'span', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'p', 'a', 'ul', 'ol', 'li', 'blockquote', 'hr', 'br', 'button', 'img', 'strong', 'em', 'table', 'thead', 'tbody', 'tr', 'td', 'th', 'input'],
  ADD_ATTR: ['class', 'target', 'rel', 'title', 'style', 'data-image-wrapper', 'data-image-id', 'data-copy-id', 'alt', 'src', 'loading', 'type', 'checked', 'disabled'],
  KEEP_CONTENT: true,
  RETURN_DOM: false,
  FORBID_TAGS: ['script', 'style', 'iframe', 'object', 'embed'],
  FORBID_ATTR: ['onclick', 'onload', 'onerror', 'onmouseover']
}

const MARKDOWN_OPTIONS = {
  renderer: new Renderer(),
  breaks: true,
  gfm: true
}

// KaTeX 数学公式：块级 $$...$$ 与行内 $...$（仅本模块渲染时生效，不污染全局 marked 配置）
const renderKatex = (tex, displayMode) => {
  try {
    return katex.renderToString(tex, {
      displayMode,
      throwOnError: false,
      output: 'html'
    })
  } catch (error) {
    return `<code class="math-error">${escapeHtml(tex)}</code>`
  }
}

const MATH_BLOCK_EXTENSION = {
  name: 'mathBlock',
  level: 'block',
  start(src) {
    return src.indexOf('$$')
  },
  tokenizer(src) {
    const match = /^\$\$([\s\S]+?)\$\$/.exec(src)
    if (match) {
      return {
        type: 'mathBlock',
        raw: match[0],
        text: match[1].trim()
      }
    }
    return undefined
  },
  renderer(token) {
    return `<div class="math-block">${renderKatex(token.text, true)}</div>`
  }
}

const MATH_INLINE_EXTENSION = {
  name: 'mathInline',
  level: 'inline',
  start(src) {
    return src.indexOf('$')
  },
  tokenizer(src) {
    const match = /^\$(?!\s)([^$\n]+?)(?<!\s)\$/.exec(src)
    if (match) {
      return {
        type: 'mathInline',
        raw: match[0],
        text: match[1].trim()
      }
    }
    return undefined
  },
  renderer(token) {
    return renderKatex(token.text, false)
  }
}

MARKDOWN_OPTIONS.extensions = [MATH_BLOCK_EXTENSION, MATH_INLINE_EXTENSION]

// 删除线只认 GFM 的双波浪线 ~~text~~，禁用单波浪线 ~text~（marked 默认会把单个 ~ 误解析为删除线）
const markdownParser = new Marked()
markdownParser.use({
  tokenizer: {
    del(src) {
      const match = /^(~~)(?=[^\s~])((?:\\.|[^\\])*?(?:\\.|[^\s~\\]))\1(?=[^~]|$)/.exec(src)
      if (!match) return undefined
      return {
        type: 'del',
        raw: match[0],
        text: match[2],
        tokens: this.lexer.inlineTokens(match[2])
      }
    }
  }
})

const escapeHtml = value =>
  String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')

// 将 HTML 实体还原为纯文本（用于生成标题锚点 slug）
const decodeEntityText = value => {
  if (typeof document === 'undefined') {
    return String(value)
      .replace(/&lt;/g, '<')
      .replace(/&gt;/g, '>')
      .replace(/&amp;/g, '&')
      .replace(/&quot;/g, '"')
      .replace(/&#39;/g, "'")
  }

  const textarea = document.createElement('textarea')
  textarea.innerHTML = value
  return textarea.value
}

// 生成 URL/ID 友好的 slug（保留中英文与数字）
const slugify = value =>
  String(value || '')
    .toLowerCase()
    .replace(/[\s_]+/g, '-')
    .replace(/[^\w一-龥-]+/g, '')
    .replace(/-+/g, '-')
  .replace(/^-|-$/g, '')
  .slice(0, 48)

// 图片加载失败重试：消化后端 429 限流 / 瞬时抖动
const IMAGE_MAX_RETRY = 3
const IMAGE_RETRY_DELAYS = [400, 1000, 2000]

// 渲染缓存上限：防止长会话中每条消息都持有整段 HTML 导致内存持续增长
const RENDER_CACHE_MAX = 10

const normalizeHref = (href, { allowDataImage = false } = {}) => {
  if (typeof href !== 'string') return ''

  const normalizedHref = href.trim()
  if (!normalizedHref) return ''

  const allowedPattern = allowDataImage
    ? /^(https?:|mailto:|tel:|data:image\/|\/|\.{1,2}\/|#)/i
    : /^(https?:|mailto:|tel:|\/|\.{1,2}\/|#)/i

  return allowedPattern.test(normalizedHref) ? normalizedHref : ''
}

const decodeCodeContent = codeContent => {
  const plainCode = String(codeContent)
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<[^>]*>/g, '')

  if (typeof document === 'undefined') {
    return plainCode
      .replace(/&lt;/g, '<')
      .replace(/&gt;/g, '>')
      .replace(/&amp;/g, '&')
      .replace(/&quot;/g, '"')
      .replace(/&#39;/g, "'")
      .replace(/\r\n/g, '\n')
      .replace(/\n$/, '')
  }

  const textarea = document.createElement('textarea')
  textarea.innerHTML = plainCode
  return textarea.value.replace(/\r\n/g, '\n').replace(/\n$/, '')
}

const copyToClipboard = async text => {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      const textArea = document.createElement('textarea')
      textArea.value = text
      textArea.style.position = 'fixed'
      textArea.style.left = '-999999px'
      textArea.style.top = '-999999px'
      document.body.appendChild(textArea)
      textArea.focus()
      textArea.select()
      document.execCommand('copy')
      document.body.removeChild(textArea)
    }

    return true
  } catch (error) {
    console.error('复制失败:', error)
    return false
  }
}

MARKDOWN_OPTIONS.renderer.link = function link(token) {
  const href = token?.href ?? arguments[0]
  const title = token?.title ?? arguments[1]

  // marked v15 的 link token 没有 .text，文本由内联 token 组成，需用 parser.parseInline 还原。
  let text = ''
  if (token && token.tokens) {
    try {
      text = this.parser.parseInline(token.tokens)
    } catch (error) {
      text = token.text || arguments[2] || ''
    }
  } else {
    text = arguments[2] || ''
  }

  const safeHref = normalizeHref(href) || '#'
  const safeText = typeof text === 'string' && text.trim() ? text : safeHref
  const safeTitle = typeof title === 'string' ? title : ''

  return `<a target="_blank" rel="noopener noreferrer nofollow" href="${safeHref}" title="${safeTitle}">${safeText}</a>`
}

MARKDOWN_OPTIONS.renderer.image = function image(token) {
  const href = typeof token === 'object' && token !== null ? token.href : arguments[0]
  const title = typeof token === 'object' && token !== null ? token.title : arguments[1]
  const text = typeof token === 'object' && token !== null ? token.text : arguments[2]
  const imageId = `img-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`

  const rawSrc = typeof href === 'string' ? href.trim() : ''
  // 只拦截会执行脚本的协议/非图片 data:，其余放行，最终由 DOMPurify 兜底净化。
  // 避免过严的白名单把相对路径、blob:、协议相对等合法图片地址误判为错误。
  const isUnsafe =
    /^(javascript|vbscript):/i.test(rawSrc) ||
    (/^data:/i.test(rawSrc) && !/^data:image\//i.test(rawSrc))

  if (!rawSrc || isUnsafe) {
    return '<div class="image-wrapper error-wrapper"></div>'
  }

  const escapedSrc = escapeHtml(rawSrc)
  const escapedAlt = escapeHtml(text || '图片')
  const escapedTitle = escapeHtml(title || '')

  return `
    <div class="image-wrapper loading-wrapper" data-image-wrapper="${imageId}">
      <img id="${imageId}" src="${escapedSrc}" alt="${escapedAlt}" title="${escapedTitle}" data-image-id="${imageId}" loading="lazy" decoding="async">
    </div>
  `
}

export const useChatMessageContent = ({ contentRef, isUser, loading, message, messageApi, onMermaidPreview }) => {
  const codeBlocksData = ref({})
  const renderCache = new Map()
  const emojiStore = useEmojiStore()
  const themeStore = useThemeStore()

  // 表情风格切换后清空渲染缓存，避免旧风格残留
  watch(
    () => emojiStore.provider,
    () => renderCache.clear()
  )

  const createCodeBlockMarkup = (attrs, codeContent, { streaming = false } = {}) => {
    const languageMatch = attrs.match(/class="[^"]*language-([^"\\s]+)[^"]*"/)
    const language = languageMatch ? languageMatch[1] : ''
    const copyId = `code-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
    const codeText = decodeCodeContent(codeContent)
    const langLabel = language ? `<span class="code-lang">${language}</span>` : '<span class="code-lang">code</span>'
    const stateClass = streaming ? ' streaming-code' : ''

    codeBlocksData.value[copyId] = codeText

    return `
      <div class="code-block-wrapper${stateClass}">
        <div class="code-header">
          ${langLabel}
          <button class="code-copy-btn" type="button" title="复制代码" data-copy-id="${copyId}">
            <svg class="copy-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
              <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
            </svg>
          </button>
        </div>
        <pre><code${attrs}>${codeContent}</code></pre>
    </div>
  `
}

// 支持以代码块形式编写的流程图（mermaid 及其常见别名）。
// 这些语言标记的代码块会被识别为图表占位符，由 mermaid 在挂载后渲染为 SVG。
const MERMAID_LANGUAGES = new Set([
  'mermaid',
  'flowchart',
  'sequence',
  'sequencediagram',
  'classdiagram',
  'statediagram',
  'statediagram-v2',
  'gantt',
  'pie',
  'erdiagram',
  'journey',
  'gitgraph',
  'mindmap',
  'timeline',
  'quadrantchart',
  'requirementdiagram',
  'block-beta',
  'sankey',
  'xychart-beta',
  'c4context'
])

const createMermaidBlockMarkup = codeContent => {
  const source = decodeCodeContent(codeContent)
  const escapedSource = escapeHtml(source)

  return `
    <div class="mermaid-block" data-diagram="mermaid">
      <div class="mermaid-render"></div>
      <div class="mermaid-toolbar">
        <button class="mermaid-zoom-btn" type="button" data-action="preview-diagram" title="放大查看" aria-label="放大查看流程图"><span aria-hidden="true">+</span> 放大查看</button>
        <button class="mermaid-toggle-btn" type="button" data-action="toggle-source" title="查看/隐藏源码" aria-label="查看或隐藏流程图源码">查看源码</button>
      </div>
      <pre class="mermaid-source hidden"><code>${escapedSource}</code></pre>
    </div>
  `
}

// 裸 mermaid 自动识别：模型偶发不写 ```mermaid 围栏时，把"裸写"的图源包裹成围栏块。
// 仅在渲染前对原始 Markdown 做行级扫描，避开已有围栏代码块；流式阶段不包裹延伸到 EOF 的块（可能仍在增长）。
const MERMAID_DIRECTIVE_RE = /^(flowchart|graph|sequenceDiagram|classDiagram|stateDiagram(?:-v2)?|erDiagram|gantt|gitGraph|mindmap|journey|timeline|quadrantChart|requirementDiagram|block-beta|sankey|xychart-beta|c4context|c4Context|pie)\b/i

// 易与英文单词混淆的指令，需正文含 mermaid 语法证据才认定
const AMBIGUOUS_DIRECTIVES = new Set(['flowchart', 'graph', 'gantt', 'pie', 'journey', 'timeline', 'mindmap', 'sankey'])

// mermaid 语法痕迹：连线 / 节点形状 / 关键字 / 注释
const MERMAID_TOKEN_RE = /->|\[[^\]]*\]|\{[^\}]*\}|\([^)]*\)|(?:^\s*(?:subgraph|end|class|classDef|style|linkStyle|direction|click|note|participant|actor|activate|deactivate|loop|alt|else|opt|rect|section|title|axis|accTitle|accDescr)\b)|^%%/

const wrapBareMermaidBlocks = (markdown, { streaming = false } = {}) => {
  const text = String(markdown || '')
  if (!text) return text
  const lines = text.split('\n')
  const out = []
  let i = 0
  while (i < lines.length) {
    const line = lines[i]

    // 原样保留围栏代码块（``` 或 ~~~），避免破坏已有代码块
    const fenceHead = line.trimStart().match(/^(`{3,}|~{3,})/)
    if (fenceHead) {
      const fence = fenceHead[1]
      const closer = new RegExp('^' + fence[0] + '{' + fence.length + ',}\\s*$')
      out.push(line)
      i++
      while (i < lines.length) {
        out.push(lines[i])
        if (closer.test(lines[i].trimStart())) { i++; break }
        i++
      }
      continue
    }

    const directive = MERMAID_DIRECTIVE_RE.exec(line.trim())
    if (directive) {
      const name = directive[1].toLowerCase()
      const ambiguous = AMBIGUOUS_DIRECTIVES.has(name)
      const block = [line]
      let j = i + 1
      while (j < lines.length) {
        const next = lines[j]
        if (!next.trim()) break
        // 明显是散文（超长且无 mermaid 痕迹）则停止收集
        if (next.length > 120 && !MERMAID_TOKEN_RE.test(next)) break
        block.push(next)
        j++
      }
      const atEof = j >= lines.length
      const bodyHasEvidence = block.slice(1).some(l => MERMAID_TOKEN_RE.test(l))
      const hasEvidence = ambiguous ? bodyHasEvidence : true
      if (block.length >= 2 && hasEvidence && (!streaming || !atEof)) {
        out.push('```mermaid')
        out.push(...block)
        out.push('```')
        i = j
        continue
      }
    }

    out.push(line)
    i++
  }
  return out.join('\n')
}

const normalizeMarkdownForStreaming = content => {
    const normalizedContent = String(content || '').replace(/\r\n/g, '\n')
    const fenceMatches = normalizedContent.match(/(^|\n)```/g)

    if (fenceMatches && fenceMatches.length % 2 === 1) {
      return `${normalizedContent}\n\`\`\``
    }

    return normalizedContent
  }

  // CommonMark 的分隔符规则对 CJK 全角标点不友好，导致加粗无法解析：
  // 1. 标点紧跟 **（如 **注意：**正文）——** 无法作为闭合分隔符；
  // 2. ** 后紧跟标点（如是**"外部加水"**）——** 无法作为开启分隔符。
  // 两种场景都在 ** 与标点之间插入零宽空格（ZWSP）使其可正常开启/闭合，渲染后再统一移除。
  const CJK_PUNCT = '，。；：、！？（）【】《》「」『』“”‘’．…—,;:.!?\'"'
  const fixCjkBoldCloser = content => {
    const text = String(content || '')
    return text
      .replace(new RegExp(`([${CJK_PUNCT}])(\\*\\*)(?=[^\\s*])`, 'g'), `$1\u200B$2`)
      .replace(new RegExp(`([^\\s\\p{P}\\p{S}])(\\*\\*)(?=[${CJK_PUNCT}])`, 'gu'), `$1$2\u200B`)
  }

  const postProcessMarkdownHtml = (html, { streaming = false, messageAnchorId = '' } = {}) =>
    html
      .replace(/\u200B/g, '')
      .replace(/<pre><code([^>]*)>([\s\S]*?)<\/code><\/pre>/g, (_match, attrs, codeContent) => {
        const langMatch = attrs.match(/language-([^\s"\\]+)/)
        const lang = langMatch ? langMatch[1].toLowerCase() : ''

        if (MERMAID_LANGUAGES.has(lang)) {
          return createMermaidBlockMarkup(codeContent)
        }

        return createCodeBlockMarkup(attrs, codeContent, { streaming })
      })
      .replace(/<h([1-6])>([\s\S]*?)<\/h\1>/g, (_match, level, inner) => {
        const rawText = inner.replace(/<[^>]+>/g, '')
        const slug = slugify(decodeEntityText(rawText)) || 'section'
        const anchorId = messageAnchorId
          ? `${slugify(String(messageAnchorId))}--${slug}`
          : `heading--${slug}`

        return `<h${level} id="${anchorId}" class="heading-anchor">` +
          `<a class="heading-link" href="#${anchorId}" data-anchor-id="${anchorId}" ` +
          `title="复制本节链接" aria-label="复制本节链接">#</a>${inner}</h${level}>`
      })
      .replace(/<table>/g, '<div class="table-wrapper"><table>')
      .replace(/<\/table>/g, '</table></div>')

  const renderMarkdown = (content, { streaming = false, scope = 'main' } = {}) => {
    if (!content) return ''

    const cacheKey = `${scope}::${content}`
    if (!streaming && renderCache.has(cacheKey)) {
      return renderCache.get(cacheKey)
    }

    try {
      const normalizedContent = streaming
        ? normalizeMarkdownForStreaming(wrapBareMermaidBlocks(content, { streaming: true }))
        : wrapBareMermaidBlocks(content, { streaming: false })
      const parsedHtml = markdownParser.parse(fixCjkBoldCloser(normalizedContent), MARKDOWN_OPTIONS)
      const cleanHtml = DOMPurify.sanitize(parsedHtml, SANITIZE_OPTIONS)

      if (scope === 'main') {
        codeBlocksData.value = {}
      }
      const messageAnchorId = message.value?.messageNo || message.value?.id || ''
      const renderedHtml = postProcessMarkdownHtml(cleanHtml, { streaming, messageAnchorId })
      const finalHtml = replaceEmojis(renderedHtml, emojiStore.provider)

      if (!streaming) {
        if (renderCache.size >= RENDER_CACHE_MAX) {
          const oldestKey = renderCache.keys().next().value
          if (oldestKey !== undefined) {
            renderCache.delete(oldestKey)
          }
        }
        renderCache.set(cacheKey, finalHtml)
      }

      return finalHtml
    } catch (error) {
      console.error('Markdown 渲染失败:', error)
      return replaceEmojis(DOMPurify.sanitize(String(content), SANITIZE_OPTIONS), emojiStore.provider)
    }
  }

  const copyCodeBlock = async (code, button) => {
    if (await copyToClipboard(code)) {
      messageApi.success('代码已复制到剪贴板')

      const originalHTML = button.innerHTML
      button.innerHTML = `
        <svg class="copy-icon success" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="20,6 9,17 4,12"></polyline>
        </svg>
      `
      button.style.color = '#10b981'

      setTimeout(() => {
        button.innerHTML = originalHTML
        button.style.color = ''
      }, 2000)

      return true
    }

    messageApi.error('复制失败，请重试')
    return false
  }

  const copyAnchorLink = async anchorId => {
    try {
      const base = typeof window !== 'undefined'
        ? `${window.location.origin}${window.location.pathname}`
        : ''
      const url = `${base}#${anchorId}`

      if (await copyToClipboard(url)) {
        messageApi.success('本节链接已复制')
      } else {
        messageApi.error('复制失败，请重试')
      }
    } catch (error) {
      console.error('复制锚点链接失败:', error)
      messageApi.error('复制失败，请重试')
    }
  }

  const serializeMermaidPreviewSvg = svg => {
    const clonedSvg = svg.cloneNode(true)
    const prefix = `mermaid-preview-${Date.now()}-${Math.random().toString(36).slice(2, 9)}-`
    const idElements = [
      ...(clonedSvg.hasAttribute('id') ? [clonedSvg] : []),
      ...clonedSvg.querySelectorAll('[id]')
    ]
    const idMap = new Map()

    idElements.forEach((element, index) => {
      const oldId = element.getAttribute('id')
      if (!oldId) return
      const newId = `${prefix}${index}`
      idMap.set(oldId, newId)
      element.setAttribute('id', newId)
    })

    const cssEscape = value => {
      if (globalThis.CSS?.escape) return globalThis.CSS.escape(value)
      const characters = Array.from(value)
      return characters.map((character, index) => {
        const codePoint = character.codePointAt(0)
        if (codePoint === 0) return '\uFFFD'
        if (
          (codePoint >= 1 && codePoint <= 31) ||
          codePoint === 127 ||
          (index === 0 && /[0-9]/.test(character)) ||
          (index === 1 && /[0-9]/.test(character) && characters[0] === '-')
        ) {
          return `\\${codePoint.toString(16)} `
        }
        if (index === 0 && character === '-' && characters.length === 1) return '\\-'
        if (codePoint >= 128 || /[A-Za-z0-9_-]/.test(character)) return character
        return `\\${character}`
      }).join('')
    }
    const regexEscape = value => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    const idEntries = [...idMap.entries()].sort(([left], [right]) => right.length - left.length)
    const idRefAttributes = new Set(['aria-labelledby', 'aria-describedby'])
    ;[clonedSvg, ...clonedSvg.querySelectorAll('*')].forEach(element => {
      Array.from(element.attributes).forEach(attribute => {
        let value = attribute.value
        idEntries.forEach(([oldId, newId]) => {
          value = value.split(`url(#${oldId})`).join(`url(#${newId})`)
          if (value === `#${oldId}`) value = `#${newId}`
        })
        if (idRefAttributes.has(attribute.name)) {
          value = value.split(/\s+/).map(id => idMap.get(id) || id).join(' ')
        }
        if (value !== attribute.value) element.setAttribute(attribute.name, value)
      })
    })

    clonedSvg.querySelectorAll('style').forEach(style => {
      let css = style.textContent || ''
      idEntries.forEach(([oldId, newId]) => {
        const escapedSelectorId = regexEscape(cssEscape(oldId))
        css = css.split(`url(#${oldId})`).join(`url(#${newId})`)
        css = css.replace(
          new RegExp(`#${escapedSelectorId}(?![A-Za-z0-9_-]|[^\\x00-\\x7F]|\\\\)(?=[^{}]*\\{)`, 'g'),
          `#${newId}`
        )
      })
      style.textContent = css
    })

    return clonedSvg.outerHTML
  }

  const getSvgViewBoxSize = svg => {
    const baseVal = svg.viewBox?.baseVal
    if (baseVal?.width > 0 && baseVal?.height > 0) {
      return { width: baseVal.width, height: baseVal.height }
    }

    const values = (svg.getAttribute('viewBox') || '')
      .trim()
      .split(/[\s,]+/)
      .map(Number)
    if (values.length === 4 && values.every(Number.isFinite) && values[2] > 0 && values[3] > 0) {
      return { width: values[2], height: values[3] }
    }

    return null
  }

  const parseAbsoluteSvgLength = value => {
    const match = String(value || '').match(/^\s*(\d+(?:\.\d+)?|\.\d+)\s*(?:px)?\s*$/i)
    return match ? Number(match[1]) : 0
  }

  const handleContentClick = event => {
    const target = event.target
    const headingLink = target?.closest?.('.heading-link')
    if (headingLink) {
      const anchorId = headingLink.dataset.anchorId
      if (anchorId) {
        copyAnchorLink(anchorId)
      }
      event.preventDefault()
      return
    }

    const mermaidZoom = target?.closest?.('.mermaid-zoom-btn')
    if (mermaidZoom) {
      const svg = mermaidZoom.closest('.mermaid-block')?.querySelector('.mermaid-render svg')
      if (svg) {
        const viewBox = getSvgViewBoxSize(svg)
        const rect = svg.getBoundingClientRect()
        const width = viewBox?.width || parseAbsoluteSvgLength(svg.getAttribute('width')) || rect.width
        const height = viewBox?.height || parseAbsoluteSvgLength(svg.getAttribute('height')) || rect.height
        onMermaidPreview?.({ svg: serializeMermaidPreviewSvg(svg), width, height })
      }
      return
    }

    const mermaidToggle = target?.closest?.('.mermaid-toggle-btn')
    if (mermaidToggle) {
      const block = mermaidToggle.closest('.mermaid-block')
      const sourcePre = block?.querySelector('.mermaid-source')
      if (sourcePre) {
        const nowHidden = sourcePre.classList.toggle('hidden')
        mermaidToggle.textContent = nowHidden ? '查看源码' : '隐藏源码'
      }
      return
    }

    const button = target?.closest?.('.code-copy-btn')
    if (!button) return

    const copyId = button.dataset.copyId
    if (copyId && codeBlocksData.value[copyId]) {
      copyCodeBlock(codeBlocksData.value[copyId], button)
    }
  }

  const reasoningState = computed(() => {
    const fallback = extractReasoning(message.value?.content || '')
    const explicit = String(message.value?.reasoningContent || '')
    return explicit.trim()
      ? { reasoning: explicit, main: fallback.main }
      : fallback
  })

  const processedContent = computed(() => {
    if (!message.value?.content) return ''
    // 正文排除推理过程，推理单独渲染到折叠块
    const main = reasoningState.value.main
    if (loading.value) {
      // 「流式结束后解析 MD」开启时：流式期间仅显示纯文本，结束后再完整解析 Markdown，
      // 避免超长回复在流式阶段逐字解析 Markdown 造成的卡顿。
      if (themeStore.config.parseMdAfterStream) {
        return `<div class="md-stream-plain">${escapeHtml(main)}</div>`
      }
      // 流式阶段也按 Markdown 渐进渲染（自动补全未闭合围栏），避免结束时整段重排的“闪烁”。
      return renderMarkdown(main, { streaming: true })
    }

    return renderMarkdown(main)
  })

  const hasReasoning = computed(() => Boolean(reasoningState.value.reasoning))
  const reasoningHtml = computed(() => {
    if (!hasReasoning.value) return ''
    const text = reasoningState.value.reasoning
    // 思考过程按纯文本展示：保留原有缩进/空行，避免渲染出代码复制按钮与混乱排版
    return `<div class="reasoning-plain">${escapeHtml(text)}</div>`
  })

  const reasoningExpanded = ref(Boolean(loading.value))
  watch(
    () => loading.value,
    isLoading => {
      // 流式阶段自动展开推理过程，结束后默认收起，避免刷屏
      reasoningExpanded.value = isLoading
    }
  )
  const toggleReasoning = () => {
    reasoningExpanded.value = !reasoningExpanded.value
  }

  const highlightCode = useThrottleFn(() => {
    if (!contentRef.value) return

    requestAnimationFrame(() => {
      const unhighlightedCodeBlocks = contentRef.value.querySelectorAll('pre code:not(.hljs)')
      if (unhighlightedCodeBlocks.length === 0) return

      const batchSize = 2
      let index = 0

      const processBatch = () => {
        const endIndex = Math.min(index + batchSize, unhighlightedCodeBlocks.length)

        for (let i = index; i < endIndex; i += 1) {
          try {
            hljs.highlightElement(unhighlightedCodeBlocks[i])
          } catch (error) {
            console.warn('代码高亮失败:', error)
          }
        }

        index = endIndex
        if (index < unhighlightedCodeBlocks.length) {
          requestAnimationFrame(processBatch)
        }
      }

      processBatch()
    })
  }, 300)

  // 懒加载 mermaid（仅在消息含流程图时才加载，避免拖大主聊天包）
  let mermaidModulePromise = null
  const getMermaid = () => {
    if (!mermaidModulePromise) {
      mermaidModulePromise = import('mermaid').then(mod => {
        const mermaid = mod.default
        mermaid.initialize({
          startOnLoad: false,
          securityLevel: 'strict',
          theme: 'neutral',
          fontFamily: 'var(--code-font-family, system-ui)'
        })
        return mermaid
      })
    }
    return mermaidModulePromise
  }

  const markMermaidWidth = (block, renderEl) => {
    const svg = renderEl.querySelector('svg')
    if (!svg) return

    const viewBoxWidth = getSvgViewBoxSize(svg)?.width
    const attributeWidth = parseAbsoluteSvgLength(svg.getAttribute('width'))
    const rectWidth = svg.getBoundingClientRect().width
    const naturalWidth = viewBoxWidth > 0
      ? viewBoxWidth
      : attributeWidth > 0
        ? attributeWidth
        : rectWidth
    const availableWidth = renderEl.clientWidth || block.clientWidth || 0
    const isWide = availableWidth > 0 && naturalWidth > availableWidth
    const readableWidth = Math.min(
      1400,
      Math.max(naturalWidth, Math.min(availableWidth * 1.15, 900))
    )

    block.classList.toggle('mermaid-wide', isWide)
    if (isWide) {
      svg.style.setProperty('--mermaid-natural-width', `${readableWidth}px`)
    } else {
      svg.style.removeProperty('--mermaid-natural-width')
    }
  }

  const fallbackMermaidBlock = block => {
    block.classList.remove('mermaid-rendering', 'mermaid-rendered', 'mermaid-wide')
    block.classList.add('mermaid-error')
    block.querySelector('.mermaid-zoom-btn')?.remove()
    block.querySelector('.mermaid-source')?.classList.remove('hidden')
    const toggleBtn = block.querySelector('.mermaid-toggle-btn')
    if (toggleBtn) toggleBtn.textContent = '隐藏源码'
  }

  const isCurrentMermaidBlock = block =>
    block.isConnected &&
    Boolean(contentRef.value?.contains(block))

  const isCurrentMermaidRender = (block, renderEl) =>
    isCurrentMermaidBlock(block) && block.querySelector('.mermaid-render') === renderEl

  const clearStaleMermaidRendering = (block, renderEl) => {
    if (isCurrentMermaidBlock(block) && block.querySelector('.mermaid-render') !== renderEl) {
      block.classList.remove('mermaid-rendering')
    }
  }

  const renderMermaid = useThrottleFn(() => {
    if (!contentRef.value) return

    const blocks = contentRef.value.querySelectorAll('.mermaid-block:not(.mermaid-rendered):not(.mermaid-error):not(.mermaid-rendering)')
    if (blocks.length === 0) return
    blocks.forEach(block => block.classList.add('mermaid-rendering'))

    requestAnimationFrame(async () => {
      let mermaid
      try {
        mermaid = await getMermaid()
      } catch (error) {
        console.error('Mermaid 加载失败:', error)
        blocks.forEach(block => {
          if (isCurrentMermaidBlock(block)) fallbackMermaidBlock(block)
        })
        return
      }

      for (const block of blocks) {
        if (!isCurrentMermaidBlock(block)) continue
        const sourceEl = block.querySelector('.mermaid-source code')
        if (!sourceEl) {
          fallbackMermaidBlock(block)
          continue
        }

        // 直接读取解码后的源码文本（textContent 已还原 HTML 实体）
        const source = sourceEl.textContent || ''
        const renderEl = block.querySelector('.mermaid-render')
        if (!renderEl) {
          fallbackMermaidBlock(block)
          continue
        }
        const id = `mermaid-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`

        try {
          const { svg } = await mermaid.render(id, source)
          if (!isCurrentMermaidRender(block, renderEl)) {
            clearStaleMermaidRendering(block, renderEl)
            continue
          }
          renderEl.innerHTML = svg
          markMermaidWidth(block, renderEl)
          block.classList.remove('mermaid-rendering', 'mermaid-error')
          block.classList.add('mermaid-rendered')
        } catch (error) {
          console.warn('Mermaid 渲染失败，回退为源码展示:', error)
          if (isCurrentMermaidRender(block, renderEl)) {
            fallbackMermaidBlock(block)
          } else {
            clearStaleMermaidRendering(block, renderEl)
          }
        }
      }
    })
  }, 300)

  // 标记超出容器宽度的表格（.table-overflow），用于显示"左右滑动查看更多"提示
  const markTableOverflow = () => {
    if (!contentRef.value) return

    requestAnimationFrame(() => {
      contentRef.value.querySelectorAll('.table-wrapper').forEach(wrapper => {
        const hasOverflow = wrapper.scrollWidth > wrapper.clientWidth + 1
        wrapper.classList.toggle('table-overflow', hasOverflow)
      })
    })
  }

  const handleImageLoading = () => {
    if (!contentRef.value) return

    const images = contentRef.value.querySelectorAll('img[data-image-id]')
    images.forEach(img => {
      img.onload = null
      img.onerror = null

      const wrapper = img.closest('.image-wrapper')
      const markLoaded = () => {
        wrapper?.classList.remove('loading-wrapper')
        wrapper?.classList.add('loaded')
        delete img.dataset.retryAttempt
      }
      const markError = () => {
        // 失败重试：消化后端 429 限流 / 瞬时抖动。重试上限内保留骨架屏，避免误显示失败。
        const attempt = Number(img.dataset.retryAttempt || 0)
        if (attempt < IMAGE_MAX_RETRY) {
          img.dataset.retryAttempt = String(attempt + 1)
          setTimeout(() => {
            const current = img.src
            img.src = ''
            img.src = current
          }, IMAGE_RETRY_DELAYS[attempt] || 2000)
          return
        }

        wrapper?.classList.remove('loading-wrapper')
        wrapper?.classList.add('error-wrapper')
        img.alt = '图片加载失败'
      }

      if (img.complete && img.naturalHeight !== 0) {
        markLoaded()
        return
      }

      img.onload = markLoaded
      img.onerror = markError
    })
  }

  const copyMessageContent = async () => {
    let textToCopy = message.value?.content || ''

    if (!isUser.value) {
      const tempDiv = document.createElement('div')
      tempDiv.innerHTML = processedContent.value
      textToCopy = (tempDiv.textContent || tempDiv.innerText || '').replace(/\n\s*\n/g, '\n\n').trim()
    }

    return copyToClipboard(textToCopy)
  }

  const resetContentState = () => {
    if (contentRef.value) {
      const images = contentRef.value.querySelectorAll('img[data-image-id]')
      images.forEach(img => {
        img.onload = null
        img.onerror = null
      })
    }

    renderCache.clear()
    codeBlocksData.value = {}
  }

  return {
    copyMessageContent,
    handleContentClick,
    handleImageLoading,
    hasReasoning,
    reasoningHtml,
    reasoningExpanded,
    toggleReasoning,
    highlightCode,
    renderMermaid,
    markTableOverflow,
    processedContent,
    resetContentState
  }
}
