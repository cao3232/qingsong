import { CJK_RADICAL_SUPPLEMENT } from './pdfReaderRadicals.js'

// 句末标点，用于判断一个分句是否为完整句尾
const SENTENCE_ENDERS = '。！？!?；;…'

// 查找从目标页开始朗读时的起始段索引。
// 不是简单返回 page >= target 的第一个段，而是向前回退到完整句边界，
// 避免自动跳转页面/用户翻页播放时从上一页延续句子的半截开始读。
export const findStartIndexFromPage = (segments, page) => {
  const list = segments || []
  const target = Number(page) || 1
  const firstOnOrAfter = list.findIndex(item => Number(item?.page) >= target)
  if (firstOnOrAfter < 0) return Math.max(0, list.length - 1)
  if (firstOnOrAfter === 0) return 0

  let index = firstOnOrAfter

  // 先回退到目标页的第一个段，避免从页中间开始
  while (
    index > 0 &&
    Number(list[index]?.page) === target &&
    Number(list[index - 1]?.page) === target
  ) {
    index -= 1
  }

  // 再向前回退一步到目标页前一页的最后一个段，组成完整句。
  // 如果该段前已出现句末标点，说明句子完整，不需要回退。
  if (index > 0) {
    const prev = list[index - 1]
    const curr = list[index]
    const prevEndsWithPunct = SENTENCE_ENDERS.includes((prev?.content || '').trim().slice(-1))
    const isPrevPageAdjacent = Number(prev.page) === Number(curr.page) - 1
    if (!prevEndsWithPunct && isPrevPageAdjacent) {
      index -= 1
    }
  }

  return index
}

const cleanStr = item => String(item?.str ?? '').replace(/\s+/g, ' ').trim()

const hasLayoutInfo = items => items.some(item => Number.isFinite(Number(item?.transform?.[5])))

const normalizeText = text =>
  text
    .replace(/[ \t]+/g, ' ')
    .replace(/ ?\n ?/g, '\n')
    .replace(/\n{2,}/g, '\n')
    .trim()

const toEntries = items =>
  items
    .map(item => {
      const str = cleanStr(item)
      if (!str) return null
      const rawX = Number(item?.transform?.[4])
      const rawY = Number(item?.transform?.[5])
      return {
        str,
        x: Number.isFinite(rawX) ? rawX : 0,
        y: Number.isFinite(rawY) ? rawY : 0,
        width: Number(item?.width) || 0
      }
    })
    .filter(Boolean)

// 内容流是否基本自上而下（几乎不向上回跳）—— 是则 PDF 自带顺序可直接使用
const isContentOrderStable = items => {
  let prevY = null
  let meaningful = 0
  for (const item of items) {
    if (!cleanStr(item)) continue
    const y = Number(item?.transform?.[5])
    if (!Number.isFinite(y)) continue
    meaningful += 1
    if (prevY != null && y > prevY + 2) return false
    prevY = y
  }
  return meaningful > 1
}

const extractSequentially = items => {
  let text = ''
  for (const item of items || []) {
    if (item?.hasEOL && !cleanStr(item)) {
      text += '\n'
      continue
    }
    const value = cleanStr(item)
    if (!value) continue
    text += value
    if (item?.hasEOL) text += '\n'
  }
  return normalizeText(text)
}

// 优先路径：直接使用 PDF 携带的内容顺序，仅按 y 变化断行、按 x 间距补空格
const extractByContentOrder = items => {
  const entries = toEntries(items)
  const textLines = []
  let lineText = ''
  let lineY = null
  let prevEndX = null

  for (const entry of entries) {
    if (lineY != null && Math.abs(entry.y - lineY) > 2) {
      if (lineText) textLines.push(lineText)
      lineText = ''
      prevEndX = null
    }
    const gap = prevEndX != null ? entry.x - prevEndX : null
    if (gap != null && gap > 1.5 && lineText) lineText += ' '
    lineText += entry.str
    prevEndX = entry.x + entry.width
    lineY = entry.y
  }
  if (lineText) textLines.push(lineText)

  return normalizeText(textLines.join('\n'))
}

