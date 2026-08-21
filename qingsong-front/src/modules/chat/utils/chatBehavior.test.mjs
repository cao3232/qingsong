import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

import {
  extractReasoning,
  resolveStreamResponse,
  shouldAutoFollowMessages,
  shouldShowMessageTimestamp
} from './chatBehavior.js'
import { consumeChatSseReader } from './chatSse.js'

const chatMessageSource = readFileSync(
  new URL('../components/ChatMessage.vue', import.meta.url),
  'utf8'
)
const chatWorkspaceSource = readFileSync(
  new URL('../components/ChatWorkspace.vue', import.meta.url),
  'utf8'
)
const useChatWorkspaceSource = readFileSync(
  new URL('../composables/useChatWorkspace.js', import.meta.url),
  'utf8'
)
const useAIChatPageSource = readFileSync(
  new URL('../composables/useAIChatPage.js', import.meta.url),
  'utf8'
)
const systemSettingSource = readFileSync(
  new URL('../../tools/pages/SystemSettingPage.vue', import.meta.url),
  'utf8'
)
const themeStoreSource = readFileSync(
  new URL('../../../stores/theme.js', import.meta.url),
  'utf8'
)

test('时间戳需要设置开启、时间有效且来源准确', () => {
  const timestamp = new Date()

  assert.equal(shouldShowMessageTimestamp({ enabled: true, timestamp, hasAccurateTimestamp: true }), true)
  assert.equal(shouldShowMessageTimestamp({ enabled: false, timestamp, hasAccurateTimestamp: true }), false)
  assert.equal(shouldShowMessageTimestamp({ enabled: true, timestamp: null, hasAccurateTimestamp: true }), false)
  assert.equal(shouldShowMessageTimestamp({ enabled: true, timestamp, hasAccurateTimestamp: false }), false)
})

test('自动跟随需要设置开启且用户位于底部附近', () => {
  assert.equal(shouldAutoFollowMessages({ enabled: true, distanceFromBottom: 20 }), true)
  assert.equal(shouldAutoFollowMessages({ enabled: true, distanceFromBottom: 160 }), false)
  assert.equal(shouldAutoFollowMessages({ enabled: false, distanceFromBottom: 0 }), false)
})

test('仅有未闭合 think 标签时正文保持为空', () => {
  assert.deepEqual(extractReasoning('<think>正在分析'), {
    reasoning: '正在分析',
    main: ''
  })
})

test('闭合 think 标签从最终正文中移除', () => {
  assert.deepEqual(extractReasoning('<think>分析过程</think>最终回答'), {
    reasoning: '分析过程',
    main: '最终回答'
  })
})

test('流读取失败进入请求错误流程而不是提交截断回复', async () => {
  assert.match(useAIChatPageSource, /import \{[\s\S]*consumeChatSseReader[\s\S]*\} from ['"]\.\.\/utils\/chatSse\.js['"]/)
  assert.match(
    useAIChatPageSource,
    /consumeChatSseReader\(reader,\s*\{/
  )

  const streamError = new Error('stream interrupted')
  let readCount = 0
  let cancelled = false
  const reader = {
    async read() {
      readCount += 1
      if (readCount === 1) {
        return {
          done: false,
          value: new TextEncoder().encode('event: chunk\ndata: {"content":"partial"}\n\n')
        }
      }
      throw streamError
    },
    async cancel() {
      cancelled = true
    }
  }

  await assert.rejects(consumeChatSseReader(reader), error => error === streamError)
  assert.equal(cancelled, true)
})

test('模型返回合法 JSON 时保留完整原文', () => {
  const content = '{"content":"这是模型要求输出的数据","items":[1,2]}'

  assert.deepEqual(resolveStreamResponse(content), {
    content,
    errorMessage: ''
  })
})

test('接口错误信封提取错误消息', () => {
  assert.deepEqual(resolveStreamResponse('{"ok":0,"msg":"模型不可用"}'), {
    content: '模型不可用',
    errorMessage: '模型不可用'
  })
})

test('用户消息和 AI 消息共用系统时间戳设置', () => {
  assert.match(chatMessageSource, /shouldShowMessageTimestamp/)
  assert.match(chatMessageSource, /user-message-time[\s\S]*?formatTime\(message\.timestamp\)/)
  assert.match(chatMessageSource, /message-time[\s\S]*?formatTime\(message\.timestamp\)/)
})

test('聊天工作区按系统设置持续跟随新消息和流式内容', () => {
  assert.match(chatWorkspaceSource, /autoScroll:\s*computed\(\(\) => themeStore\.config\.autoScroll\)/)
  assert.match(useChatWorkspaceSource, /shouldAutoFollowMessages/)
  assert.match(useChatWorkspaceSource, /props\.currentMessages\[props\.currentMessages\.length - 1\]\?\.content/)
})

test('系统设置移除无效动画开关并清理旧配置', () => {
  assert.doesNotMatch(systemSettingSource, /启用动画效果|themeStore\.config\.enableAnimations/)
  assert.doesNotMatch(themeStoreSource, /['"]--enable-animations['"]/)
  assert.match(themeStoreSource, /delete parsedConfig\.enableAnimations/)
})
