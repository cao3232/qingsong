<!--
修改记录:
- 日期: 2025-08-24
- 变更: 启用 content-visibility 与 contain-intrinsic-size 优化滚动性能；流式阶段轻量渲染，结束后再完整解析与高亮；挂载 window.copyCodeBlock 以修复代码复制按钮。
-->
<template name="ChatMessage">
  <div class="ai-message" :class="{ 'user-message': isUser, 'assistant-message': !isUser }"
    :data-message-id="messageAnchorId" :data-message-role="message.role" :data-timestamp="messageTimestampAttr">
    <!-- User Avatar (for flex-direction: row-reverse) -->
    <div class="message-avatar" v-if="isUser">
      <div class="avatar-container user-avatar">
        <img v-if="!userAvatarFailed" :src="userAvatarUrl" :alt="'用户头像'" class="avatar-image" loading="lazy"
          @error="userAvatarFailed = true" />
        <span v-else class="avatar-fallback-text">{{ userFallbackText }}</span>
      </div>
    </div>

    <!-- 消息内容 -->
    <div class="message-content">
      <!-- AI Header (Avatar + Info) -->
      <div class="message-info" v-if="!isUser">
        <div class="message-avatar">
          <div class="avatar-container ai-avatar">
            <img v-if="!aiAvatarFailed" :src="aiAvatarUrl" :alt="senderName + '头像'" class="avatar-image" loading="lazy"
              @error="aiAvatarFailed = true" />
            <span v-else class="avatar-fallback-text">{{ aiFallbackText }}</span>
          </div>
        </div>
        <div class="message-header">
          <span class="sender-name">{{ senderName }}</span>
          <span v-if="showMessageTime" class="message-time">{{ formatTime(message.timestamp) }}</span>
          <span v-if="props.message.chatModel" class="chat-model">powered by {{ props.message.chatModel }}</span>
          <span v-if="messageStatus === 'cancelled'" class="message-cancelled-badge"
            title="回复未完成，已生成内容已保存">已停止生成</span>
          <span v-if="props.message.elapsedMs != null && !props.loading" class="message-elapsed-time" title="本次模型流式响应耗时">
            耗时 {{ formatElapsedTime(props.message.elapsedMs) }}
          </span>
          <span v-if="props.message.tokenUsage && !props.loading" class="message-token-usage" title="本次模型调用的实际 token 用量">
            <span class="token-usage-title">用量</span>
            <span class="token-metric"><span>输入</span><b>{{ formatTokenCount(props.message.tokenUsage.promptTokens) }}</b></span>
            <span class="token-metric"><span>输出</span><b>{{ formatTokenCount(props.message.tokenUsage.completionTokens) }}</b></span>
            <span class="token-total"><b>{{ formatTokenCount(props.message.tokenUsage.totalTokens) }}</b><span>总 tokens</span></span>
          </span>
        </div>
      </div>

      <!-- 消息主体 -->
      <div class="message-body" ref="contentRef">
        <!-- 用户消息 -->
        <!-- 用户消息 -->
        <!-- 编辑/重试按钮（最新一条用户消息、非流式时显示） -->
        <div v-if="isUser && isLatestUserMessage && !props.isStreaming && !props.loading && !isEditing"
          class="message-retry-indicator">
          <button @click="startEdit" class="retry-btn" title="编辑">
            <PencilIcon class="retry-icon" />
            <span class="retry-text">编辑</span>
          </button>
          <button @click="handleRetry" class="retry-btn" title="重试">
            <ArrowPathIcon class="retry-icon" />
            <span class="retry-text">重试</span>
          </button>
        </div>

        <!-- AI 思考过程：独立于正文，默认收起 -->
        <div v-if="hasReasoning && !isUser" class="reasoning-block" :class="{ collapsed: !reasoningExpanded, active: loading }">
          <button class="reasoning-toggle" type="button" @click="toggleReasoning">
            <span class="reasoning-mark" aria-hidden="true"><i></i><i></i><i></i></span>
            <span class="reasoning-label">{{ loading ? '正在思考' : '已完成思考' }}</span>
            <span class="reasoning-meta">{{ reasoningExpanded ? '收起' : '查看' }}</span>
            <ChevronRightIcon class="chev" :class="{ expanded: reasoningExpanded }" />
          </button>
          <div v-show="reasoningExpanded" class="reasoning-content" v-html="reasoningHtml" @click="handleContentClick">
          </div>
        </div>

        <!-- AI 工具执行步骤：独立于正文，默认收起详情 -->
        <div v-if="toolSteps.length" class="tool-steps">
          <div v-for="step in toolSteps" :key="step.toolCallId" class="tool-step" :class="`tool-step-${step.status}`">
            <span class="tool-step-icon" aria-hidden="true">
              <span v-if="step.status === 'running'" class="tool-spinner"></span>
              <template v-else-if="step.status === 'success'">✅</template>
              <template v-else-if="step.status === 'failed'">❌</template>
              <template v-else>🔧</template>
            </span>
            <span class="tool-step-label">{{ toolStatusLabel(step.status) }}</span>
            <span class="tool-step-name">{{ step.name }}</span>
            <button v-if="step.args || step.result || step.error" class="tool-step-toggle" type="button"
              @click="toggleToolDetail(step.toolCallId)">
              {{ expandedToolIds[step.toolCallId] ? '收起' : '详情' }}
            </button>
            <div v-show="expandedToolIds[step.toolCallId]" class="tool-step-detail">
              <div v-if="step.args" class="tool-step-field"><span class="field-label">参数</span><pre>{{ step.args }}</pre></div>
              <div v-if="step.result" class="tool-step-field"><span class="field-label">结果</span><pre>{{ step.result }}</pre></div>
              <div v-if="step.error" class="tool-step-field error"><span class="field-label">错误</span><pre>{{ step.error }}</pre></div>
              <div v-if="step.durationMs != null" class="tool-step-field"><span class="field-label">耗时</span><span>{{ step.durationMs }}ms</span></div>
            </div>
          </div>
        </div>

        <!-- 用户消息编辑态（内联就地编辑） -->
        <div v-if="isUser && isEditing" class="user-edit-box">
          <textarea ref="editTextareaRef" v-model="editContent" class="user-edit-textarea" rows="3"
            @keydown.ctrl.enter="saveEdit" @keydown.meta.enter="saveEdit" @keydown.esc="cancelEdit"></textarea>
          <div class="user-edit-actions">
            <button class="edit-btn edit-cancel" type="button" @click="cancelEdit">取消</button>
            <button class="edit-btn edit-save" type="button" :disabled="!editContent.trim()"
              @click="saveEdit">保存并发送</button>
          </div>
        </div>
        <div v-else-if="isUser" class="user-message-content">
          <div class="user-text">
            <template v-if="message.content && String(message.content).trim()">{{ message.content }}</template>
            <span v-else-if="message.status === 'failed'" class="user-failed-hint">（发送失败）</span>
            <span v-else class="user-empty-hint">（空消息）</span>
          </div>
          <span v-if="showMessageTime" class="user-message-time">{{ formatTime(message.timestamp) }}</span>
        </div>

        <!-- AI 错误状态提示 -->
        <div v-else-if="showError" class="message-status-banner error">
          <ExclamationCircleIcon class="status-icon" />
          <div class="status-body">
            <div class="status-title">{{ isRateLimit ? '请求过于频繁' : '生成失败' }}</div>
            <div class="status-desc">{{ message.content || (isRateLimit ? '请求被限流，请稍后重试。' : '回复生成时出现错误，请重试。') }}</div>
          </div>
          <button class="status-retry" @click="handleErrorAction">
            {{ message.sessionDeleted ? '新建会话' : (message.retryStale ? '更新当前会话' : '重试') }}
          </button>
        </div>

        <!-- AI 生成中状态提示 -->
        <div v-else-if="isGenerating" class="ai-generating">
          <span class="gen-dot"></span>
          <span class="gen-dot"></span>
          <span class="gen-dot"></span>
          <span class="gen-text">AI 正在生成回复…</span>
        </div>

        <!-- AI 空内容占位 -->
        <div v-else-if="showEmptyPlaceholder" class="ai-empty">（暂无内容）</div>

        <!-- AI消息 -->
        <div v-else class="ai-text" :class="{ 'streaming': loading, 'collapsed': isAssistantCollapsed }"
          v-html="displayHtml" @click="handleContentClick">
        </div>

      </div>

      <!-- 操作按钮 -->
      <div class="message-actions" v-if="message.content">
        <div class="action-group">
          <button class="action-btn tts-btn" @click="toggleMessagePlay" :title="isCurrentPlaying ? '停止播放' : '朗读本条消息'"
            :class="{ 'playing': isCurrentPlaying }" :disabled="props.loading">
            <SpeakerXMarkIcon v-if="isCurrentPlaying" class="btn-icon tts-icon" />
            <SpeakerWaveIcon v-else class="btn-icon tts-icon" />
            <span class="btn-text">{{ isCurrentPlaying ? '停止' : '播放' }}</span>
          </button>
          <button class="action-btn download-btn" @click="downloadMessage" :disabled="isDownloading || props.loading"
            :title="isDownloading ? '正在生成语音…' : '下载本条语音（mp3）'">
            <ArrowDownTrayIcon class="btn-icon" />
            <span class="btn-text">{{ isDownloading ? '生成中' : '下载语音' }}</span>
          </button>
          <button class="action-btn copy-btn" @click="copyContent" :title="copyButtonTitle" :disabled="props.loading">
            <DocumentDuplicateIcon v-if="!copied" class="btn-icon" />
            <CheckIcon v-else class="btn-icon success" />
            <span class="btn-text">{{ copied ? '已复制' : '复制' }}</span>
          </button>
          <button class="action-btn share-btn" @click="openShare" :disabled="props.loading" title="分享本条消息为图片">
            <PhotoIcon class="btn-icon" />
            <span class="btn-text">分享图片</span>
          </button>
          <button class="action-btn regenerate-btn" v-if="!isUser && props.isLatestMessage && !props.loading"
            @click="handleRetry" title="重新生成">
            <ArrowPathIcon class="btn-icon regenerate-icon" />
            <span class="btn-text">重新生成</span>
          </button>
          <button class="action-btn send-btn" @click="sendToFeishu" title="发送到飞书" v-if="!isUser"
            :disabled="props.loading">
            <PaperAirplaneIcon class="btn-icon" />
            <span class="btn-text">发送飞书</span>
          </button>
        </div>
      </div>
    </div>

    <!-- 分享当前消息为图片 -->
    <MessageShareModal v-model:show="showShareModal" :message="props.message" :is-user="isUser"
      :sender-name="shareSenderName" :avatar-url="isUser ? userAvatarUrl : aiAvatarUrl"
      :chat-model="props.message?.chatModel" :content-node="shareContentNode"
      :previous-message="props.previousMessage" />

    <Teleport to="body">
      <div v-if="mermaidPreviewOpen" ref="mermaidPreviewDialogRef" class="mermaid-preview-backdrop" role="dialog" aria-modal="true"
        aria-label="Mermaid 流程图预览" @click.self="closeMermaidPreview" @keydown.esc="closeMermaidPreview">
        <div class="mermaid-preview-panel">
          <div class="mermaid-preview-toolbar">
            <span class="mermaid-preview-scale">{{ mermaidPreviewScalePercent }}%</span>
            <button type="button" title="缩小" aria-label="缩小" @click="zoomMermaidPreview(-0.1)">
              <MinusIcon />
            </button>
            <button type="button" title="放大" aria-label="放大" @click="zoomMermaidPreview(0.1)">
              <PlusIcon />
            </button>
            <button type="button" title="适应窗口" aria-label="适应窗口" @click="fitMermaidPreview">
              <ArrowsPointingOutIcon />
            </button>
            <button type="button" title="按 1:1 显示" aria-label="按原始尺寸显示流程图"
              @click="resetMermaidPreview">1:1</button>
            <button type="button" title="关闭" aria-label="关闭" @click="closeMermaidPreview">
              <XMarkIcon />
            </button>
          </div>
          <div ref="mermaidPreviewViewportRef" class="mermaid-preview-viewport" tabindex="-1"
            @wheel.prevent="handleMermaidPreviewWheel" @pointerdown="handleMermaidPointerDown"
            @pointermove="handleMermaidPointerMove" @pointerup="handleMermaidPointerUp"
            @pointercancel="handleMermaidPointerUp" @lostpointercapture="handleMermaidLostPointerCapture">
            <div class="mermaid-preview-canvas" :style="mermaidPreviewCanvasStyle" v-html="mermaidPreviewSvg"></div>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { API_BASE_URL } from '@/config/env'
