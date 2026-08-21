import test from 'node:test'
import assert from 'node:assert/strict'
import { buildPdfOutline } from './pdfReaderOutline.js'

test('buildPdfOutline maps outline dest to page numbers', async () => {
  const items = [
    { title: '第一章', dest: [{ num: 0, gen: 0 }] },
    { title: '第二章', dest: [{ num: 5, gen: 0 }] }
  ]
  const outline = await buildPdfOutline(items, async dest => dest[0].num + 1)
  assert.deepEqual(outline, [
    { title: '第一章', page: 1, children: [] },
    { title: '第二章', page: 6, children: [] }
  ])
})

test('buildPdfOutline preserves nested children and depth order', async () => {
  const items = [
    {
      title: '第一章',
      dest: [0],
      items: [
        { title: '1.1', dest: [1] },
        { title: '1.2', dest: [2], items: [{ title: '1.2.1', dest: [3] }] }
      ]
    }
  ]
  const outline = await buildPdfOutline(items, async dest => dest[0] + 1)
  assert.deepEqual(outline, [
    {
      title: '第一章',
      page: 1,
      children: [
        { title: '1.1', page: 2, children: [] },
        { title: '1.2', page: 3, children: [{ title: '1.2.1', page: 4, children: [] }] }
      ]
    }
  ])
})

test('buildPdfOutline skips external url links and unresolved dests', async () => {
  const items = [
    { title: '外部链接', url: 'https://example.com' },
    { title: '无法解析', dest: 'missing' },
    { title: '有效', dest: [7] }
  ]
  const outline = await buildPdfOutline(items, async dest => {
    if (typeof dest === 'string') return null
    return dest[0] + 1
  })
  assert.deepEqual(outline, [{ title: '有效', page: 8, children: [] }])
})

test('buildPdfOutline resolves named dest strings through resolver', async () => {
  const items = [{ title: '前言', dest: 'intro' }]
  const outline = await buildPdfOutline(items, async dest => (dest === 'intro' ? 2 : null))
  assert.deepEqual(outline, [{ title: '前言', page: 2, children: [] }])
})
