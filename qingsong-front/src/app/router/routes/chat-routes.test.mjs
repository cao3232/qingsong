import { test } from 'node:test'
import assert from 'node:assert'
import { createRouter, createMemoryHistory } from 'vue-router'
import { mainRoutes } from './main.routes.js'

const router = createRouter({
  history: createMemoryHistory(),
  routes: mainRoutes
})

test('/chat、/chat/:roleId、/chat/:roleId/:chatId 同一 name 且 params 正确', async () => {
  const r1 = router.resolve('/chat')
  assert.strictEqual(r1.name, 'AIChat')
  assert.strictEqual(r1.params.roleId, '')
  assert.strictEqual(r1.params.chatId, '')

  const r2 = router.resolve('/chat/3')
  assert.strictEqual(r2.name, 'AIChat')
  assert.strictEqual(r2.params.roleId, '3')
  assert.strictEqual(r2.params.chatId, '')

  const r3 = router.resolve('/chat/3/99')
  assert.strictEqual(r3.name, 'AIChat')
  assert.strictEqual(r3.params.roleId, '3')
  assert.strictEqual(r3.params.chatId, '99')

  // 三个形态 resolve 到同一 name → AppShell 组件 key 不变，不会重挂载
  assert.strictEqual(r1.name, r2.name)
  assert.strictEqual(r2.name, r3.name)
})