import http from '@/utils/http'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useChatStore } from '../stores/index.js'
import { useThemeStore } from '@/stores/theme'
import {
  DocumentDuplicateIcon,
  CheckIcon,
  ChevronRightIcon,
  PaperAirplaneIcon,
  ExclamationCircleIcon,
  ArrowPathIcon,
  PencilIcon,
  SpeakerWaveIcon,
  SpeakerXMarkIcon,
  ArrowDownTrayIcon,
  PhotoIcon,
  MinusIcon,
  PlusIcon,
  ArrowsPointingOutIcon,
  XMarkIcon
} from '@heroicons/vue/24/outline'
import MessageShareModal from './MessageShareModal.vue'
import 'highlight.js/styles/github-dark.css'
import { useMessage } from 'naive-ui'
import { authService } from '@/services/authService'
import { generateUserAvatar, generateAIAvatar, buildAvatarUrl } from '../../../shared/utils/index.js'
import { useChatMessageContent } from '../composables/useChatMessageContent.js'
import { useTtsPlayback } from '../composables/index.js'
import { shouldShowMessageTimestamp } from '../utils/index.js'

const props = defineProps({
  message: {
    type: Object,
    required: true,
    validator: (value) => value.role && value.content !== undefined
  },
  loading: {
    type: Boolean,
    default: false
  },
  selectedRole: {
    type: Object,
    default: null
  },
  isLatestMessage: {
    type: Boolean,
    default: false
  },
  isLatestUserMessage: {
    type: Boolean,
    default: false
  },
  isStreaming: {
    type: Boolean,
    default: false
  },
  previousMessage: {
    type: Object,
    default: null
  },
  collapseAssistant: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['retry', 'edit', 'refresh-session', 'session-deleted'])

const contentRef = ref(null)
const copied = ref(false)
const messageApi = useMessage()
const chatStore = useChatStore()
const themeStore = useThemeStore()
const ttsPlayback = useTtsPlayback()

const isCurrentPlaying = computed(() =>
  ttsPlayback.isPlaying.value &&
  ttsPlayback.playingMessageNo.value === (props.message?.messageNo || props.message?.id)
)

const isDownloading = computed(() =>
  ttsPlayback.downloadingMessageNo.value === (props.message?.messageNo || props.message?.id)
)

const toggleMessagePlay = () => {
  ttsPlayback.togglePlay(props.message, messageApi)
}

const downloadMessage = async () => {
  await ttsPlayback.downloadAudio(props.message, messageApi)
}

onBeforeUnmount(() => {
  if (isCurrentPlaying.value) {
    ttsPlayback.stop()
  }
})

const isUser = computed(() => props.message?.role === 'user')
const copyButtonTitle = computed(() => copied.value ? '已复制' : '复制内容')

// 工具执行步骤：来自 SSE tool_call/tool_result 的实时合并（详见 useAIChatPage.js）
const toolSteps = computed(() =>
  Array.isArray(props.message?.toolSteps) ? props.message.toolSteps : []
)
const expandedToolIds = ref({})
const toggleToolDetail = id => {
  expandedToolIds.value[id] = !expandedToolIds.value[id]
}
const toolStatusLabel = status => ({ running: '执行中', success: '成功', failed: '失败' }[status] || status || '')

const messageStatus = computed(() => props.message?.status || '')
const isRateLimit = computed(() =>
  /429|频繁|限流|rate.?limit|too many|请求次数|quota/i.test(
    props.message?.content || props.message?.errorDetail || ''
  )
)
const hasRealContent = computed(() => {
  const content = props.message?.content
  return Boolean(content && String(content).trim() && String(content).trim() !== '请求回复中...')
})
const isGenerating = computed(() => props.loading && !hasRealContent.value)
const showEmptyPlaceholder = computed(
  () => !isUser.value && !props.loading && !hasRealContent.value && messageStatus.value !== 'error'
)
const showError = computed(() => messageStatus.value === 'error')
const showMessageTime = computed(() => shouldShowMessageTimestamp({
  enabled: themeStore.config.showTimestamp,
  timestamp: props.message?.timestamp,
  hasAccurateTimestamp: props.message?.hasAccurateTimestamp
}))
const messageAnchorId = computed(() => props.message?.messageNo || props.message?.id || null)
const messageTimestampAttr = computed(() => {
  if (!showMessageTime.value) {
    return null
  }

  const timestamp = new Date(props.message.timestamp)
  return Number.isNaN(timestamp.getTime()) ? null : timestamp.toISOString()
})

const sessionId = computed(() => {
  if (props.message?.sessionId) return props.message.sessionId
  if (props.message?.chatId) return props.message.chatId
  if (props.message?.messageNo) return props.message.messageNo
  if (props.message?.id) return props.message.id
  if (props.message?.timestamp) {
    const date = new Date(props.message.timestamp)
    return `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`
  }
  return 'default-session'
})

// 用户在设置中配置头像后，对话框固定使用该配置，不再按会话动态生成
const userAvatarUrl = computed(() => {
  const cfg = themeStore.config.userAvatar
  const configured = cfg?.enabled ? buildAvatarUrl(cfg) : ''
  return configured || generateUserAvatar(sessionId.value)
})

const aiAvatarUrl = computed(() => {
  const cfg = themeStore.config.aiAvatar
  const configured = cfg?.enabled ? buildAvatarUrl(cfg) : ''
  if (configured) return configured
  const aiId = props.selectedRole?.id || props.selectedRole?.name || sessionId.value
  return generateAIAvatar(aiId)
})

// 头像加载失败时回退到文字头像；配置变化后重置，重新尝试加载
const userAvatarFailed = ref(false)
const aiAvatarFailed = ref(false)
watch(userAvatarUrl, () => { userAvatarFailed.value = false })
watch(aiAvatarUrl, () => { aiAvatarFailed.value = false })

const userFallbackText = computed(() => {
  const session = authService.getSession?.()
  const account = session?.account || session?.username || session?.nickname || ''
  return account ? String(account).charAt(0).toUpperCase() : '我'
})
const aiFallbackText = computed(() => (senderName.value || 'AI').charAt(0).toUpperCase())

const senderName = computed(() => props.selectedRole?.name || 'AI助手')

const MIN_MERMAID_PREVIEW_SCALE = 0.05
const MAX_MERMAID_PREVIEW_SCALE = 3
const MERMAID_PREVIEW_BODY_LOCK = Symbol.for('mt-ai-front.mermaid-preview-body-lock')
const mermaidPreviewOpen = ref(false)
const mermaidPreviewScale = ref(1)
const mermaidPreviewSvg = ref('')
const mermaidPreviewSize = ref({ width: 900, height: 600 })
const mermaidPreviewX = ref(0)
const mermaidPreviewY = ref(0)
const mermaidPreviewDialogRef = ref(null)
const mermaidPreviewViewportRef = ref(null)
const activePreviewPointers = new Map()
const dragOrigin = ref(null)
const pinchOrigin = ref(null)
let previousBodyOverflow = null
let mermaidPreviewPreviousActiveElement = null
let mermaidPreviewOperationToken = 0

const clampMermaidPreviewScale = value => {
  const safeValue = Number.isFinite(value)
    ? value
    : (Number.isFinite(mermaidPreviewScale.value) ? mermaidPreviewScale.value : 1)
  return Math.min(MAX_MERMAID_PREVIEW_SCALE, Math.max(MIN_MERMAID_PREVIEW_SCALE, safeValue))
}

const mermaidPreviewScalePercent = computed(() => Math.round(mermaidPreviewScale.value * 100))
const mermaidPreviewCanvasStyle = computed(() => ({
  top: '50%',
  left: '50%',
  width: `${mermaidPreviewSize.value.width}px`,
  height: `${mermaidPreviewSize.value.height}px`,
  transform: `translate(calc(-50% + ${mermaidPreviewX.value}px), calc(-50% + ${mermaidPreviewY.value}px)) scale(${mermaidPreviewScale.value})`
}))

const zoomMermaidPreview = amount => {
  mermaidPreviewScale.value = clampMermaidPreviewScale(mermaidPreviewScale.value + amount)
}

const fitMermaidPreview = () => {
  const viewport = mermaidPreviewViewportRef.value
  if (!viewport) return

  const availableWidth = Math.max(viewport.clientWidth - 48, 1)
  const availableHeight = Math.max(viewport.clientHeight - 48, 1)
  mermaidPreviewScale.value = clampMermaidPreviewScale(Math.min(
    availableWidth / mermaidPreviewSize.value.width,
    availableHeight / mermaidPreviewSize.value.height,
    1
  ))
  mermaidPreviewX.value = 0
  mermaidPreviewY.value = 0
}

const resetMermaidPreview = () => {
  mermaidPreviewScale.value = 1
  mermaidPreviewX.value = 0
  mermaidPreviewY.value = 0
}

const cleanupMermaidPreviewPointers = () => {
  const viewport = mermaidPreviewViewportRef.value
  for (const pointerId of activePreviewPointers.keys()) {
    try {
      if (!viewport?.hasPointerCapture || viewport.hasPointerCapture(pointerId)) {
        viewport?.releasePointerCapture?.(pointerId)
      }
    } catch {
      // Pointer capture may already have been released by the browser.
    }
  }
  activePreviewPointers.clear()
  dragOrigin.value = null
  pinchOrigin.value = null
}

const handleMermaidPreviewDocumentKeydown = event => {
  if (!mermaidPreviewOpen.value) return

  if (event.key === 'Escape') {
    event.preventDefault()
    closeMermaidPreview()
    return
  }

  if (event.key !== 'Tab') return

  const dialog = mermaidPreviewDialogRef.value
  if (!dialog) return
  const focusableElements = Array.from(dialog.querySelectorAll(
    'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
  ))
  const firstFocusable = focusableElements[0] || mermaidPreviewViewportRef.value
  const lastFocusable = focusableElements.at(-1) || firstFocusable
  const activeElement = document.activeElement

  if (event.shiftKey && (activeElement === firstFocusable || !dialog.contains(activeElement))) {
    event.preventDefault()
    lastFocusable?.focus()
  } else if (!event.shiftKey && (activeElement === lastFocusable || !dialog.contains(activeElement))) {
    event.preventDefault()
    firstFocusable?.focus()
  }
}

const openMermaidPreview = async ({ svg, width, height }) => {
  const operationToken = ++mermaidPreviewOperationToken
  if (!svg) return

  const currentBodyLockOwner = document.body[MERMAID_PREVIEW_BODY_LOCK]
  if (currentBodyLockOwner && currentBodyLockOwner !== closeMermaidPreview) {
    currentBodyLockOwner()
  }

  if (!mermaidPreviewOpen.value) {
    mermaidPreviewPreviousActiveElement = document.activeElement instanceof Element
      ? document.activeElement
      : null
    previousBodyOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    document.body[MERMAID_PREVIEW_BODY_LOCK] = closeMermaidPreview
    document.addEventListener('keydown', handleMermaidPreviewDocumentKeydown, true)
  }

  const previewSize = {
    width: Number.isFinite(width) && width > 0 ? width : 900,
    height: Number.isFinite(height) && height > 0 ? height : 600
  }
  mermaidPreviewSvg.value = svg
  mermaidPreviewSize.value = previewSize
  mermaidPreviewOpen.value = true
  resetMermaidPreview()

  await nextTick()
  if (
    operationToken !== mermaidPreviewOperationToken ||
    !mermaidPreviewOpen.value ||
    mermaidPreviewSvg.value !== svg ||
    mermaidPreviewSize.value !== previewSize
  ) {
    return
  }
  const focusableElements = mermaidPreviewDialogRef.value?.querySelectorAll('button:not([disabled])') || []
  focusableElements[0]?.focus()
  if (!focusableElements.length) mermaidPreviewViewportRef.value?.focus()
  fitMermaidPreview()
}

const closeMermaidPreview = () => {
  mermaidPreviewOperationToken += 1
  document.removeEventListener('keydown', handleMermaidPreviewDocumentKeydown, true)
  cleanupMermaidPreviewPointers()
  if (!mermaidPreviewOpen.value) return

  const previousActiveElement = mermaidPreviewPreviousActiveElement
  mermaidPreviewPreviousActiveElement = null
  mermaidPreviewOpen.value = false
  mermaidPreviewScale.value = 1
  mermaidPreviewSvg.value = ''
  mermaidPreviewSize.value = { width: 900, height: 600 }
  mermaidPreviewX.value = 0
  mermaidPreviewY.value = 0

  if (document.body[MERMAID_PREVIEW_BODY_LOCK] === closeMermaidPreview && previousBodyOverflow !== null) {
    document.body.style.overflow = previousBodyOverflow
    delete document.body[MERMAID_PREVIEW_BODY_LOCK]
    previousBodyOverflow = null
  }
  previousActiveElement?.focus?.()
}

const getPreviewPointerPair = () => Array.from(activePreviewPointers.values()).slice(0, 2)
const getPreviewPointerDistance = ([first, second]) => Math.hypot(
  second.clientX - first.clientX,
  second.clientY - first.clientY
)

const startMermaidPinch = () => {
  const pointers = getPreviewPointerPair()
  const viewport = mermaidPreviewViewportRef.value
  if (pointers.length < 2 || !viewport) return

  const centerX = (pointers[0].clientX + pointers[1].clientX) / 2
  const centerY = (pointers[0].clientY + pointers[1].clientY) / 2
  const viewportRect = viewport.getBoundingClientRect()
  dragOrigin.value = null
  pinchOrigin.value = {
    distance: Math.max(getPreviewPointerDistance(pointers), 1),
    centerX,
    centerY,
    scale: mermaidPreviewScale.value,
    x: mermaidPreviewX.value,
    y: mermaidPreviewY.value,
    centerToCanvasX: centerX - viewportRect.left - viewport.clientWidth / 2 - mermaidPreviewX.value,
    centerToCanvasY: centerY - viewportRect.top - viewport.clientHeight / 2 - mermaidPreviewY.value
  }
}

const rebuildMermaidPreviewGesture = () => {
  pinchOrigin.value = null
  if (activePreviewPointers.size >= 2) {
    startMermaidPinch()
    return
  }

  const remainingPointer = activePreviewPointers.entries().next().value
  if (remainingPointer) {
    const [pointerId, point] = remainingPointer
    dragOrigin.value = {
      pointerId,
      clientX: point.clientX,
      clientY: point.clientY,
      x: mermaidPreviewX.value,
      y: mermaidPreviewY.value
    }
    return
  }

  dragOrigin.value = null
}

const handleMermaidPointerDown = event => {
  activePreviewPointers.set(event.pointerId, { clientX: event.clientX, clientY: event.clientY })
  try {
    event.currentTarget?.setPointerCapture?.(event.pointerId)
  } catch {
    // Pointer capture is optional and can fail for an inactive pointer.
  }

  if (activePreviewPointers.size >= 2) {
    startMermaidPinch()
    return
  }

  pinchOrigin.value = null
  dragOrigin.value = {
    pointerId: event.pointerId,
    clientX: event.clientX,
    clientY: event.clientY,
    x: mermaidPreviewX.value,
    y: mermaidPreviewY.value
  }
}

const handleMermaidPointerMove = event => {
  if (!activePreviewPointers.has(event.pointerId)) return
  activePreviewPointers.set(event.pointerId, { clientX: event.clientX, clientY: event.clientY })

  if (activePreviewPointers.size >= 2 && pinchOrigin.value) {
    const pointers = getPreviewPointerPair()
    const centerX = (pointers[0].clientX + pointers[1].clientX) / 2
    const centerY = (pointers[0].clientY + pointers[1].clientY) / 2
    const nextScale = clampMermaidPreviewScale(
      pinchOrigin.value.scale * getPreviewPointerDistance(pointers) / pinchOrigin.value.distance
    )
    const scaleRatio = nextScale / pinchOrigin.value.scale
    mermaidPreviewScale.value = nextScale
    mermaidPreviewX.value = pinchOrigin.value.x + centerX - pinchOrigin.value.centerX +
      pinchOrigin.value.centerToCanvasX * (1 - scaleRatio)
    mermaidPreviewY.value = pinchOrigin.value.y + centerY - pinchOrigin.value.centerY +
      pinchOrigin.value.centerToCanvasY * (1 - scaleRatio)
    return
  }

  if (dragOrigin.value?.pointerId === event.pointerId) {
    mermaidPreviewX.value = dragOrigin.value.x + event.clientX - dragOrigin.value.clientX
    mermaidPreviewY.value = dragOrigin.value.y + event.clientY - dragOrigin.value.clientY
  }
}

const handleMermaidPointerUp = event => {
  activePreviewPointers.delete(event.pointerId)
  try {
    if (!event.currentTarget?.hasPointerCapture || event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget?.releasePointerCapture?.(event.pointerId)
    }
  } catch {
    // State cleanup must continue if capture was already lost.
  }
  rebuildMermaidPreviewGesture()
}

const handleMermaidLostPointerCapture = event => {
  activePreviewPointers.delete(event.pointerId)
  rebuildMermaidPreviewGesture()
}

const handleMermaidPreviewWheel = event => {
  zoomMermaidPreview(event.deltaY < 0 ? 0.1 : -0.1)
}

// 分享卡片中的发送者显示名：用户消息显示账号名/「我」，AI 消息显示角色名
const shareSenderName = computed(() => {
  if (!isUser.value) return senderName.value
  const session = authService.getSession?.()
  return session?.nickname || session?.username || session?.account || '我'
})
const {
  copyMessageContent,
  handleContentClick,
  handleImageLoading,
  highlightCode,
  renderMermaid,
  processedContent,
  hasReasoning,
  reasoningHtml,
  reasoningExpanded,
  toggleReasoning,
  markTableOverflow,
  resetContentState
} = useChatMessageContent({
  contentRef,
  isUser,
  loading: computed(() => props.loading),
  message: computed(() => props.message),
  messageApi,
  onMermaidPreview: openMermaidPreview
})

const formatTime = timestamp => {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  const now = new Date()
  const diff = now - date

  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  return date.toLocaleDateString()
}

const formatTokenCount = value => {
  const count = Number(value)
  return Number.isFinite(count) ? count.toLocaleString('zh-CN') : '-'
}

const formatElapsedTime = value => {
  const elapsedMs = Number(value)
  if (!Number.isFinite(elapsedMs) || elapsedMs < 0) return ''
  if (elapsedMs < 1000) return `${Math.round(elapsedMs)} ms`
  return `${(elapsedMs / 1000).toFixed(2)} s`
}

const isAssistantCollapsed = computed(() => {
  if (isUser.value) return false
  if (props.loading) return false
  return props.collapseAssistant && Boolean(props.message?.content)
})

const copyContent = async () => {
  if (await copyMessageContent()) {
    copied.value = true
    messageApi.success(isUser.value ? '用户消息已复制' : 'AI回复已复制')
    setTimeout(() => {
      copied.value = false
    }, 2000)
  } else {
    messageApi.error('复制失败，请重试')
  }
}

// 分享当前消息为图片
const showShareModal = ref(false)
const shareContentNode = ref(null)

const openShare = () => {
  if (props.loading) return
  const node = contentRef.value?.querySelector(isUser.value ? '.user-text' : '.ai-text')
  if (!node) return
  const clone = node.cloneNode(true)
  // 去掉流式/折叠状态，让分享卡片展示完整内容
  clone.classList?.remove('streaming', 'collapsed', 'incomplete')
  // 分享卡片里的图片改为即时加载，确保 html2canvas 能截到图
  clone.querySelectorAll?.('img').forEach(img => {
    img.loading = 'eager'
  })
  shareContentNode.value = clone
  showShareModal.value = true
}

// 发送到飞书
const sendToFeishu = async () => {
  try {
    const result = await http.post(API_BASE_URL + '/message/send-feishu-msg', {
      message: props.message.content,
      user: 'all'
    })

    if (result?.ok !== 0) {
      messageApi.success('已发送到飞书')
    } else {
      messageApi.error(`发送失败: ${result?.msg || result?.message || '未知错误'}`)
    }
  } catch (error) {
    console.error('发送到飞书失败:', error)
    messageApi.error('发送失败，请检查网络或联系管理员')
  }
}

// 正文 DOM 节流更新：流式期间最多约每 250ms 整段重渲染一次（降频长消息的解析/重建开销），
// 流式结束后立即 flush 最终内容，保证结尾完整渲染。
const DISPLAY_THROTTLE_MS = 250
const displayHtml = ref('')
let displayUpdateTimer = null
let lastDisplayUpdateTime = 0

const flushDisplayHtml = () => {
  if (displayUpdateTimer) {
    clearTimeout(displayUpdateTimer)
    displayUpdateTimer = null
  }
  displayHtml.value = processedContent.value
  lastDisplayUpdateTime = Date.now()
}

const scheduleDisplayHtml = () => {
  const remaining = DISPLAY_THROTTLE_MS - (Date.now() - lastDisplayUpdateTime)
  if (remaining <= 0) {
    flushDisplayHtml()
    return
  }
  if (displayUpdateTimer) {
    return
  }
  displayUpdateTimer = setTimeout(() => {
    displayUpdateTimer = null
    displayHtml.value = processedContent.value
    lastDisplayUpdateTime = Date.now()
  }, remaining)
}

watch(
  () => [props.message.content, props.loading],
  ([, newLoading]) => {
    if (newLoading) {
      scheduleDisplayHtml()
    } else {
      // 流式结束：跳过节流，立即渲染最终内容
      flushDisplayHtml()
    }
  },
  { immediate: true }
)

let contentChangeTimer = null
const stopWatchingContent = watch(() => [props.message.content, props.loading], ([newContent, newLoading], [oldContent, oldLoading] = []) => {
  if (!newContent || (newContent === oldContent && newLoading === oldLoading)) {
    return
  }

  if (contentChangeTimer) {
    clearTimeout(contentChangeTimer)
  }

  contentChangeTimer = setTimeout(() => {
    // 内容增强只在消息进入视口后执行；屏幕外消息跳过，避免长会话全量做重活。
    // 流式阶段同样可见即高亮代码/加载图片；mermaid 源码未完整，待流式结束再渲染。
    runContentWork()
  }, newLoading ? 220 : 80)
}, { flush: 'post' })

watch(() => props.loading, (newVal, oldVal) => {
  if (oldVal === true && newVal === false && chatStore.autoSendToFeishu && !isUser.value) {
    sendToFeishu()
  }
})

// 消息内容增强（代码高亮/图片加载/表格标记/mermaid）仅在消息进入视口后执行。
// 屏幕外消息由 content-visibility 跳过绘制，这里再省掉它们的 JS 重活，
// 让长会话挂载不再随消息数线性堆积主线程任务。
let visibilityObserver = null
const messageVisible = ref(false)

const runContentWork = () => {
  if (!contentRef.value || !messageVisible.value) return
  requestAnimationFrame(() => {
    handleImageLoading()
    markTableOverflow()
    highlightCode()
    if (!props.loading) {
      renderMermaid()
    }
  })
}

const observeMessageVisibility = () => {
  const el = contentRef.value
  if (!el) return

  if (typeof IntersectionObserver === 'undefined') {
    messageVisible.value = true
    runContentWork()
    return
  }

  visibilityObserver = new IntersectionObserver(entries => {
    if (!entries.some(entry => entry.isIntersecting)) return
    messageVisible.value = true
    runContentWork()
    visibilityObserver?.disconnect()
    visibilityObserver = null
  }, { rootMargin: '200px 0px' })
  visibilityObserver.observe(el)
}

onMounted(() => {
  observeMessageVisibility()
})

onBeforeUnmount(() => {
  stopWatchingContent()

  if (visibilityObserver) {
    visibilityObserver.disconnect()
    visibilityObserver = null
  }

  if (mermaidPreviewOpen.value) {
    closeMermaidPreview()
  }

  if (contentChangeTimer) {
    clearTimeout(contentChangeTimer)
    contentChangeTimer = null
  }

  if (displayUpdateTimer) {
    clearTimeout(displayUpdateTimer)
    displayUpdateTimer = null
  }

  resetContentState()
  copied.value = false
})

const handleRetry = () => {
  emit('retry', props.message)
}

// 会话已推进/已删除导致重试无意义：按状态切换对应动作
const handleErrorAction = () => {
  if (props.message?.sessionDeleted) {
    emit('session-deleted', props.message)
    return
  }
  if (props.message?.retryStale) {
    emit('refresh-session', props.message)
    return
  }
  handleRetry()
}

// 用户消息就地编辑：编辑最新一条用户消息后，按"重试/重新生成"逻辑重发
const editTextareaRef = ref(null)
const isEditing = ref(false)
const editContent = ref('')

const startEdit = () => {
  if (props.loading) return
  editContent.value = props.message?.content || ''
  isEditing.value = true
  nextTick(() => {
    const el = editTextareaRef.value
    if (el) {
      el.focus()
      el.setSelectionRange(el.value.length, el.value.length)
    }
  })
}

const cancelEdit = () => {
  isEditing.value = false
  editContent.value = ''
}

const saveEdit = () => {
  const trimmed = editContent.value.trim()
  if (!trimmed) return
  if (trimmed === String(props.message?.content || '').trim()) {
    // 内容未变化，直接退出编辑
    isEditing.value = false
    editContent.value = ''
    return
  }
  isEditing.value = false
  editContent.value = ''
  emit('edit', props.message, trimmed)
}
</script>

<style scoped lang="scss">
.ai-message {
  display: flex;
  gap: 14px;
  margin-bottom: var(--message-spacing, 18px);
  padding: 0;
  max-width: var(--message-max-width, 100%);
  width: 100%;
  box-sizing: border-box;
  overflow-wrap: break-word;
  contain: layout style;
  will-change: auto;
  position: relative;
  z-index: 1;
  font-family: var(--app-font-family, system-ui);
  font-size: var(--app-font-size, 14px);

  // 在大屏幕上为整个消息容器添加最大宽度限制
  @media (min-width: 1200px) {
    max-width: 90%;
  }

  @media (min-width: 1600px) {
    max-width: 85%;
  }

  // 用户消息样式 - 右对齐布局
  &.user-message {
    flex-direction: row-reverse; // 用户消息右对齐
    margin-left: auto;
    margin-right: 0;
    max-width: 85%; // 限制用户消息区域宽度
    min-width: 200px; // 确保用户消息有最小宽度
    z-index: 2; // 提高用户消息的层级，防止被其他元素覆盖

    &+.assistant-message {
      margin-top: 24px;
    }

    .message-content {
      align-items: flex-end; // 右对齐
      max-width: 100%;

      .message-body {
        display: flex;
        justify-content: flex-end; // 右对齐
        align-items: center; // 垂直居中

        .user-message-content {
          display: flex;
          flex-direction: column;
          align-items: flex-end;
          min-width: 0;
        }

        .user-text {
          background: var(--chat-user-bubble-bg, linear-gradient(180deg, #e8f4ff, #dff0ff));
          border: 1px solid var(--chat-user-bubble-border, rgba(96, 165, 250, 0.55));
          color: var(--chat-user-bubble-text, #153e75);
          padding: var(--message-padding, 12px 16px);
          border-radius: var(--message-border-radius, 14px);
          font-size: var(--app-font-size, 15px);
          line-height: 1.62;
          box-shadow: 0 4px 12px var(--chat-user-bubble-shadow, rgba(30, 64, 175, 0.08)), 0 1px 2px var(--chat-user-bubble-shadow, rgba(30, 64, 175, 0.08));
          white-space: pre-wrap;
          font-weight: 400;
          position: relative;
          display: inline-block;
          width: fit-content;
          max-width: 720px;
          min-width: 60px;
          font-family: var(--app-font-family, system-ui);
          transition: all 0.2s ease;

          &:hover {
            background: var(--chat-user-bubble-bg-hover, linear-gradient(180deg, #dff0ff, #d4eaff));
            border-color: var(--chat-user-bubble-border-hover, rgba(59, 130, 246, 0.7));
            box-shadow: 0 6px 16px rgba(30, 64, 175, 0.11), 0 1px 2px rgba(30, 64, 175, 0.1);
            transform: translateY(-1px);
          }
        }

        .user-message-time {
          margin-top: 4px;
          padding-right: 4px;
          color: var(--chat-text-muted, #94a3b8);
          font-size: 11px;
          line-height: 1.2;
          font-variant-numeric: tabular-nums;
        }

        .user-edit-box {
          display: flex;
          flex-direction: column;
          align-items: flex-end;
          width: 100%;
          max-width: 720px;

          .user-edit-textarea {
            width: 100%;
            min-height: 80px;
            resize: vertical;
            background: var(--chat-surface, #fff);
            border: 1px solid var(--chat-accent, rgba(59, 130, 246, 0.6));
            border-radius: var(--message-border-radius, 14px);
            padding: 12px 16px;
            font-size: var(--app-font-size, 15px);
            line-height: 1.62;
            color: var(--chat-text, #153e75);
            font-family: var(--app-font-family, system-ui);
            box-sizing: border-box;
            outline: none;
            box-shadow: 0 4px 12px rgba(30, 64, 175, 0.08);
            white-space: pre-wrap;

            &:focus {
              border-color: var(--chat-accent, #3b82f6);
              box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
              box-shadow: 0 0 0 3px color-mix(in srgb, var(--chat-accent, #3b82f6) 15%, transparent);
            }
          }

          .user-edit-actions {
            display: flex;
            gap: 8px;
            margin-top: 8px;

            .edit-btn {
              border: 1px solid rgba(203, 213, 225, 0.9);
              background: var(--chat-surface, #fff);
              border-radius: 999px;
              padding: 5px 14px;
              font-size: 12px;
              cursor: pointer;
              color: var(--chat-text, #475569);
              transition: all 0.15s ease;

              &:hover {
                border-color: #93c5fd;
                color: var(--chat-accent, #2563eb);
              }

              &.edit-save {
                background: var(--chat-accent, #3b82f6);
                border-color: var(--chat-accent, #3b82f6);
                color: #fff;

                &:hover {
                  background: var(--chat-accent-hover, #2563eb);
                }

                &:disabled {
                  opacity: 0.5;
                  cursor: not-allowed;
                }
              }
            }
          }
        }
      }

      .message-actions {
        justify-content: flex-end;
        margin-top: 8px;
        margin-right: 4px;
      }
    }

    // 用户头像样式调整
    .message-avatar {
      /* .user-avatar 样式已合并到下方的全局 .avatar-container 定义中 */
    }
  }

  .message-retry-indicator {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 6px;
    margin-right: 12px;

    .retry-btn {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      background: none;
      border: none;
      color: var(--chat-text-muted, #6b7280);
      cursor: pointer;
      padding: 4px 6px;
      border-radius: 6px;

      &:hover {
        background: rgba(107, 114, 128, 0.08);
        color: var(--chat-text, #111827);
      }
    }

    .retry-icon {
      width: 16px;
      height: 16px;
    }

    .retry-text {
      font-size: 12px;
    }
  }


  // AI消息样式 - 左对齐布局
  &.assistant-message {
    max-width: 90%; // 限制AI消息区域宽度

    &+.user-message {
      margin-top: 24px;
    }

    .message-content {
      .message-info {
        display: flex;
        align-items: center;
        gap: 12px;
        margin-bottom: 12px;
      }

      .message-header {
        display: flex;
        align-items: center;
        gap: 8px;

        .sender-name {
          font-size: 15px;
          font-weight: 600;
          color: var(--chat-text, #1f2937);
          letter-spacing: 0.3px;
        }

        .message-time {
          font-size: 12px;
          color: var(--chat-text-muted, #9ca3af);
          font-weight: 500;
        }

        .chat-model {
          font-size: 11px;
          color: var(--chat-text-muted, #94a3b8);
          font-style: italic;
          font-weight: 400;
          padding: 1px 8px 2px;
          border-radius: 10px;
          background: rgba(148, 163, 184, 0.08);
          cursor: default;
          transition: all 0.2s ease;

          &:hover {
            color: var(--chat-text-muted, #64748b);
            background: rgba(148, 163, 184, 0.15);
          }
        }

        .message-cancelled-badge {
          padding: 1px 8px 2px;
          border-radius: 10px;
          background: color-mix(in srgb, var(--chat-danger, #dc2626) 10%, transparent);
          color: var(--chat-danger-text, #991b1b);
          font-size: 11px;
          font-weight: 500;
          cursor: default;
          white-space: nowrap;
        }

        .message-elapsed-time {
          padding: 1px 7px 2px;
          border-radius: 999px;
          background: color-mix(in srgb, var(--chat-accent, #3b82f6) 8%, transparent);
          color: var(--chat-text-muted, #64748b);
          font-size: 11px;
          font-variant-numeric: tabular-nums;
          white-space: nowrap;
        }
      }

      .message-body {
        margin-left: 52px;
        /* 40px avatar + 12px gap */

        .ai-text {
          background: var(--chat-ai-bubble-bg, linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(250, 252, 255, 0.96)));
          border: none;
          padding: var(--message-padding, 16px 18px);
          border-radius: var(--message-border-radius, 14px);
          font-size: var(--app-font-size, 15px);
          line-height: 1.66;
          color: var(--chat-ai-bubble-text, #26384b);
          position: relative;
          box-shadow: 0 8px 22px var(--chat-ai-bubble-shadow, rgba(15, 23, 42, 0.08)), 0 1px 2px var(--chat-ai-bubble-shadow, rgba(15, 23, 42, 0.05)), 0 0 0 1px var(--chat-ai-bubble-border, transparent);
          font-family: var(--app-font-family, system-ui);
          max-width: 820px;
          width: min(100%, 820px);
          transition: none;

          &.collapsed {
            max-height: 220px;
            overflow: hidden;
            mask-image: linear-gradient(to bottom, #000 72%, transparent 100%);
            -webkit-mask-image: linear-gradient(to bottom, #000 72%, transparent 100%);
          }

          &.collapsed::before {
            content: 'AI 消息已折叠';
            position: absolute;
            top: 12px;
            right: 14px;
            z-index: 2;
            padding: 4px 8px;
            border-radius: 999px;
            background: rgba(255, 255, 255, 0.92);
            border: 1px solid rgba(191, 219, 254, 0.95);
            color: var(--chat-accent, #2563eb);
            font-size: 11px;
            font-weight: 600;
            letter-spacing: 0.02em;
          }

          &.streaming {
            border-color: var(--chat-accent, rgba(59, 130, 246, 0.8));
            box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1), 0 4px 12px rgba(0, 0, 0, 0.06);
            box-shadow: 0 0 0 3px color-mix(in srgb, var(--chat-accent, #3b82f6) 12%, transparent), 0 4px 12px rgba(0, 0, 0, 0.06);

            &::after {
              content: '';
              display: inline-block;
              width: 2px;
              height: 15px;
              background: var(--chat-accent, #3b82f6);
              margin-left: 4px;
              animation: blink 1s infinite;
              border-radius: 1px;
              vertical-align: text-bottom;
            }
          }
        }
      }
    }

    // AI头像样式调整
    /* Styles are now handled globally or within .message-info */
  }

  .message-avatar {
    flex-shrink: 0;

    .avatar-container {
      width: 44px;
      height: 44px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
      transition: all 0.3s ease;

      &:hover {
        transform: scale(1.05);
        box-shadow: 0 6px 20px rgba(0, 0, 0, 0.18);
      }

      &.user-avatar {
        position: relative;
        overflow: hidden;
        background: linear-gradient(135deg, #60a5fa 0%, #3b82f6 100%);

        &::before {
          content: '';
          position: absolute;
          top: -50%;
          left: -50%;
          width: 200%;
          height: 200%;
          background: conic-gradient(transparent, transparent, #7dd3fc, #a5f3fc, #7dd3fc, transparent, transparent);
          animation: rotate 4s linear infinite;
        }

        .avatar-icon {
          width: 26px;
          height: 26px;
          color: white;
          z-index: 1;
          position: relative;
        }

        .avatar-image {
          width: 100%;
          height: 100%;
          object-fit: cover;
          position: relative;
          z-index: 2;
          border-radius: 50%;
        }

        .avatar-fallback-text {
          color: white;
          font-weight: 600;
          font-size: 18px;
          position: relative;
          z-index: 2;
        }
      }

      &.ai-avatar {
        background: transparent;
        border: none;
        box-shadow: none;

        .ai-avatar-content {
          .ai-logo {
            font-size: 15px;
            font-weight: 700;
            color: var(--chat-text, #475569);
          }
        }

        .avatar-image {
          width: 100%;
          height: 100%;
          object-fit: cover;
          position: relative;
          z-index: 1;
          border-radius: 50%;
          background: transparent;
        }

        .avatar-fallback-text {
          width: 100%;
          height: 100%;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
          color: white;
          font-weight: 600;
          font-size: 18px;
        }
      }
    }
  }

  // 消息内容区域
  .message-content {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-width: 0;
  }

  // 状态提示：错误 / 生成中 / 空内容
  .message-status-banner {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    padding: 12px 14px;
    border-radius: 12px;
    font-size: 13px;
    line-height: 1.5;

    &.error {
      background: linear-gradient(180deg, #fef2f2, #fee2e2);
      background: var(--chat-danger-tint, linear-gradient(180deg, #fef2f2, #fee2e2));
      border: 1px solid rgba(239, 68, 68, 0.4);
      color: var(--chat-danger-text, #991b1b);
    }

    .status-icon {
      width: 18px;
      height: 18px;
      flex-shrink: 0;
      margin-top: 1px;
      color: var(--chat-danger, #dc2626);
    }

    .status-body {
      flex: 1;
      min-width: 0;
    }

    .status-title {
      font-weight: 700;
    }

    .status-desc {
      margin-top: 2px;
      color: var(--chat-danger-text, #b91c1c);
      word-break: break-word;
    }

    .status-retry {
      flex-shrink: 0;
      display: inline-flex;
      align-items: center;
      gap: 4px;
      background: var(--chat-danger, #dc2626);
      color: #fff;
      border: none;
      padding: 5px 12px;
      border-radius: 999px;
      cursor: pointer;
      font-size: 12px;
      transition: all 0.15s ease;

      &:hover {
        filter: brightness(1.12);
      }

      &:active {
        transform: translateY(1px);
      }
    }
  }

  .ai-generating {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 10px 14px;
    border-radius: 12px;
    background: rgba(59, 130, 246, 0.06);
    background: color-mix(in srgb, var(--chat-accent, #3b82f6) 7%, transparent);
    border: 1px dashed rgba(59, 130, 246, 0.35);
    border: 1px dashed color-mix(in srgb, var(--chat-accent, #3b82f6) 32%, transparent);
    color: var(--chat-text, #475569);
    font-size: 13px;

    .gen-dot {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: var(--chat-accent, #3b82f6);
      animation: gen-bounce 1.2s infinite ease-in-out;
    }

    .gen-dot:nth-child(2) {
      animation-delay: 0.15s;
    }

    .gen-dot:nth-child(3) {
      animation-delay: 0.3s;
    }

    .gen-text {
      font-weight: 500;
    }
  }

  @keyframes gen-bounce {

    0%,
    80%,
    100% {
      transform: translateY(0);
      opacity: 0.5;
    }

    40% {
      transform: translateY(-5px);
      opacity: 1;
    }
  }

  .ai-empty {
    padding: 10px 14px;
    color: var(--chat-text-muted, #9ca3af);
    font-size: 13px;
    font-style: italic;
  }

  .user-failed-hint {
    color: var(--chat-danger, #dc2626);
    font-style: italic;
  }

  .user-empty-hint {
    color: var(--chat-text-muted, #9ca3af);
    font-style: italic;
  }

  // 推理过程折叠块
  .reasoning-block {
    margin: 0 0 12px 0;
    border-left: 2px solid color-mix(in srgb, var(--chat-accent, #6366f1) 38%, transparent);
    background: color-mix(in srgb, var(--chat-accent, #6366f1) 3%, transparent);
    overflow: hidden;
    transition: border-color 0.2s ease, background 0.2s ease;

    &.collapsed {
      background: transparent;
    }

    &.active {
      border-left-color: var(--chat-accent, #6366f1);
    }

    .reasoning-toggle {
      display: flex;
      align-items: center;
      gap: 8px;
      width: 100%;
      min-height: 30px;
      padding: 4px 8px 4px 10px;
      border: none;
      background: transparent;
      color: var(--chat-text-muted, #64748b);
      font-size: 12px;
      font-weight: 500;
      cursor: pointer;
      text-align: left;
      transition: color 0.15s ease, background 0.15s ease;

      &:hover {
        background: color-mix(in srgb, var(--chat-accent, #6366f1) 5%, transparent);
        color: var(--chat-text, #334155);
      }

      .chev {
        width: 13px;
        height: 13px;
        flex-shrink: 0;
        transition: transform 0.18s ease;

        &.expanded {
          transform: rotate(90deg);
        }
      }

      .reasoning-label {
        color: inherit;
      }

      .reasoning-meta {
        margin-left: auto;
        color: color-mix(in srgb, var(--chat-text-muted, #94a3b8) 85%, transparent);
        font-size: 11px;
      }

      .reasoning-mark {
        display: inline-flex;
        align-items: center;
        gap: 2px;
        width: 14px;

        i {
          width: 3px;
          height: 3px;
          border-radius: 50%;
          background: var(--chat-accent, #6366f1);
          opacity: 0.55;
        }
      }
    }

    &.active .reasoning-mark i {
      animation: gen-bounce 1.2s infinite ease-in-out;
    }

    &.active .reasoning-mark i:nth-child(2) {
      animation-delay: 0.15s;
    }

    &.active .reasoning-mark i:nth-child(3) {
      animation-delay: 0.3s;
    }

    .reasoning-content {
      max-height: 320px;
      overflow: auto;
      padding: 5px 14px 10px 14px;
      border-top: 1px solid color-mix(in srgb, var(--chat-text-muted, #94a3b8) 12%, transparent);
      color: var(--chat-text-muted, #64748b);
      font-size: 12px;
      line-height: 1.65;

      :deep(.reasoning-plain) {
        white-space: pre-wrap;
        word-break: break-word;
        font-family: inherit;
      }
    }
  }

  // 工具执行步骤
  .tool-steps {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin: 0 0 12px 0;
    padding: 8px 10px;
    border: 1px solid color-mix(in srgb, var(--chat-text-muted, #94a3b8) 16%, transparent);
    border-radius: 8px;
    background: color-mix(in srgb, var(--chat-surface, #ffffff) 60%, transparent);
  }

  .tool-step {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 6px;
    padding: 4px 6px;
    border-radius: 6px;
    font-size: 12px;

    &.tool-step-running {
      background: color-mix(in srgb, var(--chat-accent, #6366f1) 6%, transparent);
    }

    &.tool-step-failed {
      background: color-mix(in srgb, #ef4444 7%, transparent);
    }

    .tool-step-icon {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 18px;
      font-size: 12px;

      .tool-spinner {
        width: 10px;
        height: 10px;
        border-radius: 50%;
        border: 2px solid color-mix(in srgb, var(--chat-accent, #6366f1) 30%, transparent);
        border-top-color: var(--chat-accent, #6366f1);
        animation: qs-tool-spin 0.8s linear infinite;
      }
    }

    .tool-step-label {
      flex-shrink: 0;
      color: var(--chat-text-muted, #64748b);
      font-weight: 500;
    }

    .tool-step-name {
      flex: 1 1 auto;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      color: var(--chat-text, #334155);
      font-weight: 600;
    }

    .tool-step-toggle {
      flex-shrink: 0;
      padding: 1px 6px;
      border: none;
      border-radius: 4px;
      background: transparent;
      color: var(--chat-accent, #6366f1);
      font-size: 11px;
      cursor: pointer;

      &:hover {
        background: color-mix(in srgb, var(--chat-accent, #6366f1) 8%, transparent);
      }
    }

    .tool-step-detail {
      width: 100%;
      margin-top: 4px;
      padding-top: 6px;
      border-top: 1px dashed color-mix(in srgb, var(--chat-text-muted, #94a3b8) 22%, transparent);

      .tool-step-field {
        display: flex;
        align-items: flex-start;
        gap: 8px;
        margin-top: 4px;

        .field-label {
          flex-shrink: 0;
          color: var(--chat-text-muted, #64748b);
          font-size: 11px;
          font-weight: 600;
        }

        pre {
          flex: 1 1 auto;
          margin: 0;
          max-height: 160px;
          overflow: auto;
          white-space: pre-wrap;
          word-break: break-word;
          color: var(--chat-text, #475569);
          font-size: 11px;
          line-height: 1.5;
        }

        &.error {
          .field-label,
          pre {
            color: #ef4444;
          }
        }
      }
    }
  }

  @keyframes qs-tool-spin {
    to {
      transform: rotate(360deg);
    }
  }

  // 消息操作栏
  .message-token-usage {
    display: inline-flex;
    align-items: center;
    gap: 10px;
    margin-left: 6px;
    padding: 5px 9px;
    border: 1px solid color-mix(in srgb, var(--chat-text-muted, #94a3b8) 18%, transparent);
    border-radius: 7px;
    background: color-mix(in srgb, var(--chat-surface, #ffffff) 72%, transparent);
    font-size: 10px;
    line-height: 1;
    color: var(--chat-text-muted, #64748b);
    white-space: nowrap;

    .token-usage-title {
      color: var(--chat-text-muted, #64748b);
      font-weight: 600;
      letter-spacing: 0.04em;
    }

    .token-metric {
      display: inline-flex;
      align-items: baseline;
      gap: 4px;

      span {
        color: color-mix(in srgb, var(--chat-text-muted, #94a3b8) 88%, transparent);
      }

      b {
        color: var(--chat-text, #475569);
        font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
        font-size: 11px;
        font-variant-numeric: tabular-nums;
        font-weight: 600;
      }
    }

    .token-total {
      display: inline-flex;
      align-items: baseline;
      gap: 4px;
      padding-left: 10px;
      border-left: 1px solid color-mix(in srgb, var(--chat-text-muted, #94a3b8) 22%, transparent);

      b {
        color: var(--chat-accent, #6366f1);
        font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
        font-size: 11px;
        font-variant-numeric: tabular-nums;
      }

      span {
        color: color-mix(in srgb, var(--chat-text-muted, #94a3b8) 88%, transparent);
      }
    }

    @media (max-width: 560px) {
      margin-left: 0;
      gap: 7px;
      white-space: normal;
    }
  }

  .message-actions {
    display: flex;
    align-items: center;
    margin-top: 12px;
    opacity: 0;
    transition: opacity 0.2s ease;

    .action-group {
      display: flex;
      align-items: center;
      gap: 8px;
      position: relative;
      left: 52px;
      /* Match the margin of message-body */
    }

    .action-btn {
      display: flex;
      align-items: center;
      gap: 6px;
      background: rgba(255, 255, 255, 0.6);
      background: var(--chat-surface, rgba(255, 255, 255, 0.6));
      border: 1px solid rgba(229, 231, 235, 0.6);
      border: 1px solid var(--chat-ai-bubble-border, rgba(229, 231, 235, 0.6));
      padding: 6px 12px;
      border-radius: 16px;
      font-size: 12px;
      color: var(--chat-text-muted, #6b7280);
      cursor: pointer;
      transition: all 0.2s ease;
      /* 移除模糊效果，提高性能 */

      &:hover {
        background: rgba(249, 250, 251, 0.8);
        background: var(--chat-panel-hover, rgba(249, 250, 251, 0.8));
        border-color: rgba(209, 213, 219, 0.8);
        border-color: var(--chat-ai-bubble-border, rgba(209, 213, 219, 0.8));
        color: var(--chat-text, #374151);
        /* 原: transform: translateY(-1px); */
      }

      .btn-icon {
        width: 14px;
        height: 14px;

        &.success {
          color: #10b981;
        }
      }

      .btn-text {
        font-weight: 500;
      }

      &.copy-btn:hover {
        border-color: var(--chat-accent, #3b82f6);
        color: var(--chat-accent, #3b82f6);
      }

      &.share-btn:hover:not(:disabled) {
        border-color: #8b5cf6;
        color: #7c3aed;
      }

      &.share-btn:disabled {
        opacity: 0.6;
        cursor: not-allowed;
      }

      &.tts-btn:hover {
        border-color: #06b6d4;
        color: #0891b2;
      }

      &.download-btn:hover:not(:disabled) {
        border-color: #f59e0b;
        color: #d97706;
      }

      &.download-btn:disabled {
        opacity: 0.6;
        cursor: not-allowed;
      }

      &.tts-btn.playing {
        background: rgba(6, 182, 212, 0.12);
        border-color: #06b6d4;
        color: #0e7490;

        .tts-icon {
          animation: tts-bounce 1s infinite ease-in-out;
        }
      }

      &.regenerate-btn:hover {
        border-color: #8b5cf6;
        color: #8b5cf6;
      }

      &.send-btn:hover {
        border-color: #10b981;
        color: #10b981;
      }
    }
  }

  &:hover .message-actions {
    opacity: 1;
  }
}

// Markdown内容样式
.ai-text {
  overflow-wrap: anywhere;
  word-break: break-word;
  text-rendering: optimizeLegibility;
  -webkit-font-smoothing: antialiased;
  font-feature-settings: 'liga' 1, 'kern' 1;

  // 「流式结束后解析 MD」开启时，流式阶段正文为纯文本
  :deep(.md-stream-plain) {
    white-space: pre-wrap;
    word-break: break-word;
  }

  :deep(> :first-child) {
    margin-top: 0 !important;
  }

  :deep(> :last-child) {
    margin-bottom: 0 !important;
  }

  :deep(h1) {
    margin-top: 16px;
    margin-bottom: 8px;
    font-weight: 700;
    font-size: 20px;
    line-height: 1.32;
    color: var(--chat-markdown-heading, #0f172a);
    letter-spacing: 0;
  }

  :deep(h2) {
    margin-top: 14px;
    margin-bottom: 8px;
    font-weight: 700;
    font-size: 17px;
    line-height: 1.38;
    color: var(--chat-markdown-heading, #111827);
    border-bottom: 1px solid var(--chat-markdown-divider, rgba(148, 163, 184, 0.24));
    padding-bottom: 4px;
    letter-spacing: 0;
  }

  :deep(h3) {
    margin-top: 12px;
    margin-bottom: 6px;
    font-weight: 700;
    font-size: 15px;
    line-height: 1.45;
    color: var(--chat-markdown-heading, #1f3b57);
  }

  :deep(h4) {
    margin-top: 12px;
    margin-bottom: 6px;
    font-weight: 600;
    font-size: 15px;
    line-height: 1.5;
    color: var(--chat-markdown-heading, #1f2937);
  }

  :deep(h5),
  :deep(h6) {
    margin-top: 10px;
    margin-bottom: 4px;
    font-weight: 600;
    font-size: 14px;
    line-height: 1.5;
    color: var(--chat-markdown-heading, #374151);
  }

  :deep(p) {
    margin: 0 0 12px;
    line-height: 1.72;
    font-size: 15px;
    color: var(--chat-markdown-text, #314155);
    letter-spacing: 0;
  }

  :deep(p + p) {
    margin-top: 0;
  }

  :deep(p + ul),
  :deep(p + ol),
  :deep(p + blockquote),
  :deep(p + .table-wrapper),
  :deep(p + pre) {
    margin-top: 6px;
  }

  :deep(li > p) {
    margin: 2px 0;
  }

  :deep(strong) {
    font-weight: 700;
    color: var(--chat-markdown-heading, #0f172a);
  }

  :deep(em) {
    color: var(--chat-markdown-text, #334155);
    font-style: italic;
  }

  /* 链接样式 */
  :deep(a) {
    color: var(--chat-link, #1d4ed8);
    text-decoration: underline;
    text-decoration-color: rgba(29, 78, 216, 0.24);
    text-decoration-thickness: 1px;
    text-underline-offset: 0.2em;
    word-break: break-word;
    transition: color 0.18s ease, text-decoration-color 0.18s ease;

    &:hover {
      color: var(--chat-link, #1e40af);
      text-decoration-color: rgba(30, 64, 175, 0.55);
    }

    &[target="_blank"]::after {
      content: '↗';
      font-size: 0.7em;
      margin-left: 2px;
      vertical-align: super;
      opacity: 0.6;
    }
  }

  :deep(ul) {
    margin: 8px 0;
    padding-left: 1.3em;
    list-style: disc;
    font-size: 15px;
  }

  :deep(ol) {
    margin: 8px 0;
    padding-left: 1.4em;
    list-style: decimal;
  }

  :deep(ul ul),
  :deep(ul ol),
  :deep(ol ul),
  :deep(ol ol) {
    margin: 6px 0 0;
  }

  :deep(ul ul) {
    list-style-type: circle;
  }

  :deep(ul ul ul) {
    list-style-type: square;
  }

  :deep(ol ol) {
    list-style-type: lower-alpha;
  }

  :deep(ol ol ol) {
    list-style-type: lower-roman;
  }

  :deep(li) {
    margin: 3px 0;
    line-height: 1.58;
    color: var(--chat-markdown-text, #314155);
    padding-left: 0.1em;
  }

  :deep(li + li) {
    margin-top: 4px;
  }

  :deep(ul li::marker),
  :deep(ol li::marker) {
    color: var(--chat-markdown-marker, #36536b);
    font-weight: 700;
  }

  :deep(ol li::marker) {
    font-variant-numeric: tabular-nums;
  }

  :deep(li:has(input[type="checkbox"])) {
    list-style: none;
    margin-left: -1.5em;
    padding-left: 0;
  }

  :deep(> h1 + p),
  :deep(> h2 + p),
  :deep(> h3 + p),
  :deep(> h4 + p) {
    margin-top: 4px;
  }

  :deep(> h1 + ul),
  :deep(> h2 + ul),
  :deep(> h3 + ul),
  :deep(> h4 + ul),
  :deep(> h1 + ol),
  :deep(> h2 + ol),
  :deep(> h3 + ol),
  :deep(> h4 + ol) {
    margin-top: 4px;
  }

  :deep(code) {
    background: var(--chat-inline-code-bg, #f8fafc) !important;
    padding: 1px 6px;
    border-radius: 5px;
    font-size: 0.86em;
    font-family: var(--code-font-family, 'JetBrains Mono', 'Fira Code', monospace);
    color: var(--chat-inline-code-text, #b42318);
    font-weight: 600;
    line-height: 1.4;
    display: inline;
    border: 1px solid var(--chat-inline-code-border, rgba(203, 213, 225, 0.9));
  }

  :deep(.code-block-wrapper) {
    position: relative;
    width: 100%;
    max-width: 100%;
    box-sizing: border-box;
    margin: 10px 0;
    border: 1px solid rgba(148, 163, 184, 0.24);
    border-radius: 6px;
    overflow: hidden;
    contain: layout style;
    background: var(--code-block-background, #1e293b);
    box-shadow: 0 1px 4px rgba(15, 23, 42, 0.08);

    .code-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      padding: 6px 14px;
      background: rgba(148, 163, 184, 0.06);
      border-bottom: 1px solid rgba(148, 163, 184, 0.1);

      .code-lang {
        font-size: 11px;
        color: #7d8ba1;
        font-weight: 500;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .code-copy-btn {
        background: transparent;
        border: none;
        border-radius: 6px;
        padding: 3px 8px;
        color: #64748b;
        cursor: pointer;
        transition: all 0.15s ease;
        display: flex;
        align-items: center;
        gap: 4px;
        font-size: 11px;
        flex-shrink: 0;
        opacity: 0.6;

        &:hover {
          background: rgba(255, 255, 255, 0.08);
          color: #cbd5e1;
          opacity: 1;
        }

        &:active {
          transform: translateY(1px) scale(0.98);
        }

        .copy-icon {
          display: block;

          &.success {
            color: #10b981;
          }
        }
      }
    }

    &:hover .code-copy-btn {
      opacity: 1;
    }

    pre {
      margin: 0;
      border-radius: var(--chat-radius, 0);

      code {
        padding-top: 14px;
        padding-bottom: 14px;
      }
    }
  }

  /* 流式输出时的代码块样式 */
  :deep(.streaming-code) {
    .code-header {
      background: rgba(59, 130, 246, 0.08);
      background: color-mix(in srgb, var(--chat-accent, #3b82f6) 10%, transparent);
      border-bottom-color: rgba(59, 130, 246, 0.25);
      border-bottom-color: color-mix(in srgb, var(--chat-accent, #3b82f6) 25%, transparent);
    }
  }

  :deep(pre) {
    width: 100%;
    background: var(--code-block-background, #1e293b) !important;
    padding: var(--code-block-padding, 16px 18px);
    border-radius: var(--code-block-border-radius, 6px);
    overflow-x: auto;
    margin: 0;
    max-width: 100%;
    box-sizing: border-box;

    &:hover {
      background: var(--code-block-background, #1e293b) !important;
    }

    code {
      background: transparent !important;
      padding: 0;
      color: var(--chat-code-text, #e2e8f0);
      font-size: var(--code-font-size, 13px);
      font-family: var(--code-font-family, 'JetBrains Mono', 'Fira Code', monospace);
      line-height: 1.58;
      white-space: pre;
      word-break: normal;
      display: block;
      tab-size: 2;

      &:hover {
        background: transparent !important;
      }
    }
  }

  /* 未完成标记样式（流式输出时） */
  :deep(.incomplete) {
    background: rgba(59, 130, 246, 0.1);
    background: color-mix(in srgb, var(--chat-accent, #3b82f6) 10%, transparent);
    border-radius: 2px;
  }

  :deep(.incomplete-link) {
    color: var(--chat-text-muted, #6b7280);
    font-family: var(--code-font-family, monospace);
    font-size: 0.9em;
  }

  :deep(blockquote) {
    border-left: 2px solid var(--chat-blockquote-border, #54708a);
    padding: 9px 12px 9px 14px;
    margin: 10px 0;
    color: var(--chat-blockquote-text, #475569);
    font-style: normal;
    background: var(--chat-blockquote-bg, linear-gradient(to right, rgba(248, 250, 252, 0.98), rgba(255, 255, 255, 0.78)));
    border-radius: 0 10px 10px 0;
    position: relative;

    p {
      margin: 0 !important;
    }
  }

  :deep(blockquote p + p) {
    margin-top: 6px !important;
  }

  :deep(blockquote blockquote) {
    margin: 12px 0 0;
    border-left-color: var(--chat-blockquote-border, #94a3b8);
    background: var(--chat-blockquote-bg, rgba(255, 255, 255, 0.56));
    color: var(--chat-blockquote-text, #475569);
  }

  :deep(.image-wrapper) {
    margin: 12px 0;
    border-radius: 14px;
    overflow: hidden;
    min-height: 200px;
    background-color: var(--chat-image-bg, #f8fafc);
    position: relative;
    border: 1px solid var(--chat-ai-bubble-border, rgba(226, 232, 240, 0.9));
    box-shadow: 0 6px 18px rgba(15, 23, 42, 0.08);
    box-shadow: 0 6px 18px color-mix(in srgb, var(--chat-shadow-color, #0f172a) 8%, transparent);
  }

  /* 骨架屏效果 */
  :deep(.image-wrapper.loading-wrapper)::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: linear-gradient(90deg,
        #f3f4f6 25%,
        #e5e7eb 50%,
        #f3f4f6 75%);
    background-size: 200% 100%;
    animation: skeleton-loading 1.5s infinite;
    z-index: 1;
  }

  @keyframes skeleton-loading {
    0% {
      background-position: 200% 0;
    }

    100% {
      background-position: -200% 0;
    }
  }

  /* 加载文字提示 */
  :deep(.image-wrapper.loading-wrapper)::after {
    content: '图片加载中...';
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    color: var(--chat-text-muted, #6b7280);
    font-size: 14px;
    font-weight: 500;
    z-index: 2;
    padding: 8px 16px;
    background: rgba(255, 255, 255, 0.9);
    border-radius: 20px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  }

  :deep(.image-wrapper.error-wrapper) {
    background: #fef2f2;
    background: var(--chat-danger-tint, #fef2f2);
    border: 2px dashed #fecaca;

    &::before {
      content: '';
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      width: 48px;
      height: 48px;
      background: #ef4444;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1;
      box-shadow: 0 4px 12px rgba(239, 68, 68, 0.3);
    }

    &::after {
      content: '图片加载失败';
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, 30px);
      color: #ef4444;
      font-size: 14px;
      font-weight: 600;
      z-index: 2;
    }
  }

  :deep(img:not(.twemoji-img):not(.emoji-img)) {
    max-width: 100%;
    height: auto;
    border-radius: var(--chat-radius, 0);
    display: block;
    position: relative;
    z-index: 3;
  }

  /* 图片默认可见，避免 JS 漏绑导致永久透明；加载完成后做一次淡入 */
  :deep(.image-wrapper.loaded img) {
    animation: image-fade-in 0.45s ease;
  }

  @keyframes image-fade-in {
    from {
      opacity: 0;
      transform: scale(0.98);
    }

    to {
      opacity: 1;
      transform: scale(1);
    }
  }

  :deep(img.error) {
    display: none;
  }

  :deep(.table-wrapper) {
    width: 100%;
    max-width: 100%;
    box-sizing: border-box;
    margin: 10px 0;
    overflow-x: auto;
    border: 1px solid var(--chat-table-border, #e5e7eb);
    border-radius: 10px;
    background: var(--chat-table-bg, #fff);
    box-shadow: 0 4px 12px rgba(15, 23, 42, 0.05);
    box-shadow: 0 4px 12px color-mix(in srgb, var(--chat-shadow-color, #0f172a) 5%, transparent);
    position: relative;

    /* 表格超宽时（JS 标记 .table-overflow）显示提示，sticky 固定于可视区右侧，跟随横向滚动 */
    &.table-overflow::before {
      content: '左右滑动查看更多';
      position: sticky;
      right: 12px;
      float: right;
      width: fit-content;
      margin: 10px 12px 0 0;
      padding: 4px 8px;
      border-radius: 999px;
      background: var(--chat-surface, #ffffff);
      color: var(--chat-text-muted, #64748b);
      font-size: 11px;
      line-height: 1;
      box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
      z-index: 2;
      pointer-events: none;
    }
  }

  :deep(table) {
    width: 100%;
    min-width: 520px;
    border-collapse: collapse;
    font-size: 13px;
    line-height: 1.55;

    th,
    td {
      border: 1px solid var(--chat-table-border, #e5e7eb);
      padding: 9px 11px;
      transition: background-color 0.2s ease;
      vertical-align: middle;
    }

    /* 解析出的表格语义区分：表头居中，正文单元格左对齐（长文本更易读） */
    th {
      text-align: center;
    }

    td {
      text-align: left;
    }

    thead th {
      background: var(--chat-table-head, linear-gradient(to bottom, #f8fafc, #f1f5f9));
      font-weight: 600;
      color: var(--chat-table-head-text, #334155);
      font-size: 12px;
      letter-spacing: 0.02em;
      white-space: nowrap;
      position: sticky;
      top: 0;
      z-index: 1;
    }

    tbody tr {
      transition: background-color 0.18s ease;

      &:nth-child(even) {
        background-color: var(--chat-table-stripe, #fbfdff);
      }

      &:hover {
        background-color: var(--chat-table-stripe, #eef4f7);
      }
    }

    tbody td {
      color: var(--chat-markdown-text, #334155);

      code {
        background: var(--chat-inline-code-bg, #f1f5f9);
        padding: 2px 6px;
        border-radius: 4px;
        font-size: 12px;
      }
    }
  }

  /* 流程图（mermaid）容器 */
  :deep(.mermaid-block) {
    width: 100%;
    max-width: 100%;
    box-sizing: border-box;
    overflow: hidden;
    margin: 12px 0;
    border: 1px solid var(--chat-table-border, #e5e7eb);
    border-radius: 12px;
    background: var(--chat-table-bg, #fff);
    padding: 12px;
    box-shadow: 0 4px 12px rgba(15, 23, 42, 0.05);
    box-shadow: 0 4px 12px color-mix(in srgb, var(--chat-shadow-color, #0f172a) 5%, transparent);
    position: relative;

.mermaid-render {
      display: flex;
      justify-content: center;
      align-items: flex-start;
      width: 100%;
      max-width: 100%;
      max-height: min(60vh, 520px);
overflow-x: auto;
      overflow-y: auto;
      overscroll-behavior: auto;

      svg {
        display: block;
        max-width: 100%;
        height: auto;
        margin-inline: auto;
      }
    }

    &.mermaid-wide {
      .mermaid-render {
        justify-content: flex-start;

        svg {
          width: var(--mermaid-natural-width);
          max-width: none;
          margin-inline: 0;
        }
      }
    }

    .mermaid-toolbar {
      display: flex;
      flex-wrap: wrap;
      justify-content: flex-end;
      align-items: center;
      gap: 6px;
      margin-top: 8px;

      .mermaid-toggle-btn,
      .mermaid-zoom-btn {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: 4px;
        max-width: 100%;
        background: rgba(15, 23, 42, 0.06);
        border: 1px solid rgba(148, 163, 184, 0.28);
        border-radius: 999px;
        padding: 4px 10px;
        color: #64748b;
        cursor: pointer;
        font-size: 11px;
        transition: all 0.15s ease;

        &:hover {
          background: rgba(15, 23, 42, 0.12);
          color: #334155;
        }
      }
    }

    .mermaid-source {
      margin: 8px 0 0;

      &.hidden {
        display: none !important;
      }
    }

    &.mermaid-error {
      border-color: rgba(239, 68, 68, 0.4);
      background: #fef2f2;
      background: var(--chat-danger-tint, #fef2f2);
    }
  }

  /* 删除线样式 */
  :deep(del),
  :deep(s) {
    text-decoration: line-through;
    color: var(--chat-text-muted, #9ca3af);
    opacity: 0.8;
  }

  /* 任务列表样式 */
  :deep(li input[type="checkbox"]) {
    margin-right: 10px;
    accent-color: var(--chat-accent, #3b82f6);
    cursor: pointer;
    transform: scale(1.1);
    vertical-align: middle;
  }

  :deep(li:has(input[type="checkbox"])) {
    display: flex;
    align-items: flex-start;
    gap: 4px;
  }

  :deep(li:has(input[type="checkbox"]) > input[type="checkbox"]) {
    margin-top: 0.35em;
    flex-shrink: 0;
  }

  /* 分割线样式 */
  :deep(hr) {
    border: none;
    height: 1px;
    background: linear-gradient(to right, transparent, #cbd5e1, transparent);
    margin: 18px 0;
  }

  /* 标题锚点 + 复制链接 */
  :deep(.heading-anchor) {
    position: relative;
    scroll-margin-top: 80px;

    .heading-link {
      position: absolute;
      left: -22px;
      top: 50%;
      transform: translateY(-50%);
      opacity: 0;
      color: var(--chat-text-muted, #94a3b8);
      text-decoration: none;
      font-weight: 700;
      font-size: 0.8em;
      transition: opacity 0.15s ease, color 0.15s ease;

      &:hover {
        color: var(--chat-accent, #3b82f6);
      }
    }

    &:hover .heading-link {
      opacity: 1;
    }
  }

  /* 数学公式（KaTeX） */
  :deep(.math-block) {
    width: 100%;
    max-width: 100%;
    box-sizing: border-box;
    margin: 12px 0;
    padding: 10px 14px;
    overflow-x: auto;
    text-align: center;
    background: rgba(99, 102, 241, 0.04);
    background: color-mix(in srgb, var(--chat-accent, #6366f1) 4%, transparent);
    border: 1px solid rgba(99, 102, 241, 0.15);
    border: 1px solid color-mix(in srgb, var(--chat-accent, #6366f1) 15%, transparent);
    border-radius: 10px;

    .katex {
      font-size: 1.05em;
    }
  }

  :deep(.math-error) {
    color: var(--chat-danger, #dc2626);
    background: var(--chat-danger-tint, #fef2f2);
    padding: 2px 6px;
    border-radius: 4px;
    font-size: 0.9em;
  }

  /* 水平滚动条优化：代码块/表格用小档尺寸 */
  :deep(pre),
  :deep(.table-wrapper) {
    --scrollbar-size: var(--scrollbar-size-sm);
  }

  @media (max-width: 768px) {
    /* 移动端：减小头像和间距，让消息更宽 */
    gap: 8px;

    &.user-message {
      max-width: 100%;
      min-width: 0;
    }

    &.assistant-message {
      max-width: 100%;
    }

    .message-avatar .avatar-container {
      width: 32px;
      height: 32px;
    }

    .message-avatar .avatar-container .avatar-icon {
      width: 18px;
      height: 18px;
    }

    .message-content .message-body {
      margin-left: 0;
    }

    .message-content .message-body .ai-text {
      max-width: 100%;
      width: 100%;
      padding: 10px 14px;
      font-size: 14px;
    }

    .message-content .message-body .user-text {
      max-width: 100%;
      min-width: 0;
      padding: 10px 14px;
      font-size: 14px;
    }

    .message-content .message-header .sender-name {
      font-size: 13px;
    }

    :deep(h3) {
      font-size: 15px;
    }

    :deep(p),
    :deep(ul),
    :deep(ol) {
      font-size: 14px;
    }

    :deep(.code-block-wrapper) {
      margin: 12px 0;

      .code-header {
        padding: 8px 10px;
      }
    }

    :deep(pre) {
      padding: 12px;

      code {
        font-size: 12px;
      }
    }

    :deep(blockquote) {
      padding: 10px 12px 10px 14px;
    }

    :deep(.table-wrapper) {
      margin-left: -4px;
      margin-right: -4px;
    }

:deep(.mermaid-block) {
      width: 100%;
      max-width: 100%;
      overflow: hidden;

      .mermaid-render {
        max-height: min(40vh, 420px);
      }
    }

    :deep(table) {
      min-width: 460px;
      font-size: 12px;

      th,
      td {
        padding: 8px 10px;
      }
    }
  }
}

// 动画
@keyframes blink {

  0%,
  50% {
    opacity: 1;
  }

  51%,
  100% {
    opacity: 0;
  }
}

@keyframes tts-bounce {

  0%,
  100% {
    transform: scale(1);
  }

  50% {
    transform: scale(1.15);
  }
}

@keyframes slideInRight {
  from {
    opacity: 0;
    transform: translateX(20px);
  }

  to {
    opacity: 1;
    transform: translateX(0);
  }
}

/* 新增: 跑马灯旋转动画 */
@keyframes rotate {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}
</style>
<style scoped lang="scss">
:global(.mermaid-preview-backdrop) {
  position: fixed;
  inset: 0;
  z-index: 4000;
  display: grid;
  place-items: center;
  padding: 4vh 2vw;
  box-sizing: border-box;
  background: rgba(15, 23, 42, 0.68);
  backdrop-filter: blur(6px);
}

:global(.mermaid-preview-panel) {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  width: min(1400px, 96vw);
  height: min(900px, 92vh);
  overflow: hidden;
  border: 1px solid var(--chat-ai-bubble-border, rgba(148, 163, 184, 0.3));
  border-radius: 8px;
  background: var(--chat-surface, #fff);
  box-shadow: 0 24px 72px rgba(15, 23, 42, 0.32);
}

:global(.mermaid-preview-toolbar) {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  min-width: 0;
  padding: 8px 10px;
  border-bottom: 1px solid var(--chat-ai-bubble-border, rgba(148, 163, 184, 0.3));
  background: var(--chat-panel, var(--chat-surface, #f8fafc));
  color: var(--chat-text, #334155);
}

:global(.mermaid-preview-scale) {
  margin-right: auto;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--chat-text-muted, #64748b);
}

:global(.mermaid-preview-toolbar button) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 34px;
  min-height: 34px;
  padding: 6px 9px;
  border: 1px solid var(--chat-ai-bubble-border, rgba(148, 163, 184, 0.42));
  border-radius: 6px;
  background: var(--chat-surface, #fff);
  color: var(--chat-text, #334155);
  cursor: pointer;
}

:global(.mermaid-preview-toolbar button:hover) {
  border-color: var(--chat-accent, #3b82f6);
  color: var(--chat-accent, #2563eb);
}

:global(.mermaid-preview-toolbar button svg) {
  width: 18px;
  height: 18px;
}

:global(.mermaid-preview-viewport) {
  position: relative;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  touch-action: none;
  cursor: grab;
  background: var(--chat-table-bg, #fff);
}

:global(.mermaid-preview-viewport:active) {
  cursor: grabbing;
}

:global(.mermaid-preview-canvas) {
  position: absolute;
  top: 50%;
  left: 50%;
  transform-origin: center;
  will-change: transform;
}

:global(.mermaid-preview-canvas svg) {
  display: block;
  width: 100%;
  max-width: none;
  height: auto;
}

@media (max-width: 768px) {
  :global(.mermaid-preview-backdrop) {
    width: 100vw;
    height: 100dvh;
    padding: 0;
  }

  :global(.mermaid-preview-panel) {
    width: 100vw;
    height: 100dvh;
    border: 0;
    border-radius: 0;
  }

  :global(.mermaid-preview-toolbar) {
    flex-wrap: wrap;
  }
}
</style>
<style>
.emoji-img,
.twemoji-img {
  display: inline;
  width: 1.2em !important;
  height: 1.2em !important;
  vertical-align: -0.25em;
  margin: 0;
  padding: 0;
  pointer-events: none;
}
</style>
