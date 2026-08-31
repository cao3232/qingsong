<template>
  <div class="input-area">
    <div v-if="attachedFiles.length > 0" class="selected-files">
      <div v-for="(file, index) in attachedFiles" :key="index" class="file-item">
        <div class="file-info">
          <DocumentIcon class="icon" />
          <span class="file-name">{{ file.name }}</span>
          <span class="file-size">({{ formatFileSize(file.size) }})</span>
        </div>
        <button class="remove-btn" @click="removeAttachedFile(index)">
          <XMarkIcon class="icon" />
        </button>
      </div>
    </div>

    <div class="input-container">
      <QuickPhrasePanel :visible="isPhrasePanelOpen" :role-id="selectedRole?.id"
        @close="emit('toggle-phrase-panel', false)" @select-phrase="handleSelectPhrase" />

      <textarea :ref="inputRef" :value="draftMessage" class="message-input" :placeholder="placeholder" rows="1"
        autocomplete="off" spellcheck="false" @input="handleInput" @keydown.enter="handleKeyDown"
        @paste="handlePaste"></textarea>

      <div class="action-buttons-row">
        <div class="left-actions">
          <div class="file-upload">
            <input ref="hiddenFileInputRef" type="file" accept="image/*,audio/*,video/*" multiple class="hidden"
              @change="handleLocalFileUpload">
            <button class="action-icon-btn" :disabled="isStreaming" title="上传文件" @click="triggerLocalFileInput">
              <PaperClipIcon class="icon" />
            </button>
          </div>

          <button class="action-icon-btn phrase-toggle-btn" :disabled="!selectedRole" title="常用短语"
            @click="togglePhrasePanel">
            <ChatBubbleLeftRightIcon class="icon" />
          </button>

          <button class="action-icon-btn lang-btn" title="切换语言" @click="toggleLanguage">
            <span class="lang-text">{{ selectedLanguage == 'EN' ? '英' : '中' }}</span>
          </button>

          <button class="action-icon-btn" :disabled="isStreaming || isExporting" title="导出对话" @click="handleExport">
            <ArrowDownTrayIcon class="icon" />
          </button>

          <button class="action-icon-btn" title="发送邮件" :disabled="isStreaming || isSendEmail" @click="handleSendEmail">
            <EnvelopeIcon class="icon" />
          </button>
        </div>

        <div class="right-actions" :class="{ 'send-feedback': isSendFeedbackActive }">
          <button v-if="!isStreaming" class="send-button" :disabled="!canSend"
            title="发送消息" @click="handleSendMessage">
            <PaperAirplaneIcon class="icon" />
          </button>
          <button v-else class="cancel-button" title="取消请求" @click="cancelStreamingRequest">
            <StopIcon class="icon" />
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import {
  ArrowDownTrayIcon,
  ChatBubbleLeftRightIcon,
  DocumentIcon,
  EnvelopeIcon,
  PaperAirplaneIcon,
  PaperClipIcon,
  StopIcon,
  XMarkIcon
} from '@heroicons/vue/24/outline'
import QuickPhrasePanel from './QuickPhrasePanel.vue'

const hiddenFileInputRef = ref(null)
const isSendFeedbackActive = ref(false)
let sendFeedbackTimer = null
let sendFeedbackFrame = null

const props = defineProps({
  attachedFiles: {
    type: Array,
    /**
 * 默认值工厂函数
 * 返回空数组作为默认值
 */
    default: () => []
  },
  adjustTextareaHeight: {
    type: Function,
    required: true
  },
  draftMessage: {
    type: String,
    default: ''
  },
  formatFileSize: {
    type: Function,
    required: true
  },
  handleExport: {
    type: Function,
    required: true
  },
  handleFileUpload: {
    type: Function,
    required: true
  },
  handlePaste: {
    type: Function,
    required: true
  },
  handleSelectPhrase: {
    type: Function,
    required: true
  },
  handleSendEmail: {
    type: Function,
    required: true
  },
  inputRef: {
    type: Object,
    default: null
  },
  isExporting: {
    type: Boolean,
    default: false
  },
  isSendEmail: {
    type: Boolean,
    default: false
  },
  isPhrasePanelOpen: {
    type: Boolean,
    default: false
  },
  isStreaming: {
    type: Boolean,
    default: false
  },
  placeholder: {
    type: String,
    default: ''
  },
  removeAttachedFile: {
    type: Function,
    required: true
  },
  selectedLanguage: {
    type: String,
    default: 'EN'
  },
  selectedRole: {
    type: Object,
    default: null
  },
  sendMessage: {
    type: Function,
    required: true
  },
  toggleLanguage: {
    type: Function,
    required: true
  },
  cancelStreamingRequest: {
    type: Function,
    required: true
  }
})

