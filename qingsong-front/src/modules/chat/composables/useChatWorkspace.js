import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useDialog, useMessage } from 'naive-ui'
import { useVirtualizer } from '@tanstack/vue-virtual'
import { isNotified, getConnectionErrorMessage } from '@/services/networkError'
import { chatAPI, isConnectionError } from '../services/index.js'
import { useChatStore } from '../stores/index.js'
import { useTtsPlayback } from './useTtsPlayback.js'
import { isLocalOnlyChatId } from './useAIChatPage.js'
import { shouldAutoFollowMessages } from '../utils/index.js'
import { registerVirtualListController } from './virtualListController.js'
import { waitForMessageAndHighlight } from '../utils/textHighlight.js'

const TOOL_SELECTION_STORAGE_KEY = 'ai-chat-selected-tools'
const TOOL_GROUP_STORAGE_KEY = 'ai-chat-selected-tool-group'

// 未测量消息的估算高度（首次渲染/滚到未测区时使用，实测后自动替换）
const ESTIMATED_MESSAGE_HEIGHT = 300

// 后端下发的 messageNo 为 32 位 hex；本地客户端 id 形如 user-xxx，重试校验时无需传给后端
const SERVER_MESSAGE_ID_PATTERN = /^[0-9a-f]{32}$/i

const resolveServerMessageNo = message => {
  const id = message?.messageNo || message?.id || ''
  return SERVER_MESSAGE_ID_PATTERN.test(id) ? id : ''
}

