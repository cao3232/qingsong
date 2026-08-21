import assert from 'node:assert/strict'
import test from 'node:test'

import {
  ChatSseProtocolError,
  ChatSseStreamError,
  applyChatSseEvent,
  assertChatSseCompleted,
  assertChatSseResponse,
  consumeChatSseReader,
  createChatSseParser,
  createChatSseState
} from './chatSse.js'

const encode = value => new TextEncoder().encode(value)

const createReader = chunks => {
  let index = 0
  let cancelled = false

  return {
    async read() {
      if (index >= chunks.length) return { done: true, value: undefined }
      return { done: false, value: chunks[index++] }
    },
    async cancel() {
      cancelled = true
    },
    get cancelled() {
      return cancelled
    }
  }
}

test('parser handles an event split across chunks', () => {
  const parser = createChatSseParser()

  assert.deepEqual(parser.push('event: chu'), [])
  assert.deepEqual(parser.push('nk\ndata: {"content":"hello"}\n\n'), [
    { type: 'chunk', data: { content: 'hello' } }
  ])
  assert.deepEqual(parser.finish(), [])
})

test('parser handles multiple CRLF events, comments, multiline data and Unicode', () => {
  const parser = createChatSseParser()
  const events = parser.push(
    ': keep-alive\r\nevent: chunk\r\ndata: {"content":\r\ndata: "你好\\n**bold**"}\r\n\r\n' +
      'event: future\r\ndata: {"ignored":true}\r\n\r\n' +
      'event: done\r\ndata: {"chatId":"chat-1","finishReason":"completed"}\r\n\r\n'
  )

  assert.deepEqual(events, [
    { type: 'chunk', data: { content: '你好\n**bold**' } },
    { type: 'done', data: { chatId: 'chat-1', finishReason: 'completed' } }
  ])
})

test('parser handles CR and mixed newline event boundaries', () => {
  const frames = [
    'event: chunk\rdata: {"content":"cr"}\r\r: next',
    'event: chunk\r\ndata: {"content":"crlf-cr"}\r\n\r: next',
    'event: chunk\ndata: {"content":"lf-cr"}\n\r: next'
  ]

  for (const [index, frame] of frames.entries()) {
    assert.deepEqual(createChatSseParser().push(frame), [
      { type: 'chunk', data: { content: ['cr', 'crlf-cr', 'lf-cr'][index] } }
    ])
  }
})

test('parser preserves a trailing CR across pushes until it can distinguish CR from CRLF', () => {
  const parser = createChatSseParser()

  assert.deepEqual(parser.push('event: chunk\r'), [])
  assert.deepEqual(parser.push('\ndata: {"content":"split-crlf"}\r\n\r\n'), [
    { type: 'chunk', data: { content: 'split-crlf' } }
  ])
})

test('parser finish drains a complete CR-delimited event whose final CR arrives at EOF', () => {
  const parser = createChatSseParser()

  assert.deepEqual(
    parser.push('event: chunk\rdata: {"content":"cr-eof"}\r\r'),
    []
  )
  assert.deepEqual(parser.finish(), [
    { type: 'chunk', data: { content: 'cr-eof' } }
  ])
})

test('parser parses error events and ignores unused SSE fields', () => {
  const parser = createChatSseParser()
  const events = parser.push(
    'id: 3\nretry: 1000\nevent: error\ndata: {"code":"CHAT_STREAM_ERROR","message":"生成失败"}\n\n'
  )

  assert.deepEqual(events, [
    { type: 'error', data: { code: 'CHAT_STREAM_ERROR', message: '生成失败' } }
  ])
})

test('parser rejects invalid JSON and missing or invalid required fields', () => {
  const invalidFrames = [
    'event: chunk\ndata: nope\n\n',
    'event: chunk\ndata: {}\n\n',
    'event: chunk\ndata: {"content":1}\n\n',
    'event: done\ndata: {"chatId":"chat-1"}\n\n',
    'event: done\ndata: {"chatId":"","finishReason":"completed"}\n\n',
    'event: error\ndata: {"code":"CHAT_STREAM_ERROR"}\n\n',
    'event: error\ndata: {"code":"","message":"failed"}\n\n'
  ]

  for (const frame of invalidFrames) {
    assert.throws(() => createChatSseParser().push(frame), ChatSseProtocolError)
  }
})