const groupLinesByY = (entries, lineTolerance = 2) => {
  const lines = []
  for (const entry of entries) {
    let target = null
    for (const line of lines) {
      if (Math.abs(line.y - entry.y) <= lineTolerance) {
        target = line
        break
      }
    }
    if (!target) {
      target = { y: entry.y, items: [] }
      lines.push(target)
    }
    target.items.push(entry)
  }
  lines.sort((a, b) => b.y - a.y)
  for (const line of lines) line.items.sort((a, b) => a.x - b.x)
  return lines
}

const renderLineText = line => {
  let lineText = ''
  let prevEndX = null
  for (const item of line.items) {
    const gap = prevEndX != null ? item.x - prevEndX : null
    if (gap != null && gap > 1.5 && lineText) lineText += ' '
    lineText += item.str
    prevEndX = item.x + item.width
  }
  return lineText
}

// 检测多栏布局：多数行内部存在位置集中的大横向间隙（栏沟槽），返回沟槽中心 x；无则返回 null
const findColumnGutter = lines => {
  const gutterCenters = []
  for (const line of lines) {
    const items = line.items
    if (items.length < 2) continue
    let maxGap = 0
    let maxGapCenter = null
    let prevEndX = null
    for (const item of items) {
      if (prevEndX != null) {
        const gap = item.x - prevEndX
        if (gap > maxGap) {
          maxGap = gap
          maxGapCenter = (item.x + prevEndX) / 2
        }
      }
      prevEndX = item.x + item.width
    }
    if (maxGapCenter != null && maxGap > 15) gutterCenters.push(maxGapCenter)
  }
  if (gutterCenters.length < Math.max(2, Math.ceil(lines.length / 2))) return null
  gutterCenters.sort((a, b) => a - b)
  const median = gutterCenters[Math.floor(gutterCenters.length / 2)]
  const spread = gutterCenters.reduce((sum, g) => sum + Math.abs(g - median), 0) / gutterCenters.length
  if (median <= 0 || spread > median * 0.25) return null
  return median
}

// 多栏按列读取：先读完左栏整列，再读右栏整列，避免逐行跨栏导致的跳读/回读
const extractColumns = (lines, gutter) => {
  const leftLines = []
  const rightLines = []
  for (const line of lines) {
    const left = line.items.filter(item => item.x + item.width / 2 < gutter)
    const right = line.items.filter(item => item.x + item.width / 2 >= gutter)
    if (left.length) leftLines.push({ y: line.y, items: left })
    if (right.length) rightLines.push({ y: line.y, items: right })
  }
  const leftText = leftLines.map(renderLineText).join('\n')
  const rightText = rightLines.map(renderLineText).join('\n')
  return normalizeText([leftText, rightText].filter(Boolean).join('\n'))
}

const extractByLayout = items => {
  const lines = groupLinesByY(toEntries(items))
  const gutter = findColumnGutter(lines)
  if (gutter != null) return extractColumns(lines, gutter)
  return normalizeText(lines.map(renderLineText).join('\n'))
}

// 页内是否存在跨栏布局（多数行内有位置集中的沟槽）
const isMultiColumn = items => {
  const lines = groupLinesByY(toEntries(items))
  return findColumnGutter(lines) != null
}

export const extractPdfPageText = items => {
  const list = items || []
  if (!hasLayoutInfo(list)) return extractSequentially(list)
  // 多栏文档必须按列读取：PDF 自带内容顺序与逐行重排都可能造成跨栏跳读/回读
  if (isMultiColumn(list)) return extractByLayout(list)
  // PDF 自带内容顺序正常时优先使用“携带格式”，只有内容流错乱才按坐标重排
  if (isContentOrderStable(list)) return extractByContentOrder(list)
  return extractByLayout(list)
}

