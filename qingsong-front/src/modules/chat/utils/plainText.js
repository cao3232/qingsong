// 提取消息纯文本，供语音合成（TTS）朗读时剥离 Markdown / HTML 标记
// 与渲染逻辑（useChatMessageContent）解耦：这里只负责"读出来像人话"。

const CODE_BLOCK_RE = /```[\s\S]*?```|`[^`\n]*`/g
const IMAGE_RE = /!\[([^\]]*)\]\([^)]*\)/g
const LINK_RE = /\[([^\]]*)\]\([^)]*\)/g
const HEADING_RE = /^\s{0,3}#{1,6}\s+/gm
const BLOCKQUOTE_RE = /^\s*>\s?/gm
const LIST_RE = /^\s*([-*+]|\d+[.)])\s+/gm
const HORIZONTAL_RULE_RE = /^\s*([-*_])\s*(?:\1\s*){2,}$/gm
const HTML_TAG_RE = /<[^>]*>/g
const MULTI_SPACE_RE = /[ \t]{2,}/g
const MULTI_NEWLINE_RE = /\n{3,}/g

// 去掉用于渲染的转义字符（如 \*、\#、\_），保留可读文本
const UNESCAPE_MARKDOWN_RE = /\\([\\`*_[\]{}()#+\-.!|>])/g

// —— Markdown 表格 → 可朗读文本 ——
// 表格行：以 | 开头且以 | 结尾
const isTableRow = line => /^\s*\|.*\|\s*$/.test(line)

// 分隔行（表头下一行的 --- / :---: 等）：去掉 | : 空格后只剩 - 视为分隔行
const isSeparatorRow = line => {
  const stripped = line.replace(/\|/g, '').replace(/:/g, '').replace(/\s/g, '')
  return stripped.length > 0 && /^-+$/.test(stripped)
}

const parseTableRow = line =>
  line.replace(/^\s*\|/, '').replace(/\|\s*$/, '').split('|').map(cell => cell.trim())

// 每个数据行先带列头朗读：如「姓名 张三，年龄 25」
const renderTableLines = tableLines => {
  const headers = parseTableRow(tableLines[0])
  const dataRows = tableLines.slice(1).filter(line => !isSeparatorRow(line))

  const sentences = dataRows.map(row => {
    const cells = parseTableRow(row)
    return headers
      .map((header, index) => {
        const cell = cells[index]
        return header || cell ? `${header} ${cell || ''}`.trim() : ''
      })
      .filter(Boolean)
      .join('，')
  })

  return sentences.filter(Boolean).join('。')
}

const convertTablesToSpeech = content => {
  const lines = content.split('\n')
  const output = []
  let index = 0

  while (index < lines.length) {
    if (isTableRow(lines[index])) {
      const tableLines = []
      while (index < lines.length && isTableRow(lines[index])) {
        tableLines.push(lines[index].trim())
        index += 1
      }
      if (tableLines.length >= 3) {
        output.push(renderTableLines(tableLines))
      } else {
        // 不成表格，原样保留
        output.push(...tableLines)
      }
    } else {
      output.push(lines[index])
      index += 1
    }
  }

  return output.join('\n')
}

export const extractPlainText = (content = '') => {
  if (!content) {
    return ''
  }

  const withTables = convertTablesToSpeech(String(content))

  return withTables
    .replace(CODE_BLOCK_RE, '')
    .replace(IMAGE_RE, '$1')
    .replace(LINK_RE, '$1')
    .replace(HEADING_RE, '')
    .replace(BLOCKQUOTE_RE, '')
    .replace(LIST_RE, '')
    .replace(HORIZONTAL_RULE_RE, '')
    .replace(HTML_TAG_RE, '')
    .replace(UNESCAPE_MARKDOWN_RE, '$1')
    .replace(MULTI_SPACE_RE, ' ')
    .replace(MULTI_NEWLINE_RE, '\n\n')
    .trim()
}

// 句子/段落分隔符：中文句号/问号/感叹号/分号/冒号 + 常见英文标点 + 换行
// 注意：切分必须保留这些标点（用捕获组），否则发给 TTS 的文本没有断句信号，朗读会失去停顿。
const SEGMENT_SPLIT_RE = /([。！？；：!?;\n]+)/

// MiMo TTS 单次合成文本长度有限，超长文本需分段合成。
// 按句子切块，尽量保持每段不超过 maxChars（在标点处断开，避免切碎句子）。
// 每个句块保留句末标点，让 TTS 能识别断句并生成自然停顿。
export const splitTextIntoSegments = (content = '', maxChars = 500) => {
  if (!content) {
    return []
  }

  // 折叠多余空格/制表符为单个空格，但保留换行（段落边界）：
  // 换行是 TTS 的断句/停顿信号，也用于让超长文本按段落自然分段；
  // 不能像之前把所有空白统一压成空格那样，把段落糊成一片。
  const normalized = String(content).replace(/[ \t]{2,}/g, ' ').replace(/\n{2,}/g, '\n').trim()
  if (!normalized) {
    return []
  }

  // 带捕获组 split：结果形如 [文本, 标点, 文本, 标点, ...]，把「文本+标点」并成一个完整句块
  const tokens = normalized.split(SEGMENT_SPLIT_RE)
  const sentences = []
  for (let i = 0; i < tokens.length; i += 2) {
    const sentence = `${tokens[i] || ''}${tokens[i + 1] || ''}`.trim()
    // 过滤纯标点空句块（如文本以句号开头产生的前导标点）
    if (sentence && /[^。！？；：!?;\n]/.test(sentence)) {
      sentences.push(sentence)
    }
  }

  const segments = []
  let current = ''

  const pushCurrent = () => {
    const trimmed = current.trim()
    if (trimmed) {
      segments.push(trimmed)
    }
    current = ''
  }

  for (const piece of sentences) {
    if (current && (current + ' ' + piece).length > maxChars) {
      pushCurrent()
    }

    if (piece.length > maxChars) {
      // 单个超长块（无标点可断）：按 maxChars 硬切
      let rest = piece
      while (rest.length > maxChars) {
        segments.push(rest.slice(0, maxChars))
        rest = rest.slice(maxChars)
      }
      current = rest
    } else {
      current = current ? `${current} ${piece}` : piece
    }
  }

  pushCurrent()
  return segments
}
