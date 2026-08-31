import assert from 'node:assert/strict'
import test from 'node:test'

import {
  isConnectionError,
  getConnectionErrorMessage,
  markNotified,
  isNotified,
  CONNECTION_ERROR_MESSAGE
} from './networkError.js'

test('502/503/504 网关错误判定为连接失败', () => {
  assert.equal(isConnectionError({ response: { status: 502 } }), true)
  assert.equal(isConnectionError({ response: { status: 503 } }), true)
  assert.equal(isConnectionError({ response: { status: 504 } }), true)
  assert.equal(isConnectionError({ status: 502 }), true)
})

test('网络层失败（无 response / ERR_NETWORK / TypeError / fetch 失败文案）判定为连接失败', () => {
  assert.equal(isConnectionError({}), true)
  assert.equal(isConnectionError({ code: 'ERR_NETWORK' }), true)
  assert.equal(isConnectionError({ name: 'TypeError' }), true)
  assert.equal(isConnectionError({ message: 'Failed to fetch' }), true)
  assert.equal(isConnectionError({ message: 'Network Error' }), true)
  assert.equal(isConnectionError({ message: 'load failed' }), true)
})

test('普通 HTTP 错误（401/403/500）不判定为连接失败', () => {
  assert.equal(isConnectionError({ response: { status: 401 } }), false)
  assert.equal(isConnectionError({ response: { status: 403 } }), false)
  assert.equal(isConnectionError({ response: { status: 500 } }), false)
})

test('空值不判定为连接失败', () => {
  assert.equal(isConnectionError(null), false)
  assert.equal(isConnectionError(undefined), false)
})

test('getConnectionErrorMessage 返回统一文案', () => {
  assert.equal(getConnectionErrorMessage(), CONNECTION_ERROR_MESSAGE)
  assert.equal(getConnectionErrorMessage(), '无法连接服务器，请检查网络或后端服务')
})

test('markNotified / isNotified 标记往返', () => {
  const error = new Error('x')
  assert.equal(isNotified(error), false)
  markNotified(error)
  assert.equal(isNotified(error), true)
})
