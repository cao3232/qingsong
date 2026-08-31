import { test } from 'node:test'
import assert from 'node:assert/strict'
import { formatLocalDateTime, historyCursorOf, mergeHistoryUnique, dayRangeOf } from './chatHistoryPager.js'

test('formatLocalDateTime 输出本地 ISO', () => {
  assert.equal(formatLocalDateTime(new Date(2026, 7, 30, 9, 5, 3)), '2026-08-30T09:05:03')
  assert.equal(formatLocalDateTime('2026-08-30T09:05:03'), '2026-08-30T09:05:03')
  assert.equal(formatLocalDateTime('garbage'), null)
  assert.equal(formatLocalDateTime(null), null)
})

test('historyCursorOf 取 lastMessageAt 优先，缺字段返回 null', () => {
  const cursor = historyCursorOf({ lastMessageAt: new Date(2026, 7, 30), createdAt: new Date(2026, 7, 1), sessionDbId: 42 })
  assert.deepEqual(cursor, { before: '2026-08-30T00:00:00', beforeId: 42 })
  // lastMessageAt 缺失时回退 createdAt
  const fallback = historyCursorOf({ lastMessageAt: null, createdAt: new Date(2026, 7, 1, 8, 0, 0), sessionDbId: 7 })
  assert.deepEqual(fallback, { before: '2026-08-01T08:00:00', beforeId: 7 })
  assert.equal(historyCursorOf({ lastMessageAt: new Date(), sessionDbId: null }), null)
  assert.equal(historyCursorOf(null), null)
})

test('mergeHistoryUnique 去重且保留先出现者', () => {
  const local = [{ id: 'a', tag: 'local' }, { id: 'b', tag: 'local-b' }]
  const server = [{ id: 'b', tag: 'server' }, { id: 'c' }]
  const merged = mergeHistoryUnique(local, server)
  assert.deepEqual(merged.map(i => i.id), ['a', 'b', 'c'])
  assert.equal(merged[1].tag, 'local-b')
  // 数字与字符串 id 归一比较
  assert.deepEqual(mergeHistoryUnique([{ id: 1 }], [{ id: '1' }]).length, 1)
})

test('dayRangeOf 输出当日左闭右开区间', () => {
  assert.deepEqual(dayRangeOf('2026-08-30'), { start: '2026-08-30T00:00:00', end: '2026-08-31T00:00:00' })
  assert.deepEqual(dayRangeOf('2026-12-31'), { start: '2026-12-31T00:00:00', end: '2027-01-01T00:00:00' })
  assert.deepEqual(dayRangeOf('bad'), { start: null, end: null })
  assert.deepEqual(dayRangeOf(null), { start: null, end: null })
})
