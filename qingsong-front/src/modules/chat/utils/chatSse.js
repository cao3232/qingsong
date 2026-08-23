const KNOWN_EVENT_TYPES = new Set(['meta', 'reasoning', 'chunk', 'tool_call', 'tool_result', 'done', 'error'])

export class ChatSseProtocolError extends Error {
  constructor(message) {
    super(message)
    this.name = 'ChatSseProtocolError'
  }
}

export class ChatSseStreamError extends Error {
  constructor(message, code) {
    super(message)
    this.name = 'ChatSseStreamError'
    this.code = code
  }
}

const requireObject = (value, eventType) => {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new ChatSseProtocolError(`${eventType} 事件的 data 必须是 JSON 对象`)
  }
}

const requireString = (data, field, eventType, { allowEmpty = false } = {}) => {
  if (typeof data[field] !== 'string' || (!allowEmpty && !data[field].trim())) {
    throw new ChatSseProtocolError(`${eventType} 事件缺少有效的 ${field} 字段`)
  }
}

const requireSequence = (data, eventType) => {
  if (!Number.isInteger(data.sequence) || data.sequence < 0) {
    throw new ChatSseProtocolError(`${eventType} 事件包含无效的 sequence 字段`)
  }
}

const validateEventData = (eventType, data) => {
  requireObject(data, eventType)

  if (eventType === 'chunk') {
    requireString(data, 'content', eventType, { allowEmpty: true })
    if ('sequence' in data) requireSequence(data, eventType)
    if ('requestId' in data) requireString(data, 'requestId', eventType)
    return
  }

  if (eventType === 'reasoning') {
    requireString(data, 'content', eventType, { allowEmpty: true })
    if ('sequence' in data) requireSequence(data, eventType)
    if ('requestId' in data) requireString(data, 'requestId', eventType)
    return
  }

  if (eventType === 'meta') {
    if (data.protocolVersion !== 1) {
      throw new ChatSseProtocolError('meta 事件的 protocolVersion 必须为 1')
    }
    requireString(data, 'requestId', eventType)
    requireString(data, 'chatId', eventType)
    requireSequence(data, eventType)
    if (data.sequence !== 0) {
      throw new ChatSseProtocolError('meta 事件的 sequence 必须为 0')
    }
    return
  }

  if (eventType === 'tool_call' || eventType === 'tool_result') {
    requireString(data, 'toolCallId', eventType)
    requireString(data, 'name', eventType)
    requireString(data, 'status', eventType)
    if ('sequence' in data) requireSequence(data, eventType)
    if ('requestId' in data) requireString(data, 'requestId', eventType)
    return
  }

  if (eventType === 'done') {
    requireString(data, 'chatId', eventType)
    if ('sequence' in data) requireSequence(data, eventType)
    if ('requestId' in data) requireString(data, 'requestId', eventType)
    if (data.finishReason !== 'completed') {
      throw new ChatSseProtocolError('done 事件的 finishReason 必须为 completed')
    }
    if (data.usage != null) {
      requireObject(data.usage, `${eventType}.usage`)
      for (const field of ['promptTokens', 'completionTokens', 'totalTokens']) {
        if (!Number.isInteger(data.usage[field]) || data.usage[field] < 0) {
          throw new ChatSseProtocolError(`done 事件包含无效的 usage.${field}`)
        }
      }
    }
    if ('elapsedMs' in data && (!Number.isInteger(data.elapsedMs) || data.elapsedMs < 0)) {
      throw new ChatSseProtocolError('done 事件包含无效的 elapsedMs 字段')
    }
    if (data.tools != null && !Array.isArray(data.tools)) {
      throw new ChatSseProtocolError('done 事件的 tools 必须是数组')
    }
    return
  }

  requireString(data, 'code', eventType)
  requireString(data, 'message', eventType)
  if ('sequence' in data) requireSequence(data, eventType)
  if ('requestId' in data) requireString(data, 'requestId', eventType)
}

