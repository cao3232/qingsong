import test from 'node:test'
import assert from 'node:assert/strict'
import { extractPdfPageText, splitPdfTextIntoSegments, findStartIndexFromPage, sanitizeTtsText } from './pdfReaderText.js'

test('extractPdfPageText joins text items and preserves explicit line endings', () => {
  const items = [
    { str: '第一行', hasEOL: false },
    { str: '继续', hasEOL: true },
    { str: '第二行', hasEOL: false },
    { str: '   ', hasEOL: true },
    { str: '第三行', hasEOL: false }
  ]

  assert.equal(extractPdfPageText(items), '第一行继续\n第二行\n第三行')
})

test('extractPdfPageText sorts lines top-to-bottom and inserts spaces between separate words', () => {
  const items = [
    { str: '第二行', transform: [1, 0, 0, 1, 20, 100], width: 60 },
    { str: 'Hello', transform: [1, 0, 0, 1, 20, 200], width: 50 },
    { str: 'world', transform: [1, 0, 0, 1, 130, 200], width: 50 }
  ]

  assert.equal(extractPdfPageText(items), 'Hello world\n第二行')
})

test('extractPdfPageText does not insert spaces between adjacent CJK glyphs', () => {
  const items = [
    { str: '第一', transform: [1, 0, 0, 1, 10, 50], width: 40 },
    { str: '句话', transform: [1, 0, 0, 1, 50, 50], width: 40 }
  ]

  assert.equal(extractPdfPageText(items), '第一句话')
})

test('extractPdfPageText uses the PDF carried content order when the flow is stable', () => {
  const items = [
    { str: '第一行', transform: [1, 0, 0, 1, 20, 200], width: 60 },
    { str: '第二行', transform: [1, 0, 0, 1, 20, 100], width: 60 }
  ]

  assert.equal(extractPdfPageText(items), '第一行\n第二行')
})

test('extractPdfPageText reorders by position only when the content flow is scrambled', () => {
  const items = [
    { str: '第二行', transform: [1, 0, 0, 1, 20, 100], width: 60 },
    { str: '第一行', transform: [1, 0, 0, 1, 20, 200], width: 60 }
  ]

  assert.equal(extractPdfPageText(items), '第一行\n第二行')
})

test('splitPdfTextIntoSegments removes blank input and keeps sentence boundaries', () => {
  assert.deepEqual(
    splitPdfTextIntoSegments('第一句。第二句！\n第三句？', 6),
    ['第一句。', '第二句！', '第三句？']
  )
  assert.deepEqual(splitPdfTextIntoSegments('  \n  ', 20), [])
})

test('splitPdfTextIntoSegments hard-splits a sentence longer than the limit', () => {
  // 无标点长句被兜底硬切，并在段尾追加句号，避免 TTS 对无标点结尾长段截尾
  assert.deepEqual(splitPdfTextIntoSegments('abcdefghij', 4), ['abcd。', 'efgh。', 'ij。'])
})

test('splitPdfTextIntoSegments preserves every non-whitespace character', () => {
  const text = '。第一句！第二句。\n第三句…第四句？'
  const segments = splitPdfTextIntoSegments(text, 10)
  const joined = segments.join('')
  assert.equal(joined.replace(/\s+/g, ''), text.replace(/\s+/g, ''))
})

test('splitPdfTextIntoSegments keeps leading punctuation instead of dropping it', () => {
  assert.deepEqual(splitPdfTextIntoSegments('。ABC', 10), ['。ABC'])
})

test('sanitizeTtsText decodes HTML entities and removes control/zero-width chars', () => {
  assert.equal(sanitizeTtsText('a&amp;b&#39;c&#x4E2D;\u200Bx\u00ADy'), "a&b'c中xy")
})

test('sanitizeTtsText converts nbsp to space and collapses whitespace', () => {
  assert.equal(sanitizeTtsText('hello&nbsp;world  \t X'), 'hello world X')
})

test('sanitizeTtsText strips replacement chars and private-use glyphs', () => {
  assert.equal(sanitizeTtsText('A\uFFFD\uE000B'), 'AB')
})

test('sanitizeTtsText normalizes Kangxi radicals and CJK radical supplements to standard Hanzi', () => {
  assert.equal(sanitizeTtsText('⼈⽣东⻄⻓⻔⻢⻥'), '人生东西长门马鱼')
})

test('sanitizeTtsText normalizes the full paragraph of hidden radical chars', () => {
  const input =
    '这已经不仅是⼈与⼈之间的关系，⽽是⼀种操纵游戏，其中有赢家、输家，和道德上的受难者。责任感过强是⼀条失败之路。记住：你不⽋任何⼈任何东⻄。'
  const result = sanitizeTtsText(input)
  assert.ok(result.includes('人与人之间的关系'))
  assert.ok(result.includes('而是一种操纵游戏'))
  assert.ok(result.includes('责任感过强是一条失败之路'))
  assert.ok(result.includes('你不欠任何人任何东西'))
  assert.ok(!result.includes('⼈') && !result.includes('⽽') && !result.includes('⻄') && !result.includes('⽋'))
})

test('sanitizeTtsText keeps fullwidth CJK punctuation unchanged', () => {
  assert.equal(sanitizeTtsText('你好，世界！'), '你好，世界！')
})

test('findStartIndexFromPage returns the first segment at or after a page', () => {
  const segments = [
    { page: 1, content: 'a' },
    { page: 1, content: 'b' },
    { page: 2, content: 'c' },
    { page: 3, content: 'd' }
  ]
  assert.equal(findStartIndexFromPage(segments, 1), 0)
  // page 2 的前一段（page 1 的 'b'）没有句末标点，回退一步组成完整句
  assert.equal(findStartIndexFromPage(segments, 2), 1)
  // page 3 的前一段（page 2 的 'c'）没有句末标点，回退一步组成完整句
  assert.equal(findStartIndexFromPage(segments, 3), 2)
})

test('findStartIndexFromPage stays at the page boundary when previous segment ends with punctuation', () => {
  const segments = [
    { page: 1, content: 'a。' },
    { page: 2, content: 'b' },
    { page: 3, content: 'c' }
  ]
  // page 1 的 'a。' 以句末标点结尾，page 2 不需要回退
  assert.equal(findStartIndexFromPage(segments, 2), 1)
  // page 2 的 'b' 没有句末标点，page 3 回退一步到 page 2
  assert.equal(findStartIndexFromPage(segments, 3), 1)
})

test('findStartIndexFromPage falls back to the last segment when page is past all text', () => {
  const segments = [
    { page: 1, content: 'a' },
    { page: 2, content: 'b' }
  ]
  assert.equal(findStartIndexFromPage(segments, 99), 1)
  assert.equal(findStartIndexFromPage(segments, 0), 0)
  assert.equal(findStartIndexFromPage([], 1), 0)
})
