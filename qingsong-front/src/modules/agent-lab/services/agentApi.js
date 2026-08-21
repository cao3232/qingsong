import http from '@/utils/http'
import { API_BASE_URL } from '@/utils/http'
import { fetchAuth } from '@/utils/fetchAuth'
import { drainSseBuffer } from '../utils/sseBuffer.js'

/**
 * Spring AI Alibaba 能力接口封装。
 *
 * 普通请求走 @/utils/http（已配置 baseURL、satoken header、统一解包）；
 * SSE 流式请求用原生 fetch + ReadableStream 逐事件解析（axios 不便消费 SSE）。
 */
export const agentApi = {
  /** 获取能力清单（GET /api/agent/capabilities） */
  getCapabilities() {
    return http.get('/api/agent/capabilities', { timeout: 15000 })
  },

  /** 获取所有可用工作流（GET /api/agent/graph/list） */
  getGraphList() {
    return http.get('/api/agent/graph/list', { timeout: 15000 })
  },

  /** 同步执行指定工作流（POST /api/agent/graph/{key}/run/sync） */
  runGraphSync(key, input, maxRetries = 3) {
    return http.post(`/api/agent/graph/${key}/run/sync`, { input, maxRetries }, { timeout: 180000 })
  },

  /**
   * 流式执行指定工作流（SSE）。
   *
   * 底层用 fetch 消费 text/event-stream，逐事件回调：
   * - onEvent(event)：每个节点/增量事件（GraphStreamEvent）
   * - onError(err)
   * - onDone()
   */
  runGraphStream(key, input, { threadId, maxRetries = 3, date, onEvent, onError, onDone } = {}) {
    const url = `${API_BASE_URL}/api/agent/graph/${key}/run`
    const controller = new AbortController()

    const abort = () => controller.abort()

    const run = async () => {
      try {
        const res = await fetchAuth(url, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ input, maxRetries, threadId, date }),
          signal: controller.signal
        })
        if (!res.ok || !res.body) {
          throw new Error(`请求失败：HTTP ${res.status}`)
        }

        const reader = res.body.getReader()
        const decoder = new TextDecoder('utf-8')
        let buffer = ''

        // 循环读取流，按空行分隔 SSE 事件
        for (;;) {
          const { done, value } = await reader.read()
          if (done) break
          buffer += decoder.decode(value, { stream: true })
          const drained = drainSseBuffer(buffer)
          buffer = drained.rest
          drained.events.forEach((event) => onEvent && onEvent(event))
        }
        buffer += decoder.decode()
        const drained = drainSseBuffer(buffer, true)
        drained.events.forEach((event) => onEvent && onEvent(event))
        onDone && onDone()
      } catch (err) {
        if (err && err.name === 'AbortError') return
        onError && onError(err)
      }
    }

    run()
    return { abort }
  },

  /** ReactAgent 同步对话（POST /api/agent/react/chat，返回 content + trace） */
  reactChat(message) {
    return http.post('/api/agent/react/chat', { message }, { timeout: 120000 })
  },

  /** 列出全部对话 Agent（GET /api/agent/chat/list） */
  chatAgentList() {
    return http.get('/api/agent/chat/list', { timeout: 15000 })
  },

  /** 同步对话（POST /api/agent/chat/{key}，返回 content + trace） */
  chatAgent(key, message, threadId) {
    return http.post(`/api/agent/chat/${key}`, { message, threadId }, { timeout: 120000 })
  },

  /** 流式对话（POST /api/agent/chat/{key}/stream，SSE ReactStreamEvent） */
  chatAgentStream(key, message, threadId, { onEvent, onError, onDone } = {}) {
    const url = `${API_BASE_URL}/api/agent/chat/${key}/stream`
    const controller = new AbortController()
    const abort = () => controller.abort()
    const run = async () => {
      try {
        const res = await fetchAuth(url, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ message, threadId }),
          signal: controller.signal
        })
        if (!res.ok || !res.body) throw new Error(`请求失败：HTTP ${res.status}`)
        const reader = res.body.getReader()
        const decoder = new TextDecoder('utf-8')
        let buffer = ''
        for (;;) {
          const { done, value } = await reader.read()
          if (done) break
          buffer += decoder.decode(value, { stream: true })
          let sep
          while ((sep = buffer.indexOf('\n\n')) !== -1) {
            const rawEvent = buffer.slice(0, sep)
            buffer = buffer.slice(sep + 2)
            const event = parseSseEvent(rawEvent)
            if (event && onEvent) onEvent(event)
          }
        }
        onDone && onDone()
      } catch (err) {
        if (err && err.name === 'AbortError') return
        onError && onError(err)
      }
    }
    run()
    return { abort }
  },

  /** 安全确认助手：发起对话（可能触发人工确认中断，返回 needs-confirmation） */
  confirmChat(message, threadId) {
    return http.post('/api/agent/confirm/chat', { message, threadId }, { timeout: 120000 })
  },

  /** 安全确认助手：提交 批准/拒绝/编辑 结果并恢复执行 */
  confirmResume(threadId, toolFeedbacks) {
    return http.post('/api/agent/confirm/resume', { threadId, toolFeedbacks }, { timeout: 120000 })
  },

  /**
   * 流式对话（SSE）。
   *
   * 与工作流 SSE 一致的事件结构（GraphStreamEvent）：node/agent/message/chunk/reasoningContent。
   * 回调：onEvent(事件) / onError(err) / onDone()。返回 { abort } 用于停止。
   */
  reactChatStream(message, { onEvent, onError, onDone } = {}) {
    const url = `${API_BASE_URL}/api/agent/react/chat/stream`
    const controller = new AbortController()
    const abort = () => controller.abort()

    const run = async () => {
      try {
        const res = await fetchAuth(url, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ message }),
          signal: controller.signal
        })
        if (!res.ok || !res.body) {
          throw new Error(`请求失败：HTTP ${res.status}`)
        }

        const reader = res.body.getReader()
        const decoder = new TextDecoder('utf-8')
        let buffer = ''

        for (;;) {
          const { done, value } = await reader.read()
          if (done) break
          buffer += decoder.decode(value, { stream: true })
          let sepIndex
          while ((sepIndex = buffer.indexOf('\n\n')) !== -1) {
            const rawEvent = buffer.slice(0, sepIndex)
            buffer = buffer.slice(sepIndex + 2)
            const event = parseSseEvent(rawEvent)
            if (event && onEvent) onEvent(event)
          }
        }
        onDone && onDone()
      } catch (err) {
        if (err && err.name === 'AbortError') return
        onError && onError(err)
      }
    }

    run()
    return { abort }
  }
}

/**
 * 解析一条 SSE 原始事件（可能含 data:/id:/event: 行）。
 * 取所有 data: 行合并后 JSON.parse。
 */
function parseSseEvent(raw) {
  if (!raw) return null
  const dataLines = raw
    .split('\n')
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trim())
  if (!dataLines.length) return null
  try {
    return JSON.parse(dataLines.join('\n'))
  } catch (e) {
    return null
  }
}