// 次级标点（句内停顿点）：超长句按它们就近切分，避免从字符中间硬切导致读半句
const CLAUSE_PUNCT = '，、：；,.:;…'

// 在 text 前 limit 长度内找最靠后的句内标点切点（含标点），找不到返回 -1
const findClauseCut = (text, limit) => {
  for (let index = Math.min(limit, text.length - 1); index > 0; index -= 1) {
    if (CLAUSE_PUNCT.includes(text[index])) return index + 1
  }
  return -1
}

// 超长句按句内标点拆分为多段，兜底才按字数硬切
const splitLongSentence = (sentence, limit) => {
  const parts = []
  let rest = sentence
  while (rest.length > limit) {
    const cut = findClauseCut(rest, limit)
    if (cut > 0) {
      parts.push(rest.slice(0, cut).trim())
      rest = rest.slice(cut).trim()
    } else {
      parts.push(rest.slice(0, limit))
      rest = rest.slice(limit)
    }
  }
  if (rest) parts.push(rest)
  return parts.filter(Boolean)
}

// 分段方式：'auto'（默认，短行按行、长行按标点混合）| 'line'（按行）| 'punct'（按标点）
// 段尾兜底收尾：不以句末标点结尾的段追加句号，避免 TTS 对无标点结尾的长段截尾（最后几个字不生成）
const ensureSentenceEnd = text => {
  const value = String(text || '').trim()
  if (!value) return value
  return SENTENCE_ENDERS.includes(value[value.length - 1]) ? value : `${value}。`
}

export const splitPdfTextIntoSegments = (text, maxChars = 500, splitMode = 'auto') => {
  const limit = Math.max(1, Number(maxChars) || 500)
  const mode = ['line', 'punct'].includes(splitMode) ? splitMode : 'auto'
  const cleaned = sanitizeTtsText(text)
  let sentences = []

  if (mode === 'line') {
    // 按行分割：行尾作为断点（适合每行本身就是完整一句，如诗歌/短句列表）
    sentences = cleaned ? collectSentences(cleaned, LINE_ENDERS) : []
  } else if (mode === 'punct') {
    // 按标点：忽略排版换行，只按句末标点分句，保证段首段尾是完整句子
    const input = cleaned.replace(/\s*\r?\n\s*/g, ' ')
    sentences = input ? collectSentences(input, SENTENCE_ENDERS) : []
  } else {
    // 自动混合：短字为单行、长字为文字
    sentences = collectAutoSentences(cleaned)
  }

  const segments = []
  let current = ''

  for (const sentence of sentences) {
    // auto 模式下的独立短行（诗歌/标题）：作为独立段，不与其他句子拼接
    const standalone = Boolean(sentence && typeof sentence === 'object' && sentence.standalone)
    const text = standalone ? sentence.text : String(sentence)

    if (standalone) {
      if (current) {
        segments.push(ensureSentenceEnd(current))
        current = ''
      }
      // 独立短行若仍超过上限（如测试中的超长无标点短句），仍需兜底硬切
      if (text.length > limit) {
        segments.push(...splitLongSentence(text, limit).map(ensureSentenceEnd))
      } else {
        segments.push(text)
      }
      continue
    }

    if (text.length > limit) {
      if (current) {
        segments.push(ensureSentenceEnd(current))
        current = ''
      }
      segments.push(...splitLongSentence(text, limit).map(ensureSentenceEnd))
      continue
    }

    if (current && current.length + text.length > limit) {
      segments.push(ensureSentenceEnd(current))
      current = text
    } else {
      current += text
    }
  }

  if (current) segments.push(ensureSentenceEnd(current))
  return segments
}

