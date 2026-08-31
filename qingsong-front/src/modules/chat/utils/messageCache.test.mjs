import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createMessageCache } from './messageCache.js'

test('LRU 超容量淘汰最久未使用项', () => {
  const cache = createMessageCache(2)
  cache.set('a', [1]); cache.set('b', [2])
  cache.get('a')              // touch a，b 变成最久未用
  cache.set('c', [3])
  assert.equal(cache.get('b'), undefined)
  assert.deepEqual(cache.get('a'), [1])
  assert.equal(cache.size, 2)
})

test('重复 set 同键不重复计数', () => {
  const cache = createMessageCache(2)
  cache.set('a', [1]); cache.set('a', [2])
  assert.equal(cache.size, 1)
  assert.deepEqual(cache.get('a'), [2])
})

test('数字键按字符串归一', () => {
  const cache = createMessageCache(8)
  cache.set(123, ['x'])
  assert.deepEqual(cache.get('123'), ['x'])
})

test('remove 与 clear', () => {
  const cache = createMessageCache(8)
  cache.set('a', [1]); cache.remove('a')
  assert.equal(cache.get('a'), undefined)
  cache.set('b', [2]); cache.clear()
  assert.equal(cache.size, 0)
})
