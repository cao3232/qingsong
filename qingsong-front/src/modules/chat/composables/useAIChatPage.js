import { computed, nextTick, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useMessage } from 'naive-ui'
import {
  chatAPI,
  createClientMessageId,
  isConnectionError,
  roleAPI
} from '../services/index.js'
import {
  ChatSseStreamError,
  consumeChatSseReader
} from '../utils/chatSse.js'
import { getVirtualListController } from './virtualListController.js'
import { useFavoriteMessages } from './useFavoriteMessages.js'
import { formatLocalDateTime, historyCursorOf, mergeHistoryUnique } from '../utils/chatHistoryPager.js'
import { createMessageCache } from '../utils/messageCache.js'
import { clearKeywordHighlight, waitForMessageAndHighlight } from '../utils/textHighlight.js'

const ROLE_PANEL_LAYOUT_VERSION = '2'
const LOCAL_CHAT_ID_PREFIX = 'temp-'

const createLocalChatId = () =>
  `${LOCAL_CHAT_ID_PREFIX}${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`

export const isLocalOnlyChatId = chatId => String(chatId || '').startsWith(LOCAL_CHAT_ID_PREFIX)

const DEFAULT_CONVERSATION_TITLES = new Set(['', '新会话', '新对话'])

const normalizePreviewText = value => String(value || '').replace(/\s+/g, ' ').trim()

const buildConversationPreviewTitle = value => {
  const normalizedText = normalizePreviewText(value)

  if (!normalizedText) {
    return '新会话'
  }

  return normalizedText.length > 24 ? `${normalizedText.slice(0, 24)}...` : normalizedText
}

const shouldReplaceConversationTitle = chat =>
  !chat || DEFAULT_CONVERSATION_TITLES.has(normalizePreviewText(chat.title || chat.name))

const normalizeRoles = (roleList = []) =>
  roleList.map(role => {
    if (role.value && role.value.id) {
      return {
        ...role,
        value: {
          ...role.value,
          id: String(role.value.id)
        }
      }
    }

    return role
  })

const createHistoryItem = (chatId, roleName, source = 'local') => {
  const now = new Date()

  return {
    id: String(chatId),
    title: '新会话',
    name: '',
    timestamp: now,
    createdAt: now,
    lastMessageAt: now,
    role: roleName,
    messageCount: 0,
    source
  }
}