// 发送给 TTS 前净化：解码 HTML 实体，规整康熙部首/兼容部首为统一汉字，
// 去掉控制字符/零宽字符/软连字符/替换符/私用区字形
export const sanitizeTtsText = text => {
  const named = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'", nbsp: ' ' }
  return String(text || '')
    .replace(/&#(\d+);/g, (_, n) => {
      const code = Number(n)
      return code > 0 && code <= 0x10ffff ? String.fromCodePoint(code) : ''
    })
    .replace(/&#x([0-9a-fA-F]+);/g, (_, h) => {
      const code = parseInt(h, 16)
      return code > 0 && code <= 0x10ffff ? String.fromCodePoint(code) : ''
    })
    .replace(/&(amp|lt|gt|quot|apos|nbsp);/g, (m, name) => named[name])
    .replace(/[\u2E80-\u2EFF\u2F00-\u2FD5]/g, ch => {
      const nfkc = ch.normalize('NFKC')
      if (nfkc !== ch) return nfkc
      return CJK_RADICAL_SUPPLEMENT[ch.codePointAt(0)] || ch
    })
    .replace(
      /[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F\u200B\u200C\u200D\uFEFF\u00AD\uFFFD\uE000-\uF8FF]/g,
      ''
    )
    .replace(/[ \t]+/g, ' ')
    .trim()
}

// 按行分割时的结束符：行尾也作为断点
const LINE_ENDERS = SENTENCE_ENDERS + '\n'
// 自动混合模式下的"短行"判定：≤ 此字符数的行视为短行（诗句/小标题/短句），单独成句。
// 阈值取小（10），避免把正文折行的续行误判为短行而切出半句。
const SHORT_LINE_LIMIT = 10

const collectSentences = (text, enders = SENTENCE_ENDERS) => {
  const sentences = []
  let buf = ''
  for (const ch of text) {
    buf += ch
    if (enders.includes(ch)) {
      const sentence = buf.trim()
      if (sentence) sentences.push(sentence)
      buf = ''
    }
  }
  const tail = buf.trim()
  if (tail) sentences.push(tail)
  return sentences
}

// 把 buf 按句末标点切分成完整句子，返回 { parts: 完整句子, rest: 未完成残留 }
const flushByPunct = buf => {
  const parts = []
  let acc = ''
  for (const ch of buf) {
    acc += ch
    if (SENTENCE_ENDERS.includes(ch)) {
      const sentence = acc.trim()
      if (sentence) parts.push(sentence)
      acc = ''
    }
  }
  return { parts, rest: acc }
}

// 自动混合分句：短行（诗歌/标题/短句）且无跨行残留时各自成句；
// 其余（长行正文折行、正文续行）跨行拼接，按句末标点断句，避免半句、顺序错乱。
const collectAutoSentences = text => {
  const sentences = []
  let buffer = ''
  const lines = String(text || '').split('\n')

  for (const raw of lines) {
    const line = raw.trim()
    if (!line) continue
    const endsWithPunct = SENTENCE_ENDERS.includes(line[line.length - 1])

    if (endsWithPunct) {
      // 以句末标点收尾：句子一定完整，立即消化
      const { parts, rest } = flushByPunct(buffer + line)
      sentences.push(...parts)
      buffer = rest.trim() ? rest : ''
    } else if (line.length <= SHORT_LINE_LIMIT && !buffer.trim()) {
      // 诗歌/标题式短句且无跨行残留：独立成句（standalone，作为独立段边界）
      sentences.push({ text: line, standalone: true })
    } else {
      // 长行或正文续行：跨行拼接，先消化内部完整句，残留留到下轮
      const { parts, rest } = flushByPunct(buffer + line + ' ')
      sentences.push(...parts)
      buffer = rest
    }
  }

  if (buffer.trim()) {
    const { parts, rest } = flushByPunct(buffer)
    sentences.push(...parts)
    if (rest.trim()) sentences.push(rest.trim())
  }

  return sentences.filter(Boolean)
}