const emit = defineEmits(['toggle-phrase-panel', 'update:draftMessage', 'switch-conversation'])
const canSend = computed(() => !props.isStreaming && (props.attachedFiles.length > 0 || props.draftMessage.trim().length > 0))

const handleInput = event => {
  emit('update:draftMessage', event.target.value)
  props.adjustTextareaHeight()
}

const handleKeyDown = event => {
  if (event.altKey && (event.key === 'ArrowUp' || event.key === 'ArrowDown')) {
    event.preventDefault()
    emit('switch-conversation', event.key === 'ArrowUp' ? -1 : 1)
    return
  }

  if (!event.shiftKey && event.key === 'Enter') {
    event.preventDefault()
    handleSendMessage()
  }
}

const handleSendMessage = () => {
  if (!canSend.value) return
  props.sendMessage()
  if (sendFeedbackTimer) clearTimeout(sendFeedbackTimer)
  if (sendFeedbackFrame) cancelAnimationFrame(sendFeedbackFrame)
  isSendFeedbackActive.value = false
  sendFeedbackFrame = requestAnimationFrame(() => {
    sendFeedbackFrame = null
    isSendFeedbackActive.value = true
    sendFeedbackTimer = setTimeout(() => {
      isSendFeedbackActive.value = false
      sendFeedbackTimer = null
    }, 450)
  })
}

onBeforeUnmount(() => {
  if (sendFeedbackTimer) clearTimeout(sendFeedbackTimer)
  if (sendFeedbackFrame) cancelAnimationFrame(sendFeedbackFrame)
})

const togglePhrasePanel = () => {
  emit('toggle-phrase-panel', !props.isPhrasePanelOpen)
}

const handleLocalFileUpload = event => {
  props.handleFileUpload(event)
  event.target.value = ''
}

const triggerLocalFileInput = () => {
  hiddenFileInputRef.value?.click()
}
</script>

<style scoped lang="scss">
/* ===== RETRO OS INPUT AREA - 3D BUTTONS ===== */

