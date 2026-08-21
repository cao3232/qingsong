import { CJK_RADICAL_SUPPLEMENT } from './pdfReaderRadicals.js'

export const findStartIndexFromPage = (segments, page) => {
  const list = segments || []
  const index = list.findIndex(item => Number(item?.page) >= Number(page))
  return index >= 0 ? index : Math.max(0, list.length - 1)
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

const extractByLayout = items => {
  const entries = toEntries(items)

  const lineTolerance = 2
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

  const text = lines
    .map(line => {
      let lineText = ''
      let prevEndX = null
      for (const item of line.items) {
        const gap = prevEndX != null ? item.x - prevEndX : null
        if (gap != null && gap > 1.5 && lineText) lineText += ' '
        lineText += item.str
        prevEndX = item.x + item.width
      }
      return lineText
    })
    .join('\n')

  return normalizeText(text)
}

export const extractPdfPageText = items => {
  const list = items || []
  if (!hasLayoutInfo(list)) return extractSequentially(list)
  // PDF 自带内容顺序正常时优先使用“携带格式”，只有内容流错乱才按坐标重排
  if (isContentOrderStable(list)) return extractByContentOrder(list)
  return extractByLayout(list)
}

export const splitPdfTextIntoSegments = (text, maxChars = 500) => {
  const limit = Math.max(1, Number(maxChars) || 500)
  const input = sanitizeTtsText(text)
  const sentences = input ? collectSentences(input) : []
  const segments = []
  let current = ''

  for (const sentence of sentences) {
    if (sentence.length > limit) {
      if (current) {
        segments.push(current)
        current = ''
      }
      for (let index = 0; index < sentence.length; index += limit) {
        segments.push(sentence.slice(index, index + limit))
      }
      continue
    }

    if (current && current.length + sentence.length > limit) {
      segments.push(current)
      current = sentence
    } else {
      current += sentence
    }
  }

  if (current) segments.push(current)
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

const SENTENCE_ENDERS = '。！？!?；;\n…'

const collectSentences = text => {
  const sentences = []
  let buf = ''
  for (const ch of text) {
    buf += ch
    if (SENTENCE_ENDERS.includes(ch)) {
      const sentence = buf.trim()
      if (sentence) sentences.push(sentence)
      buf = ''
    }
  }
  const tail = buf.trim()
  if (tail) sentences.push(tail)
  return sentences
}