test('parser rejects done events whose finishReason is not completed', () => {
  assert.throws(
    () => createChatSseParser().push(
      'event: done\ndata: {"chatId":"chat-1","finishReason":"stopped"}\n\n'
    ),
    ChatSseProtocolError
  )
})

test('parser finish rejects a non-empty incomplete fragment', () => {
  const parser = createChatSseParser()
  parser.push('event: chunk\ndata: {"content":"partial"}')

  assert.throws(() => parser.finish(), ChatSseProtocolError)
})

test('parser finish ignores a trailing fragment containing only SSE comment lines', () => {
  const parser = createChatSseParser()
  parser.push(': keep-alive\r\n: stream still active')

  assert.deepEqual(parser.finish(), [])
})

test('state appends chunks and records completion chat id', () => {
  const state = createChatSseState()

  applyChatSseEvent(state, { type: 'chunk', data: { content: '你' } })
  applyChatSseEvent(state, { type: 'chunk', data: { content: '好' } })
  applyChatSseEvent(state, {
    type: 'done',
    data: { chatId: 'chat-2', finishReason: 'completed' }
  })

  assert.deepEqual(state, {
    content: '你好',
    reasoning: '',
    completed: true,
    chatId: 'chat-2',
    finishReason: 'completed',
    requestId: null,
    usage: null,
    nextSequence: null
  })
  assert.equal(assertChatSseCompleted(state), state)
})

test('state accumulates reasoning separately and records meta and usage', () => {
  const state = createChatSseState()

  applyChatSseEvent(state, {
    type: 'meta',
    data: { protocolVersion: 1, requestId: 'req-1', chatId: 'chat-1', sequence: 0 }
  })
  applyChatSseEvent(state, { type: 'reasoning', data: { requestId: 'req-1', sequence: 1, content: '先分析' } })
  applyChatSseEvent(state, { type: 'chunk', data: { requestId: 'req-1', sequence: 2, content: '答案' } })
  applyChatSseEvent(state, {
    type: 'done',
    data: {
      chatId: 'chat-1',
      requestId: 'req-1',
      sequence: 3,
      finishReason: 'completed',
      usage: { promptTokens: 10, completionTokens: 4, totalTokens: 14 }
    }
  })

  assert.equal(state.reasoning, '先分析')
  assert.equal(state.content, '答案')
  assert.equal(state.requestId, 'req-1')
  assert.deepEqual(state.usage, { promptTokens: 10, completionTokens: 4, totalTokens: 14 })
})

test('state rejects an out-of-order sequenced chunk before corrupting the answer', () => {
  const state = createChatSseState()

  applyChatSseEvent(state, {
    type: 'meta',
    data: { protocolVersion: 1, requestId: 'req-order', chatId: 'chat-order', sequence: 0 }
  })
  applyChatSseEvent(state, {
    type: 'chunk',
    data: { requestId: 'req-order', sequence: 1, content: '好问题，这其实是业务建模里一个' }
  })

  assert.throws(
    () => applyChatSseEvent(state, {
      type: 'chunk',
      data: { requestId: 'req-order', sequence: 3, content: '决策点。' }
    }),
    error => error instanceof ChatSseProtocolError && /sequence/.test(error.message)
  )
  assert.equal(state.content, '好问题，这其实是业务建模里一个')
})

test('state rejects a chunk before meta without appending content', () => {
  const state = createChatSseState()

  assert.throws(
    () => applyChatSseEvent(state, {
      type: 'chunk',
      data: { requestId: 'req-early', sequence: 1, content: '不能提前追加' }
    }),
    error => error instanceof ChatSseProtocolError && /meta/.test(error.message)
  )
  assert.equal(state.content, '')
  assert.equal(state.nextSequence, null)
})

