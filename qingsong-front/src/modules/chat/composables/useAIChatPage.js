import { computed, nextTick, onMounted, onBeforeUnmount, ref } from 'vue'
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

const ROLE_PANEL_LAYOUT_VERSION = '2'
const LOCAL_CHAT_ID_PREFIX = 'temp-'

const createLocalChatId = () =>
  `${LOCAL_CHAT_ID_PREFIX}${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`

export const isLocalOnlyChatId = chatId => String(chatId || '').startsWith(LOCAL_CHAT_ID_PREFIX)

const DEFAULT_CONVERSATION_TITLES = new Set(['', '新会话', '新对话'])

const resolveConversationTimestamp = chat =>
  chat?.lastMessageAt || chat?.createdAt || chat?.timestamp || chat?.created_at || null

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

const toTimestampValue = value => {
  if (!value) {
    return 0
  }

  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? 0 : date.getTime()
}

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

  let isStartingNewChat = false
  let activeRequestToken = null
  let abortController = null

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

    loadChat(String(target.id), target)
  }

  const loadChat = async (chatId, role) => {    if (isStreaming.value) {
      isStreaming.value = false
    }

    const loadingMsg = message.loading('正在加载对话消息...', { duration: 0 })

    try {
      currentChatId.value = String(chatId)

      const roleName = selectedRoleName.value || 'user'

      const messages = await chatAPI.getChatMessages(String(chatId), 'chat', roleName)
      currentMessages.value = messages

      await nextTick()
      setTimeout(() => {
        scrollToLastMessage()
      }, 100)
    } catch (error) {
      console.error('加载对话消息失败:', error)
      currentMessages.value = []
      message.error('加载对话消息失败，请稍后重试')
    } finally {
      loadingMsg.destroy()
    }
  }

  const loadChatHistory = async (loadLastChat = true) => {
    const loadingMsg = message.loading('正在加载聊天历史...', { duration: 0 })

    try {
      const history = await chatAPI.getChatHistory('chat', selectedRoleName.value)

      const serverHistory = Array.isArray(history)
        ? history.map(item => {
            const chatId = String(item.id)
            const timestamp = resolveConversationTimestamp(item)

            return {
              id: chatId,
              title: item.title || item.name || '新会话',
              name: '',
              role: item.role,
              timestamp,
              createdAt: item.createdAt || timestamp,
              lastMessageAt: item.lastMessageAt || item.createdAt || timestamp,
              messageCount: item.messageCount ?? 0,
              source: 'server',
              desc: item.desc || ''
            }
          })
        : []

      const localHistory = chatHistory.value.filter(
        chat => chat.role === selectedRoleName.value && chat.source !== 'server'
      )

      const allHistory = [...localHistory, ...serverHistory]
      const uniqueHistory = allHistory.reduce((acc, current) => {
        if (!acc.find(item => item.id === current.id)) {
          acc.push(current)
        }

        return acc
      }, [])

      uniqueHistory.sort((a, b) => {
        const timeA = toTimestampValue(resolveConversationTimestamp(a))
        const timeB = toTimestampValue(resolveConversationTimestamp(b))
        return timeB - timeA
      })

      chatHistory.value = uniqueHistory

      if (loadLastChat && uniqueHistory.length > 0) {
        await loadChat(String(uniqueHistory[0].id), uniqueHistory[0])
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

  const loadLatestChat = async () => {
    await loadChatHistory(false)
    currentMessages.value = []

    if (chatHistory.value.length > 0) {
      const latestChat = chatHistory.value[0]
      await loadChat(String(latestChat.id), latestChat)
      return
    }

    await startNewChat({ role: selectedRole, fromHistory: true })
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

      // 已被取消：丢弃累积内容，移除本次助手消息，不再写回
      if (result.status === 'cancelled' || isRequestCancelled()) {
        discardAssistantMessage()
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
        loadChatHistory(false)
      }

    } finally {
      if (requestToken === activeRequestToken) {
        isStreaming.value = false
      }
      if (abortController && abortController.signal.aborted) {
        abortController = null
      }
      // 每次对话结束后刷新角色榜单（后端已在请求时计数）
      refreshRoleStatsDebounced()
    }
  }

  const cancelStreaming = () => {
    if (!isStreaming.value) return

    isStreaming.value = false
    // 失效本次请求令牌，让 sendMessage 的循环/最终写入检测到取消并丢弃本次回复
    activeRequestToken = null
    if (abortController) {
      abortController.abort()
      abortController = null
    }
    message.info('已取消请求')

    // 立即移除正在流式中的助手消息（按引用由 sendMessage 兜底，避免重复移除）
    const lastMessage = currentMessages.value[currentMessages.value.length - 1]
    if (lastMessage && lastMessage.role === 'assistant') {
      currentMessages.value.pop()
    }
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
    await loadLatestChat()
  }

  // 当前会话已被删除：回到全新的本地会话，避免停留在已删除会话上
  const handleSessionDeleted = () => {
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

      // 默认选中：优先使用后端统计的最近对话角色 lastRole.id，否则选第一个角色
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
    isStreaming,
    loadChat,
    loadChatHistory,
    loadLatestChat,
    loadRoles,
    ragEnabled,
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