.input-area {
  padding: 8px 10px 10px;
  border-top: 1px solid rgba(128, 128, 128, 0.55);
  flex-shrink: 0;
  background: var(--chat-input-bg, linear-gradient(180deg, rgba(214, 210, 200, 0.92), #c0c0c0));
  position: relative;
  z-index: 1;
  box-shadow: 0 -8px 18px rgba(15, 23, 42, 0.08);
  box-shadow: 0 -8px 18px color-mix(in srgb, var(--chat-shadow-color, #0f172a) 8%, transparent);
}

.selected-files {
  margin: 0 4px 3px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.file-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 2px 6px;
  background: var(--chat-surface, #ffffff);
  border: 1px solid;
  border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
  border-radius: var(--chat-radius, 0);
  max-width: 100%;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 4px;
  overflow: hidden;

  .icon {
    width: 12px;
    height: 12px;
    color: var(--chat-text, #000000);
    flex-shrink: 0;
  }
}

.file-name {
  font-size: 11px;
  color: var(--chat-text, #000000);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-family: var(--chat-font-family, 'MS Sans Serif', 'Segoe UI', Tahoma, sans-serif);
}

.file-size {
  font-size: 9px;
  color: var(--chat-text-muted, #808080);
  white-space: nowrap;
}

.remove-btn {
  padding: 1px;
  border: none;
  background: transparent;
  color: var(--chat-text-muted, #808080);
  cursor: pointer;
  margin-left: 4px;
  border-radius: var(--chat-radius, 0);

  &:hover {
    color: var(--chat-danger-text, #800000);
    background: var(--chat-panel, #c0c0c0);
  }

  .icon {
    width: 10px;
    height: 10px;
  }
}

.input-container {
  position: relative;
  background: var(--chat-surface, #ffffff);
  border: 1px solid rgba(64, 64, 64, 0.45);
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.1), inset 0 1px 0 rgba(255, 255, 255, 0.75);
  box-shadow: 0 6px 18px color-mix(in srgb, var(--chat-shadow-color, #0f172a) 10%, transparent), inset 0 1px 0 rgba(255, 255, 255, 0.75);
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
  overflow: visible;

  &:focus-within {
    border-color: var(--chat-accent, rgba(16, 132, 208, 0.75));
    box-shadow: 0 8px 22px rgba(15, 23, 42, 0.12), 0 0 0 2px rgba(16, 132, 208, 0.14);
    box-shadow: 0 8px 22px color-mix(in srgb, var(--chat-shadow-color, #0f172a) 12%, transparent), 0 0 0 2px rgba(16, 132, 208, 0.14);
    outline: none;
  }
}

.message-input {
  width: 100%;
  resize: none;
  border: none;
  border-radius: 10px 10px 0 0;
  padding: 12px 14px 10px;
  min-height: 52px;
  max-height: 220px;
  font-size: 13px;
  line-height: 1.55;
  background: var(--chat-surface, #ffffff);
  color: var(--chat-surface-text, #111827);
  font-family: var(--chat-font-family, 'MS Sans Serif', 'Segoe UI', Tahoma, sans-serif);
  border-bottom: 1px solid var(--chat-bevel-shadow, rgba(203, 213, 225, 0.9));
  transition: none;
  overflow-y: auto;

  &::placeholder {
    color: var(--chat-text-muted, #808080);
    font-size: 12px;
    font-weight: normal;
    opacity: 1;
    font-family: var(--chat-font-family, 'MS Sans Serif', 'Segoe UI', Tahoma, sans-serif);
  }

  &:focus {
    outline: none;
  }

  &::-webkit-scrollbar {
    width: var(--scrollbar-size-sm);
    height: var(--scrollbar-size-sm);
  }

  &::-webkit-scrollbar-track {
    background: transparent;
  }

  &::-webkit-scrollbar-thumb {
    background: var(--scrollbar-thumb);
    border-radius: 2px;
  }
}

.action-buttons-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 5px 7px;
  background: var(--chat-panel-hover, linear-gradient(180deg, #f8fafc, #e5e7eb));
  border-radius: 0 0 10px 10px;
  border-top: 1px solid var(--chat-bevel-shadow, rgba(203, 213, 225, 0.95));
}

.left-actions,
.right-actions {
  display: flex;
  align-items: center;
  gap: 3px;
}

.file-upload .hidden {
  display: none;
}

.action-icon-btn {
  height: 28px;
  width: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--chat-bevel-shadow, rgba(148, 163, 184, 0.78));
  background: var(--chat-surface, #f8fafc);
  color: var(--chat-text, #334155);
  cursor: pointer;
  border-radius: 8px;
  padding: 0;
  transition: background-color 0.16s ease, border-color 0.16s ease, color 0.16s ease, transform 0.16s ease;

  &:hover:not(:disabled) {
    background: var(--chat-panel-hover, #ffffff);
    border-color: var(--chat-accent, rgba(16, 132, 208, 0.65));
    color: var(--chat-accent, #000080);
    transform: translateY(-1px);
  }

  &:active:not(:disabled) {
    transform: translateY(0);
  }

  &:disabled {
    color: var(--chat-text-muted, #808080);
    cursor: not-allowed;
    background: var(--chat-panel, #c0c0c0);
    opacity: 0.6;
  }

  .icon {
    width: 14px;
    height: 14px;
  }

  &.lang_btn {
    width: auto;
    padding: 0 6px;
    font-size: 10px;
    font-weight: bold;
    color: var(--chat-text, #000000);
    border: 2px solid;
    border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
    font-family: var(--chat-font-family, 'MS Sans Serif', 'Segoe UI', Tahoma, sans-serif);

    &:hover:not(:disabled) {
      background: var(--chat-panel-hover, #d4d4d4);
    }
  }
}

.send-button,
.cancel-button {
  width: 34px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(15, 23, 42, 0.18);
  border-radius: 9px;
  color: var(--chat-text-on-accent, #ffffff);
  cursor: pointer;
  padding: 0;
  transition: background-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;

  .icon {
    width: 16px;
    height: 16px;
  }
}

.send-button {
  background: var(--chat-send-bg, linear-gradient(180deg, #1084d0, #000080));
  box-shadow: 0 4px 10px rgba(0, 0, 128, 0.22);
  box-shadow: 0 4px 10px color-mix(in srgb, var(--chat-accent, #000080) 22%, transparent);

  &:hover:not(:disabled) {
    background: var(--chat-send-bg-hover, linear-gradient(180deg, #1d9be8, #0000a8));
    box-shadow: 0 6px 14px rgba(0, 0, 128, 0.28);
    box-shadow: 0 6px 14px color-mix(in srgb, var(--chat-accent, #000080) 28%, transparent);
    transform: translateY(-1px);
  }

  &:active:not(:disabled) {
    transform: translateY(0);
  }

  &:disabled {
    background: var(--chat-panel, #c0c0c0);
    color: var(--chat-text-muted, #808080);
    cursor: not-allowed;
    box-shadow: var(--chat-shadow, none);
  }
}

.cancel-button {
  background: var(--chat-danger, linear-gradient(180deg, #dc2626, #991b1b));
  color: var(--chat-text-on-accent, #ffffff);

  &:hover {
    filter: brightness(1.12);
    transform: translateY(-1px);
  }

  &:active {
    transform: translateY(0);
  }
}

@media (max-width: 768px) {
  .input-area {
    padding: 4px;
  }

  .message-input {
    padding: 6px 8px 5px;
    font-size: 12px;
    min-height: 40px;
  }

  .action-buttons-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 2px 4px;
    min-height: 30px;
  }

  .left-actions {
    display: flex;
    align-items: center;
    gap: 3px;
  }
}
</style>