test('state rejects missing sequence and mismatched request id after meta', () => {
  const missingSequenceState = createChatSseState()
  applyChatSseEvent(missingSequenceState, {
    type: 'meta',
    data: { protocolVersion: 1, requestId: 'req-guard', chatId: 'chat-guard', sequence: 0 }
  })
  assert.throws(
    () => applyChatSseEvent(missingSequenceState, {
      type: 'chunk',
      data: { requestId: 'req-guard', content: '不能追加' }
    }),
    error => error instanceof ChatSseProtocolError && /sequence/.test(error.message)
  )
  assert.equal(missingSequenceState.content, '')

  const mismatchedRequestState = createChatSseState()
  applyChatSseEvent(mismatchedRequestState, {
    type: 'meta',
    data: { protocolVersion: 1, requestId: 'req-guard', chatId: 'chat-guard', sequence: 0 }
  })
  assert.throws(
    () => applyChatSseEvent(mismatchedRequestState, {
      type: 'chunk',
      data: { requestId: 'req-other', sequence: 1, content: '不能追加' }
    }),
    error => error instanceof ChatSseProtocolError && /requestId/.test(error.message)
  )
  assert.equal(mismatchedRequestState.content, '')
})

test('state rejects a done event for a different chat', () => {
  const state = createChatSseState()
  applyChatSseEvent(state, {
    type: 'meta',
    data: { protocolVersion: 1, requestId: 'req-chat', chatId: 'chat-1', sequence: 0 }
  })

  assert.throws(
    () => applyChatSseEvent(state, {
      type: 'done',
      data: {
        requestId: 'req-chat',
        sequence: 1,
        chatId: 'chat-2',
        finishReason: 'completed'
      }
    }),
    error => error instanceof ChatSseProtocolError && /chatId/.test(error.message)
  )
  assert.equal(state.completed, false)
  assert.equal(state.nextSequence, 1)
})

test('consumer preserves the exact Chinese answer when sequenced chunks arrive in order', async () => {
  const expected = '好问题，这其实是业务建模里一个非常经典的决策点。先别急着问"什么时候插入"'
  const reader = createReader([encode(
    'event: meta\ndata: {"protocolVersion":1,"requestId":"req-text","chatId":"chat-text","sequence":0}\n\n' +
    'event: chunk\ndata: {"requestId":"req-text","sequence":1,"content":"好问题，这其实是业务建模里一个"}\n\n' +
    'event: chunk\ndata: {"requestId":"req-text","sequence":2,"content":"非常经典的决策点。"}\n\n' +
    'event: chunk\ndata: {"requestId":"req-text","sequence":3,"content":"先别急着问\\\"什么时候插入\\\""}\n\n' +
    'event: done\ndata: {"requestId":"req-text","sequence":4,"chatId":"chat-text","finishReason":"completed"}\n\n'
  )])

  const result = await consumeChatSseReader(reader)

  assert.equal(result.content, expected)
})

test('parser rejects malformed done token usage', () => {
  const invalidUsage = [
    { promptTokens: 10, completionTokens: 4 },
    { promptTokens: -1, completionTokens: 4, totalTokens: 3 },
    { promptTokens: '10', completionTokens: 4, totalTokens: 14 }
  ]

  for (const usage of invalidUsage) {
    assert.throws(
      () => createChatSseParser().push(
        `event: done\ndata: ${JSON.stringify({ chatId: 'chat-1', finishReason: 'completed', usage })}\n\n`
      ),
      ChatSseProtocolError
    )
  }
})

