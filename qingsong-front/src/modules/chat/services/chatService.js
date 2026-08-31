import { API_BASE_URL } from '@/config/env'
import http from '@/utils/http'
import { fetchAuth } from '@/utils/fetchAuth'
import { assertChatSseResponse } from '../utils/chatSse.js'

const normalizeId = (value) => String(value)
const encodePathParam = (value) => encodeURIComponent(normalizeId(value))
let runtimeMessageSequence = 0

const nextRuntimeSequence = () => {
  runtimeMessageSequence += 1
  return runtimeMessageSequence.toString(36)
}

// API_BASE_URL 为空(走同源相对路径)时，new URL 需要传入绝对 base，否则会抛 TypeError 导致请求根本发不出去
const resolveBaseUrl = () =>
  API_BASE_URL && API_BASE_URL.trim()
    ? API_BASE_URL
    : (typeof window !== 'undefined' ? window.location.origin : '')

const normalizeTimestamp = (value) => {
  if (!value) {
    return null
  }

  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

const createHistoryMessageId = (chatId, index, role = 'unknown') =>
  `history-${normalizeId(chatId)}-${role}-${index}`

export const createClientMessageId = (prefix = 'msg') =>
  `${prefix}-${Date.now().toString(36)}-${nextRuntimeSequence()}`

// 网络/网关错误判定与统一文案已上提到 @/services/networkError（单一真源），
// 此处仅作兼容 re-export，保持本模块导出接口不变，调用方 import 路径无需改动
export { isConnectionError, getConnectionErrorMessage } from '@/services/networkError'

export const decodeStreamChunk = (decoder, value) =>
  decoder.decode(value, { stream: true })

export const flushStreamDecoder = (decoder) => decoder.decode()

const extractErrorMessage = async (response) => {
  const contentType = response.headers.get('content-type') || ''

  if (contentType.includes('application/json')) {
    try {
      const payload = await response.json()
      if (payload?.msg) {
        return payload.msg
      }
    } catch {
      // ignore json parse errors and fallback to plain text/status
    }
  }

  try {
    const text = await response.text()
    if (text) {
      return text
    }
  } catch {
    // ignore body read errors and fallback to status
  }

  return `HTTP error! status: ${response.status}`
}

const ensureOk = async (response) => {
  if (!response.ok) {
    const error = new Error(await extractErrorMessage(response))
    error.status = response.status
    throw error
  }
}

const getStreamReader = async (response) => {
  await ensureOk(response)
  return response.body.getReader()
}

const mapRoleList = (roles = []) => {
  const roleList = roles.map((role) => ({
    name: role.name,
    icon: 'user',
    value: {
      ...role,
      id: normalizeId(role.id),
      desc: role.description
    }
  }))

  const favoriteRoles = []
  const regularRoles = []

  roleList.forEach((role) => {
    role.value.favor = role.value.favor ?? '0'
    role.value.sort = role.value.sort ?? '1000000'

    if (role.value.favor === '1') {
      favoriteRoles.push(role)
      return
    }

    regularRoles.push(role)
  })

  const sortByRank = (left, right) => (left.value.sort > right.value.sort ? -1 : 1)

  favoriteRoles.sort(sortByRank)
  regularRoles.sort(sortByRank)

  return [...favoriteRoles, ...regularRoles]
}

const mapChatHistory = (chatList = [], type = 'chat') =>
  chatList.map((chat) => ({
    id: normalizeId(chat.id),
    // 游标分页第二排序键（ai_chat_session 主键）
    sessionDbId: chat.sessionDbId ?? null,
    // 标识服务端来源：loadChatHistory 用 source !== 'server' 过滤出本地临时会话
    source: 'server',
    role: chat.role || chat.roleCode || chat.name,
    title:
      chat.title ||
      chat.name ||
      (type === 'pdf'
        ? `PDF对话 ${normalizeId(chat.id).slice(-6)}`
        : type === 'service'
          ? `咨询 ${normalizeId(chat.id).slice(-6)}`
          : '新对话'),
    name: chat.name || chat.title || '',
    createdAt: normalizeTimestamp(chat.createdAt),
    lastMessageAt: normalizeTimestamp(chat.lastMessageAt),
    messageCount: chat.messageCount ?? 0
  }))

const resolveHistoryRole = (type = 'chat', role) => {
  if (role !== null && role !== undefined && String(role).trim()) {
    return String(role)
  }

  if (type === 'service' || type === 'pdf') {
    return type
  }

  return 'default'
}

const mapChatMessages = (messages = [], role, chatId) =>
  messages.map((message, index) => {
    const accurateTimestamp = normalizeTimestamp(message.timestamp ?? message.createDate ?? message.createdAt)
    const stableMessageId = message.id
      ? normalizeId(message.id)
      : createHistoryMessageId(chatId, index, message.role)

    return {
      ...message,
      id: stableMessageId,
      messageNo: stableMessageId,
      chatId: normalizeId(chatId),
      chatRoleId: role,
      timestamp: accurateTimestamp,
      hasAccurateTimestamp: Boolean(accurateTimestamp)
    }
  })

const TOOL_LABELS = {
  selectPrompt: '选择提示词',
  savePrompt: '保存提示词',
  getRoleHistory: '获取角色历史',
  saveRoleHistory: '保存角色历史'
}

const mapToolDefinitions = (toolMap = {}) =>
  Object.entries(toolMap).map(([key, description]) => ({
    key,
    name: key,
    label: TOOL_LABELS[key] || key,
    description: typeof description === 'string' ? description.trim() : ''
  }))

const mapToolGroups = (toolGroups = {}) =>
  Object.entries(toolGroups)
    .filter(([, value]) => value && typeof value === 'object' && !Array.isArray(value))
    .map(([groupKey, tools]) => ({
      groupKey,
      tools: mapToolDefinitions(tools)
    }))

const createSceneStreamSender =
  (pathBuilder) => async (prompt, chatId) => {
    const response = await fetchAuth(pathBuilder(prompt, normalizeId(chatId)), {
      method: 'GET'
    })

    return getStreamReader(response)
  }

export const chatAPI = {
  async getSourceOptions() {
    try {
      const result = await http.get(`${API_BASE_URL}/api/model-sources/info`)

      if (!result?.ok || !Array.isArray(result.data)) {
        return []
      }

      return result.data.map((source) => ({
        ...source,
        id: normalizeId(source.id)
      }))
    } catch (error) {
      console.error('Chat API Error:', error)
      return []
    }
  },

  async toggleSourceActive(sourceId) {
    return http.post(
      `${API_BASE_URL}/api/model-sources/${normalizeId(sourceId)}/toggle-active`
    )
  },

  async getActiveModels(sourceId) {
    try {
      const url = sourceId
        ? `${API_BASE_URL}/api/model-configs/source/${normalizeId(sourceId)}`
        : `${API_BASE_URL}/api/model-configs/active`
      const result = await http.get(url)

      if (!result?.ok || !Array.isArray(result.data)) {
        return []
      }

      return result.data.map((model) => ({
        ...model,
        id: normalizeId(model.id)
      }))
    } catch (error) {
      console.error('Chat API Error:', error)
      return []
    }
  },

  async switchTopModel(modelId) {
    return http.post(
      `${API_BASE_URL}/api/model-configs/${normalizeId(modelId)}/top`,
      null,
      {
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
      }
    )
  },

  async refreshRoles() {
    try {
      const result = await http.get(`${API_BASE_URL}/ai/refresh/roles`)

      if (result && result.data) {
        result.data = result.data.map((role) => ({
          ...role,
          id: normalizeId(role.id)
        }))
      }

      return result
    } catch (error) {
      console.error('Chat API Error:', error)
      return []
    }
  },

  // 预分配会话身份与用户消息号：
  // - 不带 sessionNo（新会话）：后端预建会话行，返回真实 sessionNo + 首个 messageNo；
  // - 带 sessionNo（既有会话）：原样回传 sessionNo，仅签发新的 messageNo。
  // 前端在发送/重试前调用，拿到后随主聊天请求带回，保证会话身份稳定可比对。
  async preChat({ role, sessionNo, bizType = 'chat' } = {}) {
    try {
      const params = new URLSearchParams()
      if (bizType) params.append('bizType', bizType)
      if (role) params.append('role', role)
      if (sessionNo) params.append('sessionNo', String(sessionNo))
      const query = params.toString()
      const url = query
        ? `${API_BASE_URL}/ai/chat/pre?${query}`
        : `${API_BASE_URL}/ai/chat/pre`
      const result = await http.post(url)
      if (result?.ok && result.data?.messageNo) {
        return {
          sessionNo: result.data.sessionNo || null,
          messageNo: String(result.data.messageNo)
        }
      }
      return null
    } catch (error) {
      console.error('preChat Error:', error)
      throw error
    }
  },

  async sendMessage(data, chatId, { signal } = {}) {
    try {
      const url = new URL(`/ai/chat`, resolveBaseUrl())
      if (chatId) {
        url.searchParams.append('chatId', normalizeId(chatId))
      }
      const response = await fetchAuth(url, {
        method: 'POST',
        body:
          data instanceof FormData
            ? data
            : new URLSearchParams({
                prompt: data
              }),
        signal
      })

      await ensureOk(response)
      assertChatSseResponse(response)
      const reader = response.body.getReader()
      return {
        reader,
        sessionId: response.headers.get('X-Session-Id')
      }
    } catch (error) {
      console.error('Chat API Error:', error)
      throw error
    }
  },

  async getChatHistory(type = 'chat', role) {
    try {
      const effectiveRole = resolveHistoryRole(type, role)
      const chatList = await http.get(
        `${API_BASE_URL}/ai/history/${type}/${effectiveRole}`
      )
      return mapChatHistory(chatList, type)
    } catch (error) {
      console.error('Chat API Error:', error)
      return []
    }
  },

  async getChatHistoryInfo(type = 'chat', role) {
    try {
      const effectiveRole = resolveHistoryRole(type, role)
      // 走同源相对路径，经 vite 代理到后端，避免直连 API_BASE_URL(8088) 触发跨域
      const url = `/ai/history/${encodeURIComponent(type)}/${encodeURIComponent(effectiveRole)}/info`
      const data = await http.get(url)
      return data || {}
    } catch (error) {
      console.error('Chat API Error:', error)
      return {}
    }
  },

  async getChatMessages(chatId, type = 'chat', role) {
    try {
      const effectiveRole = resolveHistoryRole(type, role)
      const messages = await http.get(
        `${API_BASE_URL}/ai/history/${type}/${effectiveRole}/${normalizeId(chatId)}`
      )
      return mapChatMessages(messages, effectiveRole, chatId)
    } catch (error) {
      // 404：会话不存在或已删除（区别于"存在但无消息"的 []），由调用方决定回退策略
      if (error?.status === 404) {
        return null
      }
      console.error('Chat API Error:', error)
      return []
    }
  },

  // 会话列表游标分页：keyword 匹配标题；start/end 为时间范围（左闭右开）；before/beforeId 为上一页末条游标
  async getChatHistoryPage({ type = 'chat', role, keyword, start, end, before, beforeId, limit = 15 } = {}) {
    try {
      const effectiveRole = resolveHistoryRole(type, role)
      const params = new URLSearchParams()
      if (keyword) params.append('keyword', keyword)
      if (start) params.append('start', start)
      if (end) params.append('end', end)
      if (before) params.append('before', before)
      if (beforeId != null) params.append('beforeId', String(beforeId))
      params.append('limit', String(limit))
      // 走同源相对路径，经 vite 代理到后端，避免直连 API_BASE_URL(8088) 触发跨域
      const result = await http.get(
        `/ai/history/${encodeURIComponent(type)}/${encodeURIComponent(effectiveRole)}/page?${params.toString()}`
      )
      if (!result?.ok) {
        return { list: [], hasMore: false }
      }
      return {
        list: mapChatHistory(Array.isArray(result.data?.list) ? result.data.list : [], type),
        hasMore: Boolean(result.data?.hasMore)
      }
    } catch (error) {
      console.error('Chat API Error:', error)
      return { list: [], hasMore: false }
    }
  },

  // 有会话记录的日期集合（yyyy-MM-dd 降序），供日历高亮有记录日期
  async getChatHistoryDates(type = 'chat', role) {
    try {
      const effectiveRole = resolveHistoryRole(type, role)
      const result = await http.get(
        `/ai/history/${encodeURIComponent(type)}/${encodeURIComponent(effectiveRole)}/dates`
      )
      return result?.ok && Array.isArray(result.data) ? result.data : []
    } catch (error) {
      console.error('Chat API Error:', error)
      return []
    }
  },

  // 消息内容搜索：返回消息粒度命中项（sessionNo/sessionTitle/messageNo/messageType/snippet/createdAt）
  async searchChatMessages(keyword, { type = 'chat', role, start, end, limit = 100 } = {}) {
    try {
      const effectiveRole = resolveHistoryRole(type, role)
      const params = new URLSearchParams()
      params.append('keyword', keyword)
      if (start) params.append('start', start)
      if (end) params.append('end', end)
      params.append('limit', String(limit))
      const result = await http.get(
        `/ai/history/${encodeURIComponent(type)}/${encodeURIComponent(effectiveRole)}/search?${params.toString()}`
      )
      return result?.ok && Array.isArray(result.data) ? result.data : []
    } catch (error) {
      console.error('Chat API Error:', error)
      return []
    }
  },

  async deleteChat(chatId, type = 'chat', role) {
    try {
      const effectiveRole = resolveHistoryRole(type, role)
      return await http.delete(
        `${API_BASE_URL}/ai/history/${type}/${effectiveRole}/${normalizeId(chatId)}`
      )
    } catch (error) {
      console.error('Chat API Error:', error)
      return false
    }
  },

  async updateChatName(chatId, name, type = 'chat', role) {
    try {
      const encodedName = encodeURIComponent(name)
      const effectiveRole = resolveHistoryRole(type, role)
      const result = await http.put(
        `${API_BASE_URL}/ai/history/${type}/${effectiveRole}/${normalizeId(chatId)}/${encodedName}`
      )

      if (result.ok === 1) {
        return { success: true }
      }

      return { success: false, msg: result.msg || '更新失败' }
    } catch (error) {
      console.error('Chat API Error:', error)
      return { success: false, msg: '网络错误，请稍后重试' }
    }
  },

  sendGameMessage: createSceneStreamSender(
    (prompt, chatId) =>
      `${API_BASE_URL}/ai/game?prompt=${encodeURIComponent(prompt)}&chatId=${chatId}`
  ),

  async sendPdfMessage(prompt, chatId) {
    const response = await fetchAuth(
      `${API_BASE_URL}/ai/pdf/chat?prompt=${encodeURIComponent(prompt)}&chatId=${normalizeId(chatId)}`,
      {
        method: 'GET',
        signal: AbortSignal.timeout(30000)
      }
    )

    return getStreamReader(response)
  },

  async getAvailableTools() {
    const data = await http.get(`${API_BASE_URL}/tools/name`)
    return mapToolGroups(data)
  },

  async getRoles() {
    try {
      const data = await http.get(`${API_BASE_URL}/roles`)

      if (!data.ok) {
        throw new Error('获取角色失败')
      }

      return mapRoleList(data.data)
    } catch (error) {
      console.error('Chat API Error:', error)
      return []
    }
  },

  // 对话复盘（日报）：角色/会话/消息聚合 + 活跃时段 + 角色使用榜单（date 必传，yyyy-MM-dd）
  async getReview(date) {
    try {
      if (!date) throw new Error('复盘日期不能为空')
      const data = await http.get(`${API_BASE_URL}/ai/stats/review?date=${encodeURIComponent(date)}`, {
        timeout: 60000
      })

      if (!data.ok) {
        throw new Error(data.msg || '获取对话复盘失败')
      }

      return data.data
    } catch (error) {
      console.error('Chat API Error (review):', error)
      throw error
    }
  },

  // 已持久化的 AI 解读记录（date 必传，yyyy-MM-dd；无则返回 null）
  async getInsight(date) {
    try {
      if (!date) throw new Error('复盘日期不能为空')
      const data = await http.get(`${API_BASE_URL}/ai/stats/review/insight?date=${encodeURIComponent(date)}`, {
        timeout: 60000
      })

      if (!data.ok) {
        throw new Error(data.msg || '获取对话解读失败')
      }

      return data.data
    } catch (error) {
      console.error('Chat API Error (insight):', error)
      throw error
    }
  },

  async exportMessage(roleId, messageNo) {
    const url = new URL(
      `/ai/export/message/${encodePathParam(roleId)}/${encodePathParam(messageNo)}`,
      resolveBaseUrl()
    )
    url.searchParams.set('_ts', Date.now().toString())

    const response = await fetchAuth(url, {
      cache: 'no-store'
    })

    await ensureOk(response)
    return response.blob()
  },

  async sendEmail(roleId, messageNo) {
    return http.get(
      `${API_BASE_URL}/message/send-email-html/${encodePathParam(roleId)}/${encodePathParam(messageNo)}`,
      { timeout: 60000 }
    )
  },

  // 获取当前用户的聊天上下文窗口大小（0 表示无上下文）
  async getContextSize() {
    try {
      const data = await http.get(`${API_BASE_URL}/user-config/me/context-size`)
      if (!data.ok) {
        throw new Error(data.msg || '获取上下文窗口大小失败')
      }
      return data.data?.size ?? 0
    } catch (error) {
      console.error('Chat API Error (contextSize):', error)
      return 0
    }
  },

  // 更新当前用户的聊天上下文窗口大小（0-100，0 表示使用默认）
  async updateContextSize(size) {
    const data = await http.put(`${API_BASE_URL}/user-config/me/context-size`, size)
    if (!data.ok) {
      throw new Error(data.msg || '更新上下文窗口大小失败')
    }
    return data
  }
}