export const useChatWorkspace = (props, emit, options = {}) => {
  const message = useMessage()
  const dialog = useDialog()
  const chatStore = useChatStore()
  const autoScroll = options.autoScroll || computed(() => true)

  const showRoleDescriptionModal = ref(false)
  // 角色聊天历史统计（来自后端 /info 接口），与角色描述合并展示
  const roleStatsInfo = ref({})
  // 角色聊天历史统计加载中
  const roleStatsLoading = ref(false)
  const isEditingConversationName = ref(false)
  const editingConversationName = ref('')
  const conversationNameInputRef = ref(null)
  const messagesRef = ref(null)

  // —— 消息列表虚拟滚动（@tanstack/vue-virtual 动态测量） ——
  // 只挂载可视区 ± overscan 的消息，不可见消息用上下 spacer 撑出滚动高度。
  const virtualizer = useVirtualizer(
    computed(() => ({
      getScrollElement: () => messagesRef.value,
      count: props.currentMessages.length,
      estimateSize: () => ESTIMATED_MESSAGE_HEIGHT,
      overscan: 6,
      getItemKey: index =>
        props.currentMessages[index]?.messageNo || props.currentMessages[index]?.id || index,
      // 测量含 margin 的完整行高（spacing 由 .virtual-message-slot 的 margin-bottom 承担）
      measureElement: el => {
        const style = typeof window !== 'undefined' ? window.getComputedStyle(el) : null
        const marginTop = style ? parseFloat(style.marginTop) || 0 : 0
        const marginBottom = style ? parseFloat(style.marginBottom) || 0 : 0
        return Math.round(el.getBoundingClientRect().height + marginTop + marginBottom)
      }
    }))
  )

  const virtualItems = computed(() => {
    const instance = virtualizer.value
    return instance ? instance.getVirtualItems() : []
  })

  const virtualPaddingStart = computed(() => virtualItems.value[0]?.start ?? 0)

  const virtualPaddingEnd = computed(() => {
    const items = virtualItems.value
    if (items.length === 0 || !virtualizer.value) {
      return 0
    }
    const last = items[items.length - 1]
    return Math.max(0, virtualizer.value.getTotalSize() - last.end)
  })

  const virtualItemMeasureElement = el => virtualizer.value?.measureElement(el)

  // 跳转到指定索引并（可选）高亮；目标不在 DOM 中时轮询等待其渲染后高亮
  const clearJumpHighlight = () => {
    document.querySelectorAll('.jump-highlight').forEach(node => {
      node.classList.remove('jump-highlight', 'jump-pulse')
    })
  }

  const highlightMessageByIndex = index => {
    const slot = messagesRef.value?.querySelector(`[data-index="${index}"]`)
    if (!slot) {
      return false
    }
    const target = slot.querySelector('.message-item') || slot
    clearJumpHighlight()
    target.classList.add('jump-highlight')
    setTimeout(() => target.classList.add('jump-pulse'), 100)
    setTimeout(() => {
      target.classList.remove('jump-highlight', 'jump-pulse')
    }, 4000)
    return true
  }

  const scrollToIndex = (index, { align = 'auto', behavior = 'auto', highlight = false } = {}) => {
    const instance = virtualizer.value
    if (!instance || index < 0 || index >= props.currentMessages.length) {
      return
    }
    instance.scrollToIndex(index, { align, behavior })
    if (highlight) {
      let tries = 0
      const attempt = () => {
        if (highlightMessageByIndex(index)) {
          return
        }
        if (tries++ >= 30) {
          return
        }
        requestAnimationFrame(attempt)
      }
      requestAnimationFrame(attempt)
    }
  }

  const inputRef = ref(null)
  const draftMessage = ref('')
  const showScrollButton = ref(false)
  const distanceFromBottom = ref(0)

  // 会话内搜索
  const searchQuery = ref('')
  const searchResults = ref([])
  const activeResultIndex = ref(-1)
  let searchDebounceTimer = null

  const scrollToSearchResult = index => {
    const id = searchResults.value[index]
    if (!id) return

    // 搜索结果存的是消息 id：先映射回真实索引，再交给虚拟列表定位
    const messageIndex = props.currentMessages.findIndex(
      message => String(message?.messageNo || message?.id) === String(id)
    )
    if (messageIndex < 0) return

    scrollToIndex(messageIndex, { align: 'auto', behavior: 'smooth', highlight: true })
    // 会话内搜索：跳转后对消息体内的关键词做字符级高亮定位
    const keyword = searchQuery.value.trim()
    if (keyword) {
      waitForMessageAndHighlight(messageIndex, keyword)
    }
  }

  const runSearch = () => {
    const query = searchQuery.value.trim().toLowerCase()
    if (!query) {
      searchResults.value = []
      activeResultIndex.value = -1
      return
    }

    const matches = props.currentMessages
      .filter(message => String(message?.content || '').toLowerCase().includes(query))
      .map(message => message.messageNo || message.id)
      .filter(Boolean)

    searchResults.value = matches
    if (matches.length > 0) {
      activeResultIndex.value = 0
      scrollToSearchResult(0)
    } else {
      activeResultIndex.value = -1
    }
  }

  const onSearchInput = (value) => {
    if (value !== undefined) searchQuery.value = value
    if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
    searchDebounceTimer = setTimeout(runSearch, 200)
  }

  const searchPrev = () => {
    if (searchResults.value.length === 0) return
    activeResultIndex.value =
      (activeResultIndex.value - 1 + searchResults.value.length) % searchResults.value.length
    scrollToSearchResult(activeResultIndex.value)
  }

  const searchNext = () => {
    if (searchResults.value.length === 0) return
    activeResultIndex.value = (activeResultIndex.value + 1) % searchResults.value.length
    scrollToSearchResult(activeResultIndex.value)
  }

  const clearSearch = () => {
    searchQuery.value = ''
    searchResults.value = []
    activeResultIndex.value = -1
  }

  // 右侧点状目录：按消息 id（优先）或真实索引定位并高亮，交给虚拟列表跳转
  const jumpToUserMessage = item => {
    if (!item) {
      return
    }

    let index = typeof item.index === 'number' ? item.index : -1

    if (index < 0 && item.id) {
      index = props.currentMessages.findIndex(
        message => String(message?.messageNo || message?.id) === String(item.id)
      )
    }

    if (index < 0 || index >= props.currentMessages.length) {
      return
    }

    scrollToIndex(index, { align: 'auto', behavior: 'smooth', highlight: true })
  }
  const attachedFiles = ref([])
  const selectedLanguage = ref('EN')
  const isPhrasePanelOpen = ref(false)
  const isRoleBadgeHovered = ref(false)
  const isRoleNameCopied = ref(false)
  const collapseAssistantMessages = ref(false)
  const modelOptions = ref([])
  const sourceOptions = ref([]) 
  const activeModelId = ref('')
  const activeSourceId = ref('')
  const isSwitchingModel = ref(false)
  const temperature = ref(0.7)
  const isExporting = ref(false)
  const isSendEmail = ref(false)

  // 上下文条数：0 表示无上下文；通过独立用户配置接口持久化
  const contextSize = ref(0)
  let contextSizeSaveTimer = null

  const updateContextSize = value => {
    let next = Number(value)
    if (!Number.isFinite(next)) {
      next = 0
    }
    contextSize.value = Math.min(100, Math.max(0, next))
  }

  const saveContextSize = async (value) => {
    try {
      await chatAPI.updateContextSize(value)
    } catch (error) {
      message.warning('上下文窗口大小保存失败：' + (error.message || '未知错误'))
    }
  }

  watch(contextSize, value => {
    if (typeof window === 'undefined') {
      return
    }
    if (contextSizeSaveTimer) {
      clearTimeout(contextSizeSaveTimer)
    }
    contextSizeSaveTimer = setTimeout(() => saveContextSize(value), 500)
  })

  let previousSelectedModelId = null
  let scrollTimer = null
  let scrollToBottomTimer = null

  const normalizeModelId = value => (value === null || value === undefined ? '' : String(value))

  const getActiveModel = () =>
    modelOptions.value.find(model => normalizeModelId(model.id) === normalizeModelId(activeModelId.value))

  const appendSharedMessageParams = formData => {
    formData.append('role', props.selectedRoleName || 'user')
    formData.append('language', selectedLanguage.value)

    const activeModel = getActiveModel()
    if (activeModel?.code) {
      formData.append('model', activeModel.code)
    }

    if (props.ragEnabled && props.selectedKnowledgeBase?.id) {
      formData.append('kownledgeId', String(props.selectedKnowledgeBase.id))
    }

    if (typeof window !== 'undefined') {
      const selectedToolGroup = window.localStorage.getItem(TOOL_GROUP_STORAGE_KEY)

      if (selectedToolGroup) {
        let groupKeys = []
        try {
          const parsed = JSON.parse(selectedToolGroup)
          if (Array.isArray(parsed)) {
            groupKeys = parsed.filter(key => typeof key === 'string' && key)
          }
        } catch {
          // 兼容旧格式：localStorage 直接存了单个分组 key 的字符串
          groupKeys = [selectedToolGroup]
        }

        groupKeys.forEach(key => {
          formData.append('toolGroupKeys', key)
        })
      }
    }

    if (temperature.value !== null && temperature.value !== undefined && temperature.value !== '') {
      formData.append('temperature', String(temperature.value))
    }
  }

  const isStreaming = computed(() => props.isStreaming)

  // 自动朗读：流式结束且最新一条为 AI 回复时，按用户开关朗读该条（朗读走模块级单例，已播放的会自动先停）
  const ttsPlayback = useTtsPlayback()

  watch(isStreaming, (newValue, oldValue) => {
    if (oldValue === true && newValue === false && ttsPlayback.autoPlay.value) {
      const messages = props.currentMessages
      const lastMessage = messages[messages.length - 1]
      if (lastMessage && lastMessage.role === 'assistant' && lastMessage.content && lastMessage.status !== 'cancelled') {
        ttsPlayback.play(lastMessage, message)
      }
    }
  })

  const roleDescription = computed(() => {
    if (!props.selectedRole) {
      return ''
    }

    return (
      props.selectedRole.value?.desc ||
      props.selectedRole.value?.description ||
      props.selectedRole.desc ||
      props.selectedRole.description ||
      ''
    )
  })

  if (typeof window !== 'undefined') {
    collapseAssistantMessages.value =
      window.localStorage.getItem('ai-chat-collapse-assistant') === '1'
  }

  watch(collapseAssistantMessages, value => {
    if (typeof window === 'undefined') {
      return
    }

    window.localStorage.setItem('ai-chat-collapse-assistant', value ? '1' : '0')
  })

  const openRoleDescription = async () => {
    showRoleDescriptionModal.value = true
    roleStatsLoading.value = true
    // 合并展示：同时拉取该角色的聊天历史统计
    // selectedRoleName 为空时兜底为 'default'，与聊天主流程 role_code 落库值保持一致
    const role = props.selectedRoleName || 'default'
    try {
      const info = await chatAPI.getChatHistoryInfo('chat', role)
      if (info && typeof info === 'object') {
        roleStatsInfo.value = info
      }
    } catch (e) {
      console.error('加载角色聊天历史统计失败', e)
    } finally {
      roleStatsLoading.value = false
    }
  }

  const startEditConversationName = async () => {
    isEditingConversationName.value = true
    editingConversationName.value = props.currentChatName || ''
    await nextTick()
    conversationNameInputRef.value?.focus()
  }

  const cancelEditConversationName = () => {
    isEditingConversationName.value = false
    editingConversationName.value = ''
  }

  const saveConversationName = () => {
    const nextName = editingConversationName.value.trim()
    if (!nextName) {
      message.warning('请输入会话名称')
      return
    }

    emit('update-chat-name', {
      chatId: props.currentChatId,
      name: nextName
    })

    isEditingConversationName.value = false
    editingConversationName.value = ''
  }

  const toggleAssistantMessages = () => {
    collapseAssistantMessages.value = !collapseAssistantMessages.value
  }

  const copyRoleName = async () => {
    const name = props.selectedRoleName || 'AI Assistant'

    try {
      await navigator.clipboard.writeText(name)
      isRoleNameCopied.value = true
      message.success('角色名称已复制')
      setTimeout(() => {
        isRoleNameCopied.value = false
      }, 2000)
    } catch (error) {
      console.error('复制角色名称失败:', error)
      message.error('复制失败')
    }
  }

  const loadModelOptions = async () => {
    try {
      const activeModels = await chatAPI.getActiveModels(activeSourceId.value)

      modelOptions.value = activeModels
        .filter(model => model.isActive)
        .map(model => ({
          ...model,
          id: normalizeModelId(model.id)
        }))
        .sort((left, right) => {
          if (Boolean(left.isTop) !== Boolean(right.isTop)) {
            return left.isTop ? -1 : 1
          }

          return (left.modelOrder || 0) - (right.modelOrder || 0)
        })

      if (modelOptions.value.length === 0) {
        activeModelId.value = ''
        previousSelectedModelId = null
        return
      }

      const retainedModel = modelOptions.value.find(
        model => model.id === normalizeModelId(activeModelId.value)
      )
      const preferredModel = retainedModel || modelOptions.value.find(model => model.isTop) || modelOptions.value[0]

      if (preferredModel) {
        activeModelId.value = preferredModel.id
        previousSelectedModelId = preferredModel.id
      }
    } catch (error) {
      console.error('加载模型列表失败:', error)
      modelOptions.value = []
      activeModelId.value = ''
      previousSelectedModelId = null
    }
  }

  const loadSourceOptions = async () => {
    try {
      const sourceOptionsData = await chatAPI.getSourceOptions()
      sourceOptions.value = sourceOptionsData

      const activeSource = sourceOptionsData.find(source => source.isActive)
      activeSourceId.value = activeSource?.id || sourceOptionsData[0]?.id || ''
    } catch (error) {
      console.error('加载源选项失败:', error)
      sourceOptions.value = []
    }
  }

  const handleModelSelectionChange = async () => {
    const nextModelId = normalizeModelId(activeModelId.value)

    if (!nextModelId || nextModelId === previousSelectedModelId) {
      return
    }

    const originalModelId = previousSelectedModelId
    isSwitchingModel.value = true

    try {
      const result = await chatAPI.switchTopModel(nextModelId)

      if (!result?.ok) {
        throw new Error(result?.msg || '切换模型失败')
      }

      previousSelectedModelId = nextModelId
      message.success('当前模型已切换')
      await loadModelOptions()
    } catch (error) {
      console.error('切换模型失败:', error)
      activeModelId.value = originalModelId || ''
      message.error(error.message || '切换模型失败，请稍后重试')
    } finally {
      isSwitchingModel.value = false
    }
  }

  const refreshModelOptions = async sourceId => {
    if (isSwitchingModel.value) {
      return
    }

    if (sourceId !== undefined) {
      activeSourceId.value = normalizeModelId(sourceId)
    }

    const selectedSource = sourceOptions.value.find(
      source => normalizeModelId(source.id) === normalizeModelId(activeSourceId.value)
    )

    if (selectedSource && !selectedSource.isActive) {
      isSwitchingModel.value = true
      try {
        const result = await chatAPI.toggleSourceActive(selectedSource.id)
        if (!result?.ok) {
          throw new Error(result?.msg || '启用模型来源失败')
        }
        await loadSourceOptions()
        message.success('模型来源已启用')
      } catch (error) {
        console.error('启用模型来源失败:', error)
        message.error(error.message || '启用模型来源失败')
        return
      } finally {
        isSwitchingModel.value = false
      }
    }

    await loadModelOptions()
  }

  const refreshSourceOptions = () => {
    if(isSwitchingModel.value){
      return
    }
    loadSourceOptions()
  }

  const handleScroll = () => {
    if (scrollTimer) {
      cancelAnimationFrame(scrollTimer)
    }

    scrollTimer = requestAnimationFrame(() => {
      if (!messagesRef.value) {
        return
      }

      const { scrollTop, scrollHeight, clientHeight } = messagesRef.value
      distanceFromBottom.value = Math.max(0, scrollHeight - scrollTop - clientHeight)
      showScrollButton.value = distanceFromBottom.value > 100
    })
  }

  const scrollToBottom = () => {
    if (!messagesRef.value) {
      return
    }

    if (scrollToBottomTimer) {
      cancelAnimationFrame(scrollToBottomTimer)
    }

    scrollToBottomTimer = requestAnimationFrame(() => {
      try {
        messagesRef.value.scrollTop = messagesRef.value.scrollHeight
        distanceFromBottom.value = 0
        showScrollButton.value = false
      } catch (error) {
        console.warn('滚动到底部失败:', error)
      }
    })
  }

  // 注册虚拟滚动控制器，供父级（useAIChatPage）的滚动到底/跳转消息走虚拟列表
  const unregisterVirtualList = registerVirtualListController({ scrollToIndex, scrollToBottom })

  watch(
    () => [
      props.currentMessages.length,
      props.currentMessages[props.currentMessages.length - 1]?.content,
      props.isStreaming
    ],
    async () => {
      if (!shouldAutoFollowMessages({
        enabled: autoScroll.value,
        distanceFromBottom: distanceFromBottom.value
      })) {
        return
      }

      await nextTick()
      scrollToBottom()
    },
    { flush: 'post' }
  )

  const adjustTextareaHeight = () => {
    const textarea = inputRef.value
    if (!textarea) {
      return
    }

    const MAX_HEIGHT = 240
    textarea.style.height = 'auto'
    const newHeight = Math.min(textarea.scrollHeight, MAX_HEIGHT)
    textarea.style.height = `${newHeight}px`
  }

  const confirmDeleteConversation = () => {
    if (!props.currentChatId) {
      message.info('当前没有可删除的对话')
      return
    }

    dialog.warning({
      title: '删除确认',
      content: '确定要删除当前对话吗？该操作不可恢复。',
      positiveText: '确定',
      negativeText: '取消',
      onPositiveClick: async () => {
        try {
          const result = await chatAPI.deleteChat(props.currentChatId, 'chat', props.selectedRoleName)
          if (result) {
            message.success('删除成功')
            // 删除的是当前对话：由页面刷新历史并加载最新对话（无记录时自动新建）
            emit('clear-chat')
          } else {
            message.error('删除失败')
          }
        } catch (error) {
          console.error('删除当前对话失败:', error)
          message.error('删除出错')
        }
      }
    })
  }

  const handleFileUpload = event => {
    const files = Array.from(event.target.files || [])
    if (files.length > 0) {
      attachedFiles.value = files
    }
  }

  const removeAttachedFile = index => {
    attachedFiles.value = attachedFiles.value.filter((_, fileIndex) => fileIndex !== index)
  }

  const formatFileSize = bytes => {
    if (bytes < 1024) {
      return `${bytes} B`
    }

    if (bytes < 1024 * 1024) {
      return `${(bytes / 1024).toFixed(1)} KB`
    }

    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  }

  const handlePaste = event => {
    const items = event.clipboardData?.items
    if (!items) {
      return
    }

    for (const item of items) {
      if (!item.type.includes('image')) {
        continue
      }

      event.preventDefault()
      const file = item.getAsFile()
      if (!file) {
        break
      }

      const timestamp = Date.now()
      const fileExtension = file.type.split('/')[1] || 'png'
      const pastedFile = new File([file], `pasted-image-${timestamp}.${fileExtension}`, {
        type: file.type
      })

      attachedFiles.value = [...attachedFiles.value, pastedFile]
      message.success('图片已加入附件列表')
      break
    }
  }

  const toggleLanguage = () => {
    selectedLanguage.value = selectedLanguage.value === 'CN' ? 'EN' : 'CN'
    message.info(`已切换系统提示词语言：${selectedLanguage.value=='CN' ? '中文' : '英文'}`)
  }

  const buildAttachmentFallbackPrompt = () => (
    selectedLanguage.value === 'CN'
      ? '请分析我上传的附件内容。'
      : 'Please analyze the attachment(s) I uploaded.'
  )

  const handleExport = async () => {
    if (isExporting.value) {
      message.warning('当前导出尚未完成，请稍后再试')
      return
    }

    let loadingMessage = null
    try {
      if (!props.selectedRole?.id || !props.currentChatId) {
        message.error('无法导出，缺少角色或会话信息')
        return
      }

      isExporting.value = true
      loadingMessage = message.loading('正在导出，请稍候...', { duration: 0 })
      const blob = await chatAPI.exportMessage(props.selectedRoleName || props.selectedRole.name, props.currentChatId)

      const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19)
      const fileName = `${props.selectedRoleName || '对话'}_${timestamp}.pdf`
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = fileName
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)

      loadingMessage?.destroy()
      message.success('导出成功')
    } catch (error) {
      console.error('导出对话失败:', error)
      loadingMessage?.destroy()
      message.error(error.message || '导出失败，请稍后重试')
    } finally {
      isExporting.value = false
    }
  }

  const handleSendEmail = async () => {
    try {
      if (!props.selectedRole?.id || !props.currentChatId) {
        message.error('无法发送邮件，缺少角色或会话信息')
        return
      }

      isSendEmail.value = true
      const roleName =
        props.selectedRoleName ||
        props.selectedRole?.name ||
        props.selectedRole?.value?.name

      if (!roleName) {
        message.error('无法发送邮件，缺少角色名称')
        return
      }

      message.loading('正在发送邮件...', { duration: 0 })
      const result = await chatAPI.sendEmail(roleName, props.currentChatId)
      message.destroyAll()

      if (result === true || result?.ok === true || result?.success === true) {
        message.success('邮件发送成功')
        return
      }

      if (result === false) {
        message.error('邮件发送失败，请稍后重试')
        return
      }

      message.success(result?.msg || result?.message || '邮件发送完成')
    } catch (error) {
      console.error('发送邮件失败:', error)
      message.destroyAll()
      message.error('发送邮件失败，请稍后重试')
    } finally {
      isSendEmail.value = false
    }
  }

  const getInputPlaceholder = () => {
    if (attachedFiles.value.length > 0) {
      return `已选择 ${attachedFiles.value.length} 个文件，可继续输入消息...`
    }

    return '输入消息，可上传图片、音频或视频...'
  }

  const handleSelectPhrase = phrase => {
    // 头尾清空：只插入去除首尾空白后的内容
    const phraseText = String(phrase || '').trim()
    if (!phraseText) return

    // 任意位置插入：以输入框光标所在位置为准（选中文本则替换选中区间）
    const textarea = inputRef.value
    const current = draftMessage.value
    const start = textarea ? (textarea.selectionStart ?? current.length) : current.length
    const end = textarea ? (textarea.selectionEnd ?? start) : start

    draftMessage.value = current.slice(0, start) + phraseText + current.slice(end)

    nextTick(() => {
      const el = inputRef.value
      if (el) {
        el.focus()
        const pos = start + phraseText.length
        el.setSelectionRange(pos, pos)
      }
    })
    setTimeout(adjustTextareaHeight, 0)
  }

  // 预分配服务端 messageNo 与会话身份；连接失败给出明确提示，其余失败提示获取失败。
  // 新会话（本地 temp 占位 id）由后端预建会话行并返回真实 sessionNo，重试时才会话身份稳定可比对。
  const ensureMessageNo = async () => {
    let pre
    try {
      const currentId = props.currentChatId ? String(props.currentChatId) : null
      const hasRealSession = currentId && !isLocalOnlyChatId(currentId)
      pre = await chatAPI.preChat({
        role: props.selectedRoleName || 'user',
        bizType: 'chat',
        sessionNo: hasRealSession ? currentId : null
      })
    } catch (error) {
      // 拦截器已对连接类错误（502/503/504/网络）统一弹过，此处判 isNotified 去重，避免双弹
      if (!isNotified(error)) {
        message.error(
          isConnectionError(error)
            ? getConnectionErrorMessage()
            : '获取消息号失败，请稍后重试'
        )
      }
      return null
    }
    if (!pre?.messageNo) {
      message.error('获取消息号失败，请稍后重试')
      return null
    }
    return pre
  }

  // 发送重入守卫：ensureMessageNo 为异步预检，期间 isStreaming 尚未置位、草稿未清空，
  // 快速连按 Enter 会重复发送同一条消息，这里同步置位 + finally 释放来防重入
  let isSubmitting = false

  const sendMessage = async () => {
    if (props.isStreaming || isSubmitting) {
      return
    }

    const messageContent = draftMessage.value.trim()
    if (!messageContent && !attachedFiles.value.length) {
      return
    }

    // 发送前校验：未选来源/模型时请求会静默缺少 model 参数，只能等后端报笼统错误，这里前置拦截并保留草稿
    if (!activeSourceId.value) {
      message.warning('请先在头部选择模型来源')
      return
    }
    if (!getActiveModel()?.code) {
      message.warning('请先在头部选择 AI 模型')
      return
    }

    const outgoingPrompt = messageContent || buildAttachmentFallbackPrompt()

    isSubmitting = true

    try {
      // 预分配服务端 messageNo 与会话身份：每条用户消息发送前都有后端 id，重试时才可比对
      const pre = await ensureMessageNo()
      if (!pre) {
        return
      }
      const userMessageNo = pre.messageNo

      const userMessage = {
        id: userMessageNo,
        messageNo: userMessageNo,
        hasAccurateTimestamp: true,
        role: 'user',
        content: outgoingPrompt,
        timestamp: new Date()
      }

      emit('append-message', userMessage)

      draftMessage.value = ''
      setTimeout(adjustTextareaHeight, 0)
      setTimeout(scrollToBottom, 100)

      const formData = new FormData()
      formData.append('prompt', outgoingPrompt)
      formData.append('messageNo', userMessageNo)

      attachedFiles.value.forEach(file => {
        formData.append('files', file)
      })

      appendSharedMessageParams(formData)

      // 新会话时 pre 已预建会话行：用返回的真实 sessionNo 作为本次 chatId，替换本地 temp 占位 id
      const effectiveChatId = pre.sessionNo || props.currentChatId
      emit('send-message', formData, effectiveChatId)

      attachedFiles.value = []
    } finally {
      isSubmitting = false
    }
  }

  const cancelStreamingRequest = () => {
    emit('cancel-streaming')
  }

  const handleRetryMessage = async failedMessage => {
    if (isStreaming.value) {
      message.warning('请等待当前消息完成')
      return
    }

    const messageIndex = props.currentMessages.findIndex(
      currentMessage =>
        currentMessage.id === failedMessage.id ||
        currentMessage.timestamp === failedMessage.timestamp
    )

    if (messageIndex === -1) {
      message.error('未找到需要重试的消息')
      return
    }

    let retryUserContent = failedMessage.content
    let targetMessageNo = resolveServerMessageNo(failedMessage)
    let remainingMessages = props.currentMessages.slice(0, messageIndex)

    if (failedMessage.role === 'assistant') {
      let userPrompt = ''
      for (let i = remainingMessages.length - 1; i >= 0; i--) {
        if (remainingMessages[i].role === 'user') {
          userPrompt = remainingMessages[i].content
          targetMessageNo = resolveServerMessageNo(remainingMessages[i])
          remainingMessages = remainingMessages.slice(0, i)
          break
        }
      }
      if (!userPrompt) {
        message.error('未找到对应的用户消息，无法重新生成')
        return
      }
      retryUserContent = userPrompt
    }

    // 重试需要原始 messageNo 供后端比对（Flow A：替换消息复用同一 id）；缺失时预分配一个新的
    if (!targetMessageNo) {
      const pre = await ensureMessageNo()
      if (!pre) {
        return
      }
      targetMessageNo = pre.messageNo
    }

    emit('update:currentMessages', remainingMessages)
    await nextTick()

    const formData = new FormData()
    formData.append('prompt', retryUserContent)
    formData.append('retry', true)
    formData.append('messageNo', targetMessageNo)
    appendSharedMessageParams(formData)

    const retryUserMessage = {
      id: targetMessageNo,
      messageNo: targetMessageNo,
      hasAccurateTimestamp: true,
      role: 'user',
      content: retryUserContent,
      timestamp: new Date()
    }

    emit('update:currentMessages', [...remainingMessages, retryUserMessage])
    setTimeout(scrollToBottom, 100)
    emit('send-message', formData, props.currentChatId)
  }

  // 就地编辑最新用户消息：行为与重试一致（截断该消息之后的内容并按新文本重发），后端无需改动。
  const handleEditMessage = async (userMessage, newContent) => {
    if (isStreaming.value) {
      message.warning('请等待当前消息完成')
      return
    }

    const targetContent = String(newContent || '').trim()
    if (!targetContent) {
      message.error('编辑内容不能为空')
      return
    }

    const messageIndex = props.currentMessages.findIndex(
      currentMessage =>
        currentMessage.id === userMessage.id ||
        currentMessage.timestamp === userMessage.timestamp
    )

    if (messageIndex === -1) {
      message.error('未找到需要编辑的消息')
      return
    }

    // 仅允许编辑用户消息；截断该消息之后的所有内容（与重试逻辑一致）
    if (props.currentMessages[messageIndex].role !== 'user') {
      message.error('仅支持编辑用户消息')
      return
    }

    const remainingMessages = props.currentMessages.slice(0, messageIndex)

    // 编辑复用原用户消息的 messageNo（Flow A）；缺失时预分配一个新的
    let targetMessageNo = resolveServerMessageNo(userMessage)
    if (!targetMessageNo) {
      const pre = await ensureMessageNo()
      if (!pre) {
        return
      }
      targetMessageNo = pre.messageNo
    }

    emit('update:currentMessages', remainingMessages)
    await nextTick()

    const formData = new FormData()
    formData.append('prompt', targetContent)
    formData.append('retry', true)
    formData.append('messageNo', targetMessageNo)
    appendSharedMessageParams(formData)

    const editUserMessage = {
      id: targetMessageNo,
      messageNo: targetMessageNo,
      hasAccurateTimestamp: true,
      role: 'user',
      content: targetContent,
      timestamp: new Date()
    }

    emit('update:currentMessages', [...remainingMessages, editUserMessage])
    setTimeout(scrollToBottom, 100)
    emit('send-message', formData, props.currentChatId)
  }

  // 会话已推进导致重试被拒：通知页面重新拉取当前会话的服务端消息
  const handleRefreshSession = () => {
    emit('refresh-session', props.currentChatId)
  }

  // 会话已被删除：通知页面回到全新的本地会话
  const handleSessionDeleted = () => {
    emit('session-deleted')
  }


  onMounted(async () => {
    await loadSourceOptions()
    await loadModelOptions()

    // 加载用户持久化的上下文窗口大小
    chatAPI.getContextSize().then(size => {
      contextSize.value = size
    }).catch(error => {
      console.warn('加载上下文窗口大小失败', error)
    })

    if (messagesRef.value) {
      messagesRef.value.addEventListener('scroll', handleScroll, { passive: true })
    }
  })

  onBeforeUnmount(() => {
    unregisterVirtualList()

    if (messagesRef.value) {
      messagesRef.value.removeEventListener('scroll', handleScroll)
    }

    if (scrollTimer) {
      cancelAnimationFrame(scrollTimer)
    }

    if (scrollToBottomTimer) {
      cancelAnimationFrame(scrollToBottomTimer)
    }

    if (contextSizeSaveTimer) {
      clearTimeout(contextSizeSaveTimer)
    }
  })

  return {
    activeModelId,
    activeSourceId,
    adjustTextareaHeight,
    attachedFiles,
    cancelEditConversationName,
    cancelStreamingRequest,
    chatStore,
    collapseAssistantMessages,
    confirmDeleteConversation,
    conversationNameInputRef,
    copyRoleName,
    contextSize,
    updateContextSize,
    draftMessage,
    editingConversationName,
    formatFileSize,
    getInputPlaceholder,
    handleExport,
    handleFileUpload,
    handlePaste,
    handleRetryMessage,
    handleEditMessage,
    handleRefreshSession,
    handleSessionDeleted,
    handleSelectPhrase,
    handleSendEmail,
    inputRef,
    isEditingConversationName,
    isPhrasePanelOpen,
    isRoleBadgeHovered,
    isRoleNameCopied,
    isExporting,
    isSendEmail,
    isStreaming,
    isSwitchingModel,
    jumpToUserMessage,
    messagesRef,
    virtualItems,
    virtualPaddingStart,
    virtualPaddingEnd,
    virtualItemMeasureElement,
    modelOptions,
    sourceOptions,
    handleModelSelectionChange,
    openRoleDescription,
    temperature,
    refreshModelOptions,
    refreshSourceOptions,
    removeAttachedFile,
    roleDescription,
    roleStatsInfo,
    roleStatsLoading,
    saveConversationName,
    scrollToBottom,
    selectedLanguage,
    sendMessage,
    showRoleDescriptionModal,
    showScrollButton,
    startEditConversationName,
    toggleAssistantMessages,
    toggleLanguage,
    searchQuery,
    searchResults,
    activeResultIndex,
    onSearchInput,
    searchPrev,
    searchNext,
    clearSearch
  }
}