test('consumer reports reasoning independently from answer content', async () => {
  const reader = createReader([encode(
    'event: meta\ndata: {"protocolVersion":1,"requestId":"req-2","chatId":"chat-2","sequence":0}\n\n' +
    'event: reasoning\ndata: {"requestId":"req-2","sequence":1,"content":"分析"}\n\n' +
    'event: chunk\ndata: {"requestId":"req-2","sequence":2,"content":"回答"}\n\n' +
    'event: done\ndata: {"requestId":"req-2","sequence":3,"chatId":"chat-2","finishReason":"completed"}\n\n'
  )])
  const reasoningUpdates = []

  const result = await consumeChatSseReader(reader, {
    onReasoning: reasoning => reasoningUpdates.push(reasoning)
  })

  assert.deepEqual(reasoningUpdates, ['分析'])
  assert.equal(result.reasoning, '分析')
  assert.equal(result.content, '回答')
  assert.equal(result.requestId, 'req-2')
})

test('state exposes server error messages and rejects events after done', () => {
  const errorState = createChatSseState()
  assert.throws(
    () => applyChatSseEvent(errorState, {
      type: 'error',
      data: { code: 'CHAT_STREAM_ERROR', message: '服务暂不可用' }
    }),
    error => error instanceof ChatSseStreamError && error.message === '服务暂不可用' &&
      error.code === 'CHAT_STREAM_ERROR'
  )

  const completedState = createChatSseState()
  applyChatSseEvent(completedState, {
    type: 'done',
    data: { chatId: 'chat-3', finishReason: 'completed' }
  })
  assert.throws(
    () => applyChatSseEvent(completedState, { type: 'chunk', data: { content: 'late' } }),
    ChatSseProtocolError
  )
})

test('state rejects EOF without done', () => {
  const state = createChatSseState()
  applyChatSseEvent(state, { type: 'chunk', data: { content: 'partial' } })

  assert.throws(
    () => assertChatSseCompleted(state),
    error => error instanceof ChatSseProtocolError && /意外中断/.test(error.message)
  )
})

test('consumer decodes split Unicode bytes, reports complete content after chunks and requires done', async () => {
  const payload = encode(
    'event: chunk\ndata: {"content":"你"}\n\n' +
      'event: chunk\ndata: {"content":"好"}\n\n' +
      'event: done\ndata: {"chatId":"chat-4","finishReason":"completed"}\n\n'
  )
  const splitAt = payload.indexOf(0xe4) + 1
  const reader = createReader([payload.slice(0, splitAt), payload.slice(splitAt)])
  const updates = []

  const result = await consumeChatSseReader(reader, {
    onContent: content => updates.push(content)
  })

  assert.deepEqual(updates, ['你', '你好'])
  assert.deepEqual(result, {
    status: 'completed', content: '你好', reasoning: '', chatId: 'chat-4', requestId: null, usage: null
  })
})

test('consumer stops after the done batch without cancelling a normally completed reader', async () => {
  let reads = 0
  let cancels = 0
  const reader = {
    async read() {
      reads += 1
      if (reads > 1) throw new Error('read after done')
      return {
        done: false,
        value: encode('event: done\ndata: {"chatId":"chat-done","finishReason":"completed"}\n\n')
      }
    },
    async cancel() {
      cancels += 1
      throw new Error('already closing')
    }
  }

  const result = await consumeChatSseReader(reader)

  assert.deepEqual(result, {
    status: 'completed', content: '', reasoning: '', chatId: 'chat-done', requestId: null, usage: null
  })
  assert.equal(reads, 1)
  assert.equal(cancels, 0)
})

test('consumer still rejects an event after done in the same parsed batch', async () => {
  const reader = createReader([
    encode(
      'event: done\ndata: {"chatId":"chat-done","finishReason":"completed"}\n\n' +
        'event: chunk\ndata: {"content":"late"}\n\n'
    )
  ])

  await assert.rejects(consumeChatSseReader(reader), ChatSseProtocolError)
})

test('consumer cancels the reader and returns cancelled without requiring done', async () => {
  const reader = createReader([encode('event: chunk\ndata: {"content":"partial"}\n\n')])

  const result = await consumeChatSseReader(reader, { isCancelled: () => true })

  assert.deepEqual(result, { status: 'cancelled', cancelled: true, content: '', chatId: null })
  assert.equal(reader.cancelled, true)
})

