import assert from 'node:assert/strict'
import test from 'node:test'

import { isAuthFailure } from './authResponse.js'

test('HTTP 401 is an authentication failure', () => {
  assert.equal(isAuthFailure({ status: 401 }), true)
})

test('Sa-Token invalid codes are authentication failures', () => {
  assert.equal(isAuthFailure({ payload: { code: 11012, msg: 'token 无效' } }), true)
})

test('normal business failures and network errors are not authentication failures', () => {
  assert.equal(isAuthFailure({ status: 403, payload: { msg: '无权限' } }), false)
  assert.equal(isAuthFailure({ status: 500, payload: { msg: '服务器内部错误' } }), false)
  assert.equal(isAuthFailure({ payload: { ok: 0, msg: '用户不存在' } }), false)
  assert.equal(isAuthFailure({}), false)
})
