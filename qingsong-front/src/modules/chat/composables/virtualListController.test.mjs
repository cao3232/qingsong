import assert from 'node:assert/strict'
import test from 'node:test'
import { registerVirtualListController, getVirtualListController } from './virtualListController.js'

test('注册后可通过 getVirtualListController 取回同一控制器', () => {
  const controller = { scrollToIndex: () => {}, scrollToBottom: () => {} }
  const unregister = registerVirtualListController(controller)
  assert.equal(getVirtualListController(), controller)
  unregister()
  assert.equal(getVirtualListController(), null)
})

test('后注册覆盖前注册；先注销旧控制器不影响新控制器', () => {
  const first = { name: 'first' }
  const second = { name: 'second' }
  const unregisterFirst = registerVirtualListController(first)
  const unregisterSecond = registerVirtualListController(second)
  assert.equal(getVirtualListController(), second)
  unregisterFirst()
  assert.equal(getVirtualListController(), second)
  unregisterSecond()
  assert.equal(getVirtualListController(), null)
})

test('同一个控制器重复注销是幂等的', () => {
  const controller = { name: 'idempotent' }
  const unregister = registerVirtualListController(controller)
  unregister()
  unregister()
  assert.equal(getVirtualListController(), null)
})
