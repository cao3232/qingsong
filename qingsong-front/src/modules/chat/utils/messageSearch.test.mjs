import { test } from 'node:test'
import assert from 'node:assert/strict'
import { highlightKeyword } from './messageSearch.js'

test('highlightKeyword 大小写不敏感并保留原文', () => {
  assert.deepEqual(highlightKeyword('Hello World hello', 'hello'), [
    { text: 'Hello', mark: true },
    { text: ' World ', mark: false },
    { text: 'hello', mark: true }
  ])
})

test('highlightKeyword 无关键词原样返回', () => {
  assert.deepEqual(highlightKeyword('abc', ''), [{ text: 'abc', mark: false }])
  assert.deepEqual(highlightKeyword('abc', '  '), [{ text: 'abc', mark: false }])
  assert.deepEqual(highlightKeyword('', 'x'), [])
  assert.deepEqual(highlightKeyword(null, 'x'), [])
})

test('highlightKeyword 中文与连续命中', () => {
  assert.deepEqual(highlightKeyword('部署部署', '部署'), [
    { text: '部署', mark: true },
    { text: '部署', mark: true }
  ])
  assert.deepEqual(highlightKeyword('前面…关键词…后面', '关键词'), [
    { text: '前面…', mark: false },
    { text: '关键词', mark: true },
    { text: '…后面', mark: false }
  ])
})