test('consumer still returns cancelled when reader cancellation rejects', async () => {
  const reader = {
    async read() {
      throw new Error('read should not be called')
    },
    async cancel() {
      throw new Error('stream already closed')
    }
  }

  const result = await consumeChatSseReader(reader, { isCancelled: () => true })

  assert.deepEqual(result, { status: 'cancelled', cancelled: true, content: '', chatId: null })
})

test('consumer treats a read rejection as cancellation when cancellation happened during the read', async () => {
  let cancelled = false
  const reader = {
    async read() {
      cancelled = true
      throw new Error('aborted')
    },
    async cancel() {}
  }

  const result = await consumeChatSseReader(reader, { isCancelled: () => cancelled })

  assert.deepEqual(result, { status: 'cancelled', cancelled: true, content: '', chatId: null })
})

test('consumer rechecks cancellation when a pending read returns EOF', async () => {
  let cancelled = false
  let readerCancelled = false
  const reader = {
    async read() {
      cancelled = true
      return { done: true, value: undefined }
    },
    async cancel() {
      readerCancelled = true
    }
  }

  const result = await consumeChatSseReader(reader, { isCancelled: () => cancelled })

  assert.deepEqual(result, { status: 'cancelled', cancelled: true, content: '', chatId: null })
  assert.equal(readerCancelled, true)
})

test('consumer cancellation result exposes a cancelled boolean', async () => {
  const result = await consumeChatSseReader(createReader([]), { isCancelled: () => true })

  assert.equal(result.cancelled, true)
})

test('consumer safely cancels and preserves abnormal exit errors', async () => {
  const cases = [
    {
      frame: 'event: error\ndata: {"code":"CHAT_STREAM_ERROR","message":"failed"}\n\n',
      expected: ChatSseStreamError
    },
    {
      frame: 'event: chunk\ndata: nope\n\n',
      expected: ChatSseProtocolError
    },
    {
      frame: 'event: chunk\ndata: {"content":"hello"}\n\n',
      expected: new Error('render failed')
    }
  ]

  for (const testCase of cases) {
    let cancelCount = 0
    const reader = {
      async read() {
        return { done: false, value: encode(testCase.frame) }
      },
      async cancel() {
        cancelCount += 1
        throw new Error('cancel failed')
      }
    }
    const onContent = testCase.expected instanceof Error
      ? () => { throw testCase.expected }
      : undefined

    await assert.rejects(
      consumeChatSseReader(reader, { onContent }),
      error => testCase.expected instanceof Error
        ? error === testCase.expected
        : error instanceof testCase.expected
    )
    assert.equal(cancelCount, 1)
  }
})

test('consumer flushes EOF and rejects incomplete or unfinished streams', async () => {
  await assert.rejects(
    consumeChatSseReader(createReader([encode('event: chunk\ndata: {"content":"partial"}')]), {}),
    ChatSseProtocolError
  )
  await assert.rejects(
    consumeChatSseReader(createReader([
      encode('event: chunk\ndata: {"content":"partial"}\n\n')
    ]), {}),
    error => error instanceof ChatSseProtocolError && /意外中断/.test(error.message)
  )
})

test('content-type helper accepts event streams and rejects other successful response types', () => {
  assert.doesNotThrow(() => assertChatSseResponse({
    headers: new Headers({ 'Content-Type': 'text/event-stream; charset=utf-8' })
  }))
  assert.throws(
    () => assertChatSseResponse({
      headers: new Headers({ 'Content-Type': 'application/json' })
    }),
    ChatSseProtocolError
  )
})

test('content-type helper rejects media types that merely contain text/event-stream', () => {
  assert.throws(
    () => assertChatSseResponse({
      headers: new Headers({
        'Content-Type': ' application/x-text/event-stream-backup ; charset=utf-8 '
      })
    }),
    ChatSseProtocolError
  )
})