const parseEventBlock = block => {
  let eventType = 'message'
  const dataLines = []

  for (const line of block.split(/\r\n|\r|\n/)) {
    if (!line || line.startsWith(':')) continue

    const colonIndex = line.indexOf(':')
    const field = colonIndex === -1 ? line : line.slice(0, colonIndex)
    let value = colonIndex === -1 ? '' : line.slice(colonIndex + 1)
    if (value.startsWith(' ')) value = value.slice(1)

    if (field === 'event') eventType = value
    if (field === 'data') dataLines.push(value)
  }

  if (!KNOWN_EVENT_TYPES.has(eventType)) return null
  if (dataLines.length === 0) {
    throw new ChatSseProtocolError(`${eventType} 事件缺少 data 字段`)
  }

  let data
  try {
    data = JSON.parse(dataLines.join('\n'))
  } catch {
    throw new ChatSseProtocolError(`${eventType} 事件包含非法 JSON`)
  }

  validateEventData(eventType, data)
  return { type: eventType, data }
}

export const createChatSseParser = () => {
  let buffer = ''
  let pendingCarriageReturn = false

  const normalizeNewlines = (chunk, { finish = false } = {}) => {
    let input = `${pendingCarriageReturn ? '\r' : ''}${String(chunk || '')}`
    pendingCarriageReturn = false

    if (!finish && input.endsWith('\r')) {
      pendingCarriageReturn = true
      input = input.slice(0, -1)
    }

    return input.replace(/\r\n|\r/g, '\n')
  }

  const drainEvents = () => {
    const events = []
    let boundaryIndex = buffer.indexOf('\n\n')

    while (boundaryIndex !== -1) {
      const block = buffer.slice(0, boundaryIndex)
      buffer = buffer.slice(boundaryIndex + 2)
      const event = parseEventBlock(block)
      if (event) events.push(event)
      boundaryIndex = buffer.indexOf('\n\n')
    }

    return events
  }

  return {
    push(chunk) {
      buffer += normalizeNewlines(chunk)
      return drainEvents()
    },

    finish() {
      buffer += normalizeNewlines('', { finish: true })
      const events = drainEvents()
      const hasNonCommentFragment = buffer
        .split('\n')
        .some(line => line.trim() && !line.startsWith(':'))

      if (hasNonCommentFragment) {
        throw new ChatSseProtocolError('SSE 流结束时存在不完整的事件残片')
      }
      buffer = ''
      return events
    }
  }
}

export const createChatSseState = () => ({
  content: '',
  reasoning: '',
  completed: false,
  chatId: null,
  finishReason: null,
  requestId: null,
  usage: null,
  elapsedMs: null,
  tools: [],
  nextSequence: null
})

const validateEventSequence = (state, event) => {
  const { data } = event
  if (event.type !== 'meta' && state.requestId === null &&
      (Number.isInteger(data.sequence) || typeof data.requestId === 'string')) {
    throw new ChatSseProtocolError(`${event.type} 事件不能出现在 meta 之前`)
  }
  if (state.nextSequence !== null && !Number.isInteger(data.sequence)) {
    throw new ChatSseProtocolError(`${event.type} 事件缺少 sequence 字段`)
  }
  if (!Number.isInteger(data.sequence)) return state.nextSequence

  if (state.nextSequence === null) {
    return 1
  }

  if (data.sequence !== state.nextSequence) {
    throw new ChatSseProtocolError(`SSE sequence 不连续，期望 ${state.nextSequence}，实际 ${data.sequence}`)
  }
  return state.nextSequence + 1
}

const validateEventRequest = (state, event) => {
  const requestId = event.data.requestId
  if (state.requestId && !requestId) {
    throw new ChatSseProtocolError(`${event.type} 事件缺少 requestId 字段`)
  }
  if (requestId && state.requestId && requestId !== state.requestId) {
    throw new ChatSseProtocolError('SSE 事件的 requestId 与 meta 不一致')
  }
}

/** 按 toolCallId 合并工具事件（call 提供 args/status=running，result 回填状态与结果）。 */
const upsertTool = (state, data) => {
  let tool = state.tools.find(item => item.toolCallId === data.toolCallId)
  if (!tool) {
    tool = { toolCallId: data.toolCallId, name: data.name, status: data.status || 'running' }
    state.tools.push(tool)
  } else {
    tool.name = data.name
    tool.status = data.status || tool.status
  }
  if (data.args != null) tool.args = data.args
  if (data.result != null) tool.result = data.result
  if (data.error != null) tool.error = data.error
  if (data.durationMs != null) tool.durationMs = data.durationMs
}