export const useAIChatPage = () => {
  const currentChatId = ref(null)
  const currentMessages = ref([])
  const chatHistory = ref([])
  const selectedRole = ref(null)
  const selectedRoleName = ref('')
  const roles = ref([])
  const roleStats = ref({ total: [], today: [] })
  const isStreaming = ref(false)
  const ragEnabled = ref(false)
  const selectedKnowledgeBase = ref(null)
  const rolePanelCollapsed = ref(false)
  const sidebarCollapsed = ref(false)
  const message = useMessage()
  // 会话列表分页状态：historyFilter 由侧边栏筛选（关键词/日期范围）驱动
  const historyHasMore = ref(false)
  const historyLoadingMore = ref(false)
  const historyFilter = ref({ keyword: '', start: null, end: null })
  const historyDates = ref([])
  // 会话消息 LRU 缓存：切会话不重拉；发送/删除消息后 invalidate（流式消息与回读形状不同，不 append）
  const messageCache = createMessageCache(8)
  // 消息收藏星标回显：消息接口已合并 favorited 字段，加载消息后直接以消息为准对齐单例，
  // 不再单独请求 favorite/status（useFavoriteMessages 单例）
  const { syncFromMessages } = useFavoriteMessages()
  // 必须在 setup 同步阶段（await 之前）获取路由，避免在 async onMounted 延续里
  // currentInstance 为空导致 useRoute() 返回 undefined
  const route = useRoute()

  let isStartingNewChat = false
  let activeRequestToken = null
  let abortController = null
  let rolesRequestPromise = null

  const currentChatName = computed(() => {
    if (!currentChatId.value) return ''

    const currentChat = chatHistory.value.find(
      chat => String(chat.id) === String(currentChatId.value)
    )

    return currentChat?.name || currentChat?.title || ''
  })

  const toggleSidebar = () => {
    sidebarCollapsed.value = !sidebarCollapsed.value
    localStorage.setItem('sidebarCollapsed', sidebarCollapsed.value.toString())
  }

  const scrollToLastMessage = () => {
    nextTick(() => {
      setTimeout(() => {
        // 消息列表已虚拟滚动：不可见消息不在 DOM 中，统一走控制器滚动到底
        getVirtualListController()?.scrollToBottom()
      }, 200)
    })
  }

  const loadRoles = async () => {
    // 并发去重：深链进入时 useAIChatPage 与 useChatRouteSync 可能同时触发角色加载，只发一次请求
    if (rolesRequestPromise) {
      return rolesRequestPromise
    }

    rolesRequestPromise = (async () => {
      const loadingMsg = message.loading('正在加载角色列表...', { duration: 0 })

      try {
        const rolesList = await chatAPI.getRoles()
        roles.value = normalizeRoles(rolesList)
      } catch (error) {
        console.error('加载角色列表失败:', error)
        message.error('加载角色列表失败，请稍后重试')
      } finally {
        loadingMsg.destroy()
      }
    })()

    try {
      return await rolesRequestPromise
    } finally {
      rolesRequestPromise = null
    }
  }

  let roleStatsTimer = null

  // 加载角色使用统计（总榜 + 今日榜 + 最近对话角色），失败时静默保留上一次数据
  const loadRoleStats = async () => {
    try {
      const data = await roleAPI.getStats()
      roleStats.value = {
        total: Array.isArray(data?.total) ? data.total : [],
        today: Array.isArray(data?.today) ? data.today : [],
        lastRole: data?.lastRole || null
      }
    } catch (error) {
      console.warn('刷新角色榜单失败，沿用上次数据:', error)
    }
  }

  // 对话后防抖刷新榜单，避免连发消息打爆接口
  const refreshRoleStatsDebounced = () => {
    if (roleStatsTimer) clearTimeout(roleStatsTimer)
    roleStatsTimer = setTimeout(() => {
      loadRoleStats()
    }, 300)
  }

  const handleRolesUpdated = updatedRoles => {
    roles.value = normalizeRoles(updatedRoles)
  }

  // 输入框内 Alt+↑/↓ 切换会话：在当前角色的会话历史中循环上一条/下一条
  const switchConversation = direction => {
    if (chatHistory.value.length === 0) {
      return
    }

    const currentIndex = chatHistory.value.findIndex(
      chat => String(chat.id) === String(currentChatId.value)
    )

    let nextIndex
    if (currentIndex === -1) {
      nextIndex = 0
    } else {
      nextIndex =
        (currentIndex + direction + chatHistory.value.length) % chatHistory.value.length
    }

    const target = chatHistory.value[nextIndex]
    if (!target) {
      return
    }

    loadChat(String(target.id))
  }

  // 返回值约定：true 成功（含空会话）；null 会话不存在/已删除（404，不 toast，交调用方回退）；false 其它失败（已 toast）
  const loadChat = async (chatId, { skipScroll = false, force = false } = {}) => {
    if (isStreaming.value) {
      isStreaming.value = false
    }

    const cacheKey = String(chatId)
    if (!force) {
      const cached = messageCache.get(cacheKey)
      if (cached) {
        currentChatId.value = cacheKey
        // 拷贝数组，避免流式期间对 currentMessages 的 mutation 污染缓存快照
        currentMessages.value = [...cached]
        syncFromMessages(currentMessages.value)
        if (!skipScroll) {
          setTimeout(() => {
            scrollToLastMessage()
          }, 100)
        }
        return true
      }
    }

    const loadingMsg = message.loading('正在加载对话消息...', { duration: 0 })

    try {
      currentChatId.value = cacheKey

      const roleName = selectedRoleName.value || 'user'

      const messages = await chatAPI.getChatMessages(cacheKey, 'chat', roleName)
      if (messages === null) {
        currentMessages.value = []
        return null
      }
      currentMessages.value = messages
      messageCache.set(cacheKey, [...messages])
      syncFromMessages(messages)

      await nextTick()
      // 深链 ?msg= 跳转时跳过自动滚底，避免覆盖消息定位高亮
      if (!skipScroll) {
        setTimeout(() => {
          scrollToLastMessage()
        }, 100)
      }
      return true
    } catch (error) {
      console.error('加载对话消息失败:', error)
      currentMessages.value = []
      message.error('加载对话消息失败，请稍后重试')
      return false
    } finally {
      loadingMsg.destroy()
    }
  }

  const loadChatHistory = async (loadLastChat = true) => {
    const loadingMsg = message.loading('正在加载聊天历史...', { duration: 0 })

    try {
      const { list, hasMore } = await chatAPI.getChatHistoryPage({
        type: 'chat',
        role: selectedRoleName.value,
        ...historyFilter.value
      })
      historyHasMore.value = hasMore

      // 本地未持久化的临时会话保持在列表头部，与服务端首页合并去重（Set，O(n)）
      const localHistory = chatHistory.value.filter(
        chat => chat.role === selectedRoleName.value && chat.source !== 'server'
      )

      chatHistory.value = mergeHistoryUnique(localHistory, list)

      if (loadLastChat && chatHistory.value.length > 0) {
        await loadChat(String(chatHistory.value[0].id))
      }
    } catch (error) {
      console.error('加载聊天历史失败:', error)
      chatHistory.value = chatHistory.value.filter(
        chat => chat.role === selectedRoleName.value && chat.source !== 'server'
      )
      message.error('加载聊天历史失败，请稍后重试')
    } finally {
      loadingMsg.destroy()
    }
  }

  // 滚动到底加载下一页。游标取当前列表末条（本地临时会话恒在头部，末条必为服务端数据）；
  // 翻页中若有会话更新 lastMessageAt 导致排序漂移，mergeHistoryUnique 去重兜底
  const loadMoreHistory = async () => {
    if (historyLoadingMore.value || !historyHasMore.value) {
      return
    }

    const cursor = historyCursorOf(chatHistory.value[chatHistory.value.length - 1])
    if (!cursor) {
      historyHasMore.value = false
      return
    }

    historyLoadingMore.value = true

    try {
      const { list, hasMore } = await chatAPI.getChatHistoryPage({
        type: 'chat',
        role: selectedRoleName.value,
        ...historyFilter.value,
        ...cursor
      })
      historyHasMore.value = hasMore
      chatHistory.value = mergeHistoryUnique(chatHistory.value, list)
    } catch (error) {
      console.error('加载更多会话失败:', error)
      message.error('加载更多会话失败，请稍后重试')
    } finally {
      historyLoadingMore.value = false
    }
  }

  // 侧边栏筛选变更（关键词/日期范围）→ 重置回第一页
  const applyHistoryFilter = filter => {
    historyFilter.value = {
      keyword: String(filter?.keyword || '').trim(),
      start: filter?.start || null,
      end: filter?.end || null
    }
    loadChatHistory(false)
  }

  const loadHistoryDates = async () => {
    historyDates.value = await chatAPI.getChatHistoryDates('chat', selectedRoleName.value)
  }

  // 发出消息后当天必有记录（用户消息已落库）：本地补日期，避免再请求一次
  const markTodayHistoryActive = () => {
    const today = formatLocalDateTime(new Date())?.slice(0, 10)
    if (today && !historyDates.value.includes(today)) {
      historyDates.value = [today, ...historyDates.value]
    }
  }

  // 侧边栏删除非当前会话后本地移除（不再延迟全量重拉历史）
  const removeChatFromHistory = chatId => {
    const index = chatHistory.value.findIndex(chat => String(chat.id) === String(chatId))
    if (index !== -1) {
      chatHistory.value.splice(index, 1)
    }
    messageCache.remove(String(chatId))
    // 该日期可能因此没有记录，日历高亮需刷新
    loadHistoryDates()
  }

  // 搜索结果点击：跨会话跳转（先加载目标会话，再定位消息；skipScroll 防止自动滚底覆盖定位）
  const openChatAtMessage = async ({ chatId, messageNo, keyword } = {}) => {
    if (!chatId || !messageNo) {
      return
    }

    if (String(chatId) === String(currentChatId.value)) {
      handleJumpToMessage({ messageNo, keyword })
      return
    }

    const result = await loadChat(String(chatId), { skipScroll: true })
    if (result === null) {
      message.warning('会话不存在或已删除')
      return
    }
    handleJumpToMessage({ messageNo, keyword })
  }

  const loadLatestChat = async () => {
    await loadChatHistory(false)
    currentMessages.value = []

    if (chatHistory.value.length > 0) {
      const latestChat = chatHistory.value[0]
      await loadChat(String(latestChat.id))
      return
    }

    await startNewChat({ role: selectedRole, fromHistory: true })
  }

  // 默认初始化：优先选中最近对话角色，否则第一个角色；无可用角色时给出提示。
  // 供 onMounted 与 useChatRouteSync（角色不存在兜底）复用
  const initDefaultChat = async () => {
    if (roles.value.length === 0) {
      await loadRoles()
    }

    const lastId = roleStats.value?.lastRole?.id
    const defaultRole = lastId != null
      ? roles.value.find(role => String(role.value?.id) === String(lastId))
      : null

    if (defaultRole) {
      await startNewChat(defaultRole)
    } else if (roles.value.length > 0) {
      await startNewChat(roles.value[0])
    } else {
      message.warning('没有可用的角色，请检查角色配置')
    }
  }

  const startNewChat = async roleValue => {
    if (isStartingNewChat) {
      return
    }

    isStartingNewChat = true

    const isFromHistory = roleValue && roleValue.fromHistory
    const actualRole = isFromHistory ? roleValue.role : roleValue
    const newChatId = createLocalChatId()

    try {
      if (isFromHistory) {
        currentChatId.value = newChatId
        currentMessages.value = []

        const newChat = createHistoryItem(newChatId, selectedRoleName.value)
        chatHistory.value = [newChat, ...chatHistory.value]
        return
      }

      if (selectedRole.value == null || selectedRole.value.name !== actualRole.value.name) {
        const roleId = String(actualRole.value.id)

        selectedRole.value = {
          ...actualRole.value,
          id: roleId
        }
        selectedRoleName.value = actualRole.value.name

        // 筛选条件按角色隔离：切角色重置，避免带着上一角色的关键词/日期过滤
        historyFilter.value = { keyword: '', start: null, end: null }
        // 日历可用日期随角色刷新（不阻塞历史加载）
        loadHistoryDates()

        await loadChatHistory()

        if (chatHistory.value.length > 0) {
          message.success('切换新角色，默认加载最新对话')
          currentChatId.value = String(chatHistory.value[0].id)
          rolePanelCollapsed.value = true
          return
        }

        message.success('切换新角色，创建新对话')
        currentChatId.value = newChatId
        currentMessages.value = []
        chatHistory.value = [createHistoryItem(newChatId, selectedRoleName.value), ...chatHistory.value]
        rolePanelCollapsed.value = true
        return
      }

      if (currentMessages.value.length === 0) {
        message.info('当前会话还未开始')
        return
      }

      if (chatHistory.value.some(chat => String(chat.id) === newChatId)) {
        return
      }

      currentChatId.value = newChatId
      currentMessages.value = []

      const newChat = createHistoryItem(newChatId, selectedRoleName.value)
      chatHistory.value = [newChat, ...chatHistory.value]

      rolePanelCollapsed.value = true
    } finally {
      setTimeout(() => {
        isStartingNewChat = false
      }, 500)
    }
  }

  const sendMessage = async (formData, chatId) => {
    isStreaming.value = true
    const requestToken = createClientMessageId('req')
    activeRequestToken = requestToken
    abortController = new AbortController()
    let requestChatId = currentChatId.value
    // 会话身份由 /chat/pre 预建：新会话时 child 传入真实 sessionNo，既有会话传入当前 id。
    // 在发起请求前就认领，保证首条发送即使网络失败，currentChatId 也已是数据库中的真实会话号，
    // 重试时不会被剥成 null 或对不上号。
    const outgoingChatId = chatId && !isLocalOnlyChatId(chatId) ? String(chatId) : null
    if (outgoingChatId && outgoingChatId !== currentChatId.value) {
      const oldChatId = currentChatId.value ? String(currentChatId.value) : null
      currentChatId.value = outgoingChatId
      requestChatId = outgoingChatId
      const localChatIndex = chatHistory.value.findIndex(chat => String(chat.id) === oldChatId)
      if (localChatIndex !== -1) {
        chatHistory.value[localChatIndex] = {
          ...chatHistory.value[localChatIndex],
          id: outgoingChatId,
          source: 'server',
          lastMessageAt: new Date(),
          timestamp: new Date()
        }
      }
    }
    const lastUserMessageIndex = currentMessages.value.length - 1
    const lastUserMessage = currentMessages.value[lastUserMessageIndex]
    const assistantMessageId = createClientMessageId('assistant')
    const assistantMessage = {
      id: assistantMessageId,
      messageNo: assistantMessageId,
      hasAccurateTimestamp: true,
      role: 'assistant',
      content: '请求回复中...',
      toolSteps: [],
      timestamp: new Date(),
      chatModel: formData.get('model') || ''
    }

    currentMessages.value.push(assistantMessage)
    // 通过响应式代理引用更新内容，确保流式增量能触发视图更新
    const assistantMessageRef = currentMessages.value[currentMessages.value.length - 1]

    const discardAssistantMessage = () => {
      const index = currentMessages.value.findIndex(msg => msg.id === assistantMessageId)
      if (index !== -1) {
        currentMessages.value.splice(index, 1)
      }
    }
    const isRequestCancelled = () =>
      requestToken !== activeRequestToken || currentChatId.value !== requestChatId

    try {
      const { reader, sessionId } = await chatAPI.sendMessage(
        formData,
        outgoingChatId ? String(outgoingChatId) : outgoingChatId,
        { signal: abortController.signal }
      )
      if (sessionId && String(sessionId).trim()) {
        const nextSessionId = String(sessionId)
        const previousChatId = chatId ? String(chatId) : null

        if (!currentChatId.value || isLocalOnlyChatId(currentChatId.value) || currentChatId.value !== nextSessionId) {
          currentChatId.value = nextSessionId
          requestChatId = nextSessionId
        }

        const localChatIndex = chatHistory.value.findIndex(chat => String(chat.id) === previousChatId)
        if (localChatIndex !== -1) {
          chatHistory.value[localChatIndex] = {
            ...chatHistory.value[localChatIndex],
            id: nextSessionId,
            source: 'server',
            lastMessageAt: new Date(),
            timestamp: new Date(),
            chatModel: formData.get('model') || ''
          }
        }
      }
      let lastUpdateTime = 0
      const updateInterval = 250
      // 按 toolCallId 合并工具执行步骤到本消息，驱动步骤卡片实时渲染
      const upsertToolStep = step => {
        if (!Array.isArray(assistantMessageRef.toolSteps)) assistantMessageRef.toolSteps = []
        const existing = assistantMessageRef.toolSteps.find(item => item.toolCallId === step.toolCallId)
        if (existing) Object.assign(existing, step)
        else assistantMessageRef.toolSteps.push(step)
      }
      const result = await consumeChatSseReader(reader, {
        isCancelled: isRequestCancelled,
        onContent: content => {
          const now = Date.now()
          if (now - lastUpdateTime > updateInterval) {
            return nextTick(() => {
              // 直接更新本助手消息引用，避免写错其它消息
              assistantMessageRef.content = content
            }).then(() => {
              lastUpdateTime = now
            })
          }
          return undefined
        },
        onReasoning: reasoning => {
          assistantMessageRef.reasoningContent = reasoning
          return undefined
        },
        onToolCall: data => upsertToolStep({
          toolCallId: data.toolCallId,
          name: data.name,
          status: data.status || 'running',
          args: data.args
        }),
        onToolResult: data => upsertToolStep({
          toolCallId: data.toolCallId,
          name: data.name,
          status: data.status,
          result: data.result,
          error: data.error,
          durationMs: data.durationMs
        })
      })

      // 已被取消：保留已生成的部分内容（后端会随流终止持久化该内容），
      // 仅当尚未生成任何内容（后端同样不落库）时才丢弃本次占位消息
      if (result.status === 'cancelled' || isRequestCancelled()) {
        const partialContent = result && result.content ? String(result.content) : ''
        if (partialContent.trim() && partialContent.trim() !== '请求回复中...') {
          assistantMessageRef.content = partialContent
          assistantMessageRef.status = 'cancelled'
        } else {
          discardAssistantMessage()
        }
        return
      }

      await nextTick(() => {
        if (!result.content || !result.content.trim()) {
          message.error('返回内容为空，请稍后重试！')
          assistantMessageRef.status = 'error'
          assistantMessageRef.content = '返回内容为空，请稍后重试。'
          isStreaming.value = false
          return
        }

        assistantMessageRef.content = result.content
        assistantMessageRef.reasoningContent = result.reasoning
        assistantMessageRef.tokenUsage = result.usage
        assistantMessageRef.elapsedMs = result.elapsedMs
        // done.tools 为后端合并的完整汇总，覆盖实时增量（保证最终一致）
        if (Array.isArray(result.tools)) assistantMessageRef.toolSteps = result.tools
      })

      if (lastUserMessage && lastUserMessage.role === 'user') {
        delete lastUserMessage.status
      }
    } catch (error) {
      // 取消导致的 abort 错误不需要提示
      if (isRequestCancelled()) {
        discardAssistantMessage()
        return
      }

      const errorCode = error instanceof ChatSseStreamError ? error.code : null
      const errorMessage = error instanceof ChatSseStreamError
        ? error.message
        : isConnectionError(error)
          ? '无法连接服务器，请检查网络或后端服务'
          : '抱歉，请求服务器发生了错误，请稍后重试。'
      message.error(errorMessage)

      if (lastUserMessage && lastUserMessage.role === 'user') {
        lastUserMessage.status = 'failed'
      }

      assistantMessageRef.status = 'error'
      assistantMessageRef.content = errorMessage
      // 会话已推进导致无法重试：前端按钮切换为"更新当前会话"重新拉取服务端消息
      assistantMessageRef.retryStale = errorCode === 'CHAT_RETRY_STALE'
      // 会话已被删除：提示用户新建会话，并刷新会话列表
      if (errorCode === 'CHAT_SESSION_DELETED') {
        assistantMessageRef.sessionDeleted = true
        if (requestChatId) {
          messageCache.remove(String(requestChatId))
        }
        loadChatHistory(false)
      }

    } finally {
      if (requestToken === activeRequestToken) {
        isStreaming.value = false
      }
      if (abortController && abortController.signal.aborted) {
        abortController = null
      }
      // 发送后使该会话的消息缓存失效（流式消息与服务端回读形状不同，下次切回重拉）
      if (requestChatId) {
        messageCache.remove(String(requestChatId))
      }
      // 用户消息已落库，无论流成功与否当天都有记录：本地补日历日期
      markTodayHistoryActive()
      // 每次对话结束后刷新角色榜单（后端已在请求时计数）
      refreshRoleStatsDebounced()
    }
  }

  const cancelStreaming = () => {
    if (!isStreaming.value) return

    isStreaming.value = false
    // 失效本次请求令牌，让 sendMessage 检测到取消并决定保留部分内容还是丢弃
    activeRequestToken = null
    if (abortController) {
      abortController.abort()
      abortController = null
    }
    message.info('已取消请求')

    // 不再移除流式中的助手消息：已生成的部分内容后端会随流终止持久化，
    // 保留在列表中与后端保持一致；sendMessage 的取消分支负责写回最终内容或丢弃空消息
  }

  const updateCurrentMessages = newMessages => {
    currentMessages.value = newMessages
  }

  const appendMessage = newMessage => {
    currentMessages.value.push(newMessage)

    if (newMessage?.role !== 'user' || !currentChatId.value) {
      return
    }

    const chatIndex = chatHistory.value.findIndex(
      chat => String(chat.id) === String(currentChatId.value)
    )

    if (chatIndex === -1) {
      return
    }

    const currentChat = chatHistory.value[chatIndex]
    if (!shouldReplaceConversationTitle(currentChat)) {
      return
    }

    const previewTitle = buildConversationPreviewTitle(newMessage.content)
    chatHistory.value[chatIndex] = {
      ...currentChat,
      title: previewTitle,
      name: '',
      previewTitle,
      firstMessagePreview: previewTitle,
      lastMessageAt: newMessage.timestamp || new Date(),
      timestamp: newMessage.timestamp || currentChat.timestamp || new Date()
    }
  }

  const handlePanelToggle = collapsed => {
    rolePanelCollapsed.value = collapsed

    if (typeof window !== 'undefined' && window.innerWidth > 768) {
      window.localStorage.setItem('rolePanelLayoutVersion', ROLE_PANEL_LAYOUT_VERSION)
      window.localStorage.setItem('rolePanelCollapsed', String(collapsed))
    }
  }

  const handleClearChat = async () => {
    // 删除当前对话后刷新历史并加载最新对话（无记录时自动新建），与左侧删除逻辑一致
    if (currentChatId.value) {
      messageCache.remove(String(currentChatId.value))
    }
    loadHistoryDates()
    await loadLatestChat()
  }

  // 当前会话已被删除：回到全新的本地会话，避免停留在已删除会话上
  const handleSessionDeleted = () => {
    if (currentChatId.value) {
      messageCache.remove(String(currentChatId.value))
    }
    const newChatId = createLocalChatId()
    currentChatId.value = newChatId
    currentMessages.value = []
    const newChat = createHistoryItem(newChatId, selectedRoleName.value)
    chatHistory.value = [newChat, ...chatHistory.value]
    setTimeout(scrollToLastMessage, 100)
  }

  const handleRagChange = ragData => {
    ragEnabled.value = ragData.enabled
    selectedKnowledgeBase.value = ragData.knowledgeBase
  }

  const handleUpdateChatName = async ({ chatId, name }) => {
    const result = await chatAPI.updateChatName(chatId, name, 'chat', selectedRoleName.value)
    if (result.success) {
      const chatIndex = chatHistory.value.findIndex(chat => String(chat.id) === String(chatId))
      if (chatIndex !== -1) {
        chatHistory.value[chatIndex].name = name
      }
      message.success('会话名称已更新')
      return
    }

    message.error(result.msg || '更新会话名称失败')
  }

  const handleJumpToMessage = messageData => {
    nextTick(() => {
      // 消息列表已虚拟滚动：不可见消息不在 DOM 中，直接按索引交给虚拟列表定位并高亮
      const virtualList = getVirtualListController()
      if (!virtualList) {
        return
      }

      const resolveVirtualIndex = data => {
        if (typeof data.messageIndex === 'number' && data.messageIndex >= 0) {
          return data.messageIndex
        }
        if (data.messageNo != null) {
          const byId = currentMessages.value.findIndex(
            message => String(message?.messageNo || message?.id) === String(data.messageNo)
          )
          if (byId !== -1) {
            return byId
          }
        }
        if (data.content) {
          const byContent = currentMessages.value.findIndex(
            message => String(message?.content || '').includes(String(data.content).trim().slice(0, 30))
          )
          if (byContent !== -1) {
            return byContent
          }
        }
        return -1
      }

      const targetIndex = resolveVirtualIndex(messageData)
      if (targetIndex >= 0 && targetIndex < currentMessages.value.length) {
        virtualList.scrollToIndex(targetIndex, { align: 'auto', behavior: 'auto', highlight: true })
        // 带关键词的跳转（搜索结果/会话内搜索）：消息渲染后做字符级高亮并定位到命中处
        if (messageData.keyword) {
          waitForMessageAndHighlight(targetIndex, messageData.keyword)
        } else {
          clearKeywordHighlight()
        }
        message.success('已跳转到目标消息')
      } else {
        message.warning('未找到对应的消息，请确保消息已加载完成')
      }
    })
  }

  let resizeTimer = null

  const handleResize = () => {
    if (typeof window === 'undefined') return
    const isMobile = window.innerWidth <= 768

    if (isMobile) {
      // 移动端：默认收起侧边栏，把空间留给聊天区
      if (!sidebarCollapsed.value) sidebarCollapsed.value = true
    } else {
      // 桌面端：恢复桌面行为
      const storedSidebar = window.localStorage.getItem('sidebarCollapsed')
      if (storedSidebar !== null) {
        sidebarCollapsed.value = storedSidebar === 'true'
      }
    }
  }

  onMounted(async () => {
    const initLoadingMsg = message.loading('正在初始化聊天页面...', { duration: 0 })

    try {
      if (typeof window !== 'undefined') {
        const isMobile = window.innerWidth <= 768
        const storedRolePanelCollapsed = window.localStorage.getItem('rolePanelCollapsed')
        const storedRolePanelVersion = window.localStorage.getItem('rolePanelLayoutVersion')
        rolePanelCollapsed.value =
          window.innerWidth > 768
            ? storedRolePanelVersion === ROLE_PANEL_LAYOUT_VERSION && storedRolePanelCollapsed !== null
              ? storedRolePanelCollapsed === 'true'
              : false
            // 移动端默认收起角色面板（图标栏），把纵向空间留给聊天区
            : true

        if (window.innerWidth > 768 && storedRolePanelVersion !== ROLE_PANEL_LAYOUT_VERSION) {
          window.localStorage.setItem('rolePanelLayoutVersion', ROLE_PANEL_LAYOUT_VERSION)
          window.localStorage.setItem('rolePanelCollapsed', 'false')
        }

        // 移动端默认收起会话历史侧边栏，仅保留可展开的细长开关条
        if (isMobile) {
          sidebarCollapsed.value = true
        }
      }

      // 并行加载角色列表与使用统计榜单
      await Promise.all([loadRoles(), loadRoleStats()])

      // 深链进入（URL 带 roleId）时，初始角色/会话由 useChatRouteSync 驱动，此处不做默认初始化
      if (!route.params?.roleId) {
        await initDefaultChat()
      }
    } catch (error) {
      console.error('初始化失败:', error)
      message.error('初始化失败，请刷新页面重试')
    } finally {
      initLoadingMsg.destroy()
    }

    // 监听窗口尺寸变化（横竖屏切换、窗口缩放）
    if (typeof window !== 'undefined') {
      window.addEventListener('resize', () => {
        if (resizeTimer) clearTimeout(resizeTimer)
        resizeTimer = setTimeout(handleResize, 150)
      })
    }
  })

  onBeforeUnmount(() => {
    if (resizeTimer) {
      clearTimeout(resizeTimer)
      resizeTimer = null
    }
    if (roleStatsTimer) {
      clearTimeout(roleStatsTimer)
      roleStatsTimer = null
    }
  })

  return {
    appendMessage,
    applyHistoryFilter,
    cancelStreaming,
    chatHistory,
    currentChatId,
    currentChatName,
    currentMessages,
    handleClearChat,
    handleJumpToMessage,
    handlePanelToggle,
    handleRagChange,
    handleRolesUpdated,
    handleSessionDeleted,
    handleUpdateChatName,
    historyDates,
    historyHasMore,
    historyLoadingMore,
    initDefaultChat,
    isStreaming,
    loadChat,
    loadChatHistory,
    loadHistoryDates,
    loadLatestChat,
    loadMoreHistory,
    loadRoles,
    openChatAtMessage,
    ragEnabled,
    removeChatFromHistory,
    rolePanelCollapsed,
    roleStats,
    roles,
    selectedKnowledgeBase,
    selectedRole,
    selectedRoleName,
    sendMessage,
    sidebarCollapsed,
    startNewChat,
    switchConversation,
    toggleSidebar,
    updateCurrentMessages
  }
}