export const applyChatSseEvent = (state, event) => {
  if (state.completed) {
    throw new ChatSseProtocolError('done 事件后不允许出现其他事件')
  }

  validateEventRequest(state, event)
  const nextSequence = validateEventSequence(state, event)

  if (event.type === 'meta') {
    if (state.requestId !== null) {
      throw new ChatSseProtocolError('SSE 流不允许重复 meta 事件')
    }
    state.requestId = event.data.requestId
    state.chatId = event.data.chatId
    state.nextSequence = nextSequence
    return state
  }

  if (event.type === 'reasoning') {
    state.nextSequence = nextSequence
    state.reasoning += event.data.content
    return state
  }

  if (event.type === 'chunk') {
    state.nextSequence = nextSequence
    state.content += event.data.content
    return state
  }

  if (event.type === 'tool_call' || event.type === 'tool_result') {
    state.nextSequence = nextSequence
    upsertTool(state, event.data)
    return state
  }

  if (event.type === 'done') {
    if (state.chatId && state.chatId !== event.data.chatId) {
      throw new ChatSseProtocolError('done 事件的 chatId 与 meta 不一致')
    }
    state.nextSequence = nextSequence
    state.completed = true
    state.chatId = event.data.chatId
    state.finishReason = event.data.finishReason
    state.usage = event.data.usage || null
    state.elapsedMs = event.data.elapsedMs ?? null
    // done.tools 是后端按 toolCallId 合并的完整汇总，优先采用
    if (Array.isArray(event.data.tools)) state.tools = event.data.tools
    return state
  }

  if (event.type === 'error') {
    throw new ChatSseStreamError(event.data.message, event.data.code)
  }

  return state
}

export const assertChatSseCompleted = state => {
  if (!state.completed) {
    throw new ChatSseProtocolError('聊天连接意外中断，未收到 done 事件')
  }
  return state
}

const cancelledResult = state => ({
  status: 'cancelled',
  cancelled: true,
  content: state.content,
  chatId: state.chatId
})

const cancelReader = async reader => {
  try {
    await reader.cancel()
  } catch {
    // The request may already have closed after AbortController.abort().
  }
}

const completedResult = state => ({
  status: 'completed',
  content: state.content,
  reasoning: state.reasoning,
  chatId: state.chatId,
  requestId: state.requestId,
  usage: state.usage,
  elapsedMs: state.elapsedMs,
  tools: state.tools
})

export const consumeChatSseReader = async (
  reader,
  { onContent = () => {}, onReasoning = () => {}, onToolCall = () => {}, onToolResult = () => {}, isCancelled = () => false } = {}
) => {
  const decoder = new TextDecoder('utf-8')
  const parser = createChatSseParser()
  const state = createChatSseState()

  const applyEvents = async events => {
    for (const event of events) {
      applyChatSseEvent(state, event)
      if (event.type === 'chunk') await onContent(state.content)
      if (event.type === 'reasoning') await onReasoning(state.reasoning)
      if (event.type === 'tool_call') await onToolCall(event.data)
      if (event.type === 'tool_result') await onToolResult(event.data)
    }
  }

  try {
    while (true) {
      if (isCancelled()) {
        await cancelReader(reader)
        return cancelledResult(state)
      }

      let readResult
      try {
        readResult = await reader.read()
      } catch (error) {
        if (!isCancelled()) throw error
        await cancelReader(reader)
        return cancelledResult(state)
      }

      const { value, done } = readResult
      if (done) {
        if (isCancelled()) {
          await cancelReader(reader)
          return cancelledResult(state)
        }
        break
      }

      await applyEvents(parser.push(decoder.decode(value, { stream: true })))

      if (state.completed) {
        return completedResult(state)
      }

      if (isCancelled()) {
        await cancelReader(reader)
        return cancelledResult(state)
      }
    }

    await applyEvents(parser.push(decoder.decode()))
    await applyEvents(parser.finish())
    assertChatSseCompleted(state)

    return completedResult(state)
  } catch (error) {
    await cancelReader(reader)
    throw error
  }
}

export const assertChatSseResponse = response => {
  const contentType = response.headers.get('content-type') || ''
  const mediaType = contentType.split(';', 1)[0].trim().toLowerCase()
  if (mediaType !== 'text/event-stream') {
    throw new ChatSseProtocolError(
      `聊天响应 Content-Type 非 text/event-stream: ${contentType || 'missing'}`
    )
  }
}
