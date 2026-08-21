<template name="ChatWorkspace">
  <div class="chat-main">
    <!-- 背景图片层 -->
    <div v-if="chatBackgroundType === 'image' && chatBackgroundImage" class="chat-background-image" :style="{
      backgroundImage: `url(${chatBackgroundImage})`,
      opacity: chatBackgroundImageOpacity
    }"></div>
    <!-- 背景色层 (纯色 或 跟随主题) -->
    <div v-else class="chat-background-color" :style="{ background: chatBackgroundColor }"></div>
    <ChatWorkspaceHeader :active-model-id="activeModelId" :active-source-id="activeSourceId"
      :auto-send-to-feishu="chatStore.autoSendToFeishu" :collapse-assistant-messages="collapseAssistantMessages"
      :conversation-name-input-ref="conversationNameInputRef" :current-chat-id="props.currentChatId"
      :current-chat-name="props.currentChatName" :current-messages-length="props.currentMessages.length"
      :editing-conversation-name="editingConversationName" :is-editing-conversation-name="isEditingConversationName"
      :is-role-badge-hovered="isRoleBadgeHovered" :is-role-name-copied="isRoleNameCopied" :is-streaming="isStreaming"
      :is-switching-model="isSwitchingModel"       :source-options="sourceOptions" :model-options="modelOptions"
      :role-description="roleDescription" :role-stats-info="roleStatsInfo" :role-stats-loading="roleStatsLoading"
      :selected-role-name="props.selectedRoleName"
      :temperature="temperature" @update:temperature="temperature = $event"
      :context-size="contextSize" @update:context-size="updateContextSize"
      :show-role-description-modal="showRoleDescriptionModal"
      :search-query="searchQuery"
      :search-match-count="searchResults.length"
      :search-active-index="activeResultIndex + 1"
      @update:search-query="onSearchInput($event)"
      @search-prev="searchPrev"
      @search-next="searchNext"
      @search-clear="clearSearch"
      @cancel-edit-conversation-name="cancelEditConversationName" @confirm-delete-conversation="confirmDeleteConversation"
      @copy-role-name="copyRoleName" @model-selection-change="handleModelSelectionChange"
      @open-role-description="openRoleDescription" @refresh-model-options="refreshModelOptions"
      @refresh-source-options="refreshSourceOptions" @save-conversation-name="saveConversationName"
      @start-edit-conversation-name="startEditConversationName" @toggle-assistant-messages="toggleAssistantMessages"
      @toggle-auto-send="chatStore.toggleAutoSend" @update:activeModelId="activeModelId = $event"
      @update:activeSourceId="activeSourceId = $event"
      @update:editingConversationName="editingConversationName = $event"
      @update:isRoleBadgeHovered="isRoleBadgeHovered = $event"
      @update:showRoleDescriptionModal="showRoleDescriptionModal = $event" />

    <div class="messages-area">
      <div class="messages" ref="messagesRef">
        <div v-if="virtualPaddingStart > 0" class="message-list-spacer"
          :style="{ height: `${virtualPaddingStart}px` }"></div>
        <div v-for="virtualItem in virtualItems" :key="virtualItem.key" class="virtual-message-slot"
          :data-index="virtualItem.index" :ref="virtualItemMeasureElement">
          <ChatMessage
            :message="props.currentMessages[virtualItem.index]"
            :loading="isStreaming && virtualItem.index === props.currentMessages.length - 1"
            :selected-role="selectedRole"
            :is-latest-message="virtualItem.index === props.currentMessages.length - 1"
            :is-latest-user-message="virtualItem.index === lastUserMessageIndex"
            :is-streaming="isStreaming && (virtualItem.index === props.currentMessages.length - 1 || virtualItem.index === lastUserMessageIndex)"
            :previous-message="virtualItem.index > 0 ? props.currentMessages[virtualItem.index - 1] : null"
            :collapse-assistant="collapseAssistantMessages" class="message-item"
            @retry="handleRetryMessage" @edit="handleEditMessage" />
        </div>
        <div v-if="virtualPaddingEnd > 0" class="message-list-spacer"
          :style="{ height: `${virtualPaddingEnd}px` }"></div>
      </div>

      <MessageJumpNav :messages="props.currentMessages" @jump="jumpToUserMessage" />
    </div>

    <button v-if="showScrollButton" class="scroll-to-bottom-btn" @click="scrollToBottom">
      <ChevronDoubleDownIcon class="icon" />
    </button>


    <ChatComposer :adjust-textarea-height="adjustTextareaHeight" :attached-files="attachedFiles"
      :cancel-streaming-request="cancelStreamingRequest" :draft-message="draftMessage"
      :format-file-size="formatFileSize" :handle-export="handleExport" :handle-file-upload="handleFileUpload"
      :handle-paste="handlePaste" :handle-select-phrase="handleSelectPhrase" :handle-send-email="handleSendEmail"
      :input-ref="inputRef" :is-exporting="isExporting" :is-send-email="isSendEmail"
      :is-phrase-panel-open="isPhrasePanelOpen" :is-streaming="isStreaming" :placeholder="getInputPlaceholder()"
      :remove-attached-file="removeAttachedFile" :selected-language="selectedLanguage"
      :selected-role="props.selectedRole" :send-message="sendMessage" :toggle-language="toggleLanguage"
      @toggle-phrase-panel="isPhrasePanelOpen = $event" @update:draftMessage="draftMessage = $event"
      @switch-conversation="switchConversation" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ChevronDoubleDownIcon } from '@heroicons/vue/24/outline'
import { useChatWorkspace } from '../composables/index.js'
import { useThemeStore } from '@/stores/theme'
import ChatComposer from './ChatComposer.vue'
import ChatMessage from './ChatMessage.vue'
import ChatWorkspaceHeader from './ChatWorkspaceHeader.vue'
import MessageJumpNav from './MessageJumpNav.vue'

const themeStore = useThemeStore()

const chatBackgroundType = computed(() => themeStore.config.chatBackgroundType)
const chatBackgroundImage = computed(() => themeStore.config.chatBackgroundImage)
const chatBackgroundImageOpacity = computed(() => themeStore.config.chatBackgroundImageOpacity ?? 0.5)
const chatBackgroundColor = computed(() => {
  if (chatBackgroundType.value === 'theme') return themeStore.config.pageBackground
  return themeStore.config.chatBackground
})
const workspaceOptions = {
  autoScroll: computed(() => themeStore.config.autoScroll)
}

const props = defineProps({
  currentMessages: {
    type: Array,
    required: true,
    default: () => []
  },
  isStreaming: {
    type: Boolean,
    default: false
  },
  selectedRole: {
    type: Object,
    default: null
  },
  selectedRoleName: {
    type: String,
    default: ''
  },
  currentChatId: {
    type: [Number, String],
    default: null
  },
  currentChatName: {
    type: String,
    default: ''
  },
  chatHistory: {
    type: Array,
    default: () => []
  },
  switchConversation: {
    type: Function,
    default: () => {}
  },
  ragEnabled: {
    type: Boolean,
    default: false
  },
  selectedKnowledgeBase: {
    type: Object,
    default: null
  }
})

const emit = defineEmits([
  'send-message',
  'cancel-streaming',
  'append-message',
  'update:currentMessages',
  'clear-chat',
  'update-chat-name'
])

// 最新一条用户消息的索引（用于就地编辑按钮显示）；找不到则返回 -1
const lastUserMessageIndex = computed(() => {
  const msgs = props.currentMessages
  for (let i = msgs.length - 1; i >= 0; i--) {
    if (msgs[i].role === 'user') return i
  }
  return -1
})


const {
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
  handleModelSelectionChange,
  handlePaste,
  handleRetryMessage,
  handleEditMessage,
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
  openRoleDescription,
  temperature,
  refreshModelOptions,
  refreshSourceOptions,
  removeAttachedFile,
  roleDescription,
  roleStatsInfo,
  saveConversationName,
  scrollToBottom,
  selectedLanguage,
  selectedRole,
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
  clearSearch,
} = useChatWorkspace(props, emit, workspaceOptions)
</script>

<style lang="scss" scoped>
/* ===== RETRO OS CHAT WORKSPACE - WINDOW CONTENT ===== */

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--chat-panel, #c0c0c0);
  overflow: hidden;
  /* Inset border for content area */
  border: none;
  border-radius: var(--chat-radius, 0);
  box-shadow: inset 1px 1px 0 var(--chat-inset-shadow, #808080), inset -1px -1px 0 var(--chat-inset-light, #ffffff);
  max-width: 100%;
  margin: 0 auto;
  padding: 0;
  font-family: var(--chat-font-family, 'MS Sans Serif', 'Segoe UI', Tahoma, sans-serif);
  font-size: 13px;
  position: relative;
}

.chat-background-image,
.chat-background-color {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  border-radius: var(--chat-radius, 0);
}

.chat-background-image {
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
}

.messages-area {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.messages {
  flex: 1;
  min-height: 200px;
  max-width: 100%;
  overflow-y: auto;
  position: relative;
  z-index: 1;
  contain: layout style paint;
  isolation: isolate;
  /* Retro sunken content panel (single source of truth for the list area) */
  background: var(--chat-sunken, #d4d0c8);
  box-shadow:
    inset 2px 2px 0 var(--chat-inset-shadow, #808080),
    inset -2px -2px 0 var(--chat-inset-light, #ffffff);
  border-radius: var(--chat-radius, 0);
  padding: 16px clamp(14px, 3vw, 30px) 20px;
}

.messages::-webkit-scrollbar {
  width: var(--chat-scrollbar-size, 17px);
}

.messages::-webkit-scrollbar-track {
  background: var(--chat-scrollbar-track, repeating-conic-gradient(#c0c0c0 0% 25%, #ffffff 0% 50%) 50% / 2px 2px);
  border: 1px solid var(--chat-scrollbar-border, #808080);
}

.messages::-webkit-scrollbar-thumb {
  background: var(--chat-scrollbar-thumb, #c0c0c0);
  border: 2px solid;
  border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);

  &:hover {
    background: var(--chat-panel-hover, #d4d4d4);
  }
}

.message-list-spacer {
  flex-shrink: 0;
}

.virtual-message-slot {
  margin-bottom: var(--message-spacing, 18px);
  max-width: 100%;
}

.virtual-message-slot > .message-item {
  margin-bottom: 0;
  max-width: 100%;
  overflow-wrap: break-word;
  word-break: break-word;
}

/* 消息间距统一由 .virtual-message-slot 承担，避免 ChatMessage 内部 margin 叠加 */
.virtual-message-slot :deep(.ai-message) {
  margin-bottom: 0;
}

.scroll-to-bottom-btn {
  position: absolute;
  bottom: 90px;
  right: 24px;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--chat-panel, #c0c0c0);
  color: var(--chat-text, #000000);
  border: 2px solid;
  border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
  cursor: pointer;
  box-shadow: var(--chat-shadow, none);
  z-index: 10;
  transition: none;
  border-radius: var(--chat-radius, 0);
}

.scroll-to-bottom-btn:hover {
  background: var(--chat-panel-hover, #d4d4d4);
  transform: none;
  box-shadow: var(--chat-shadow, none);
}

.scroll-to-bottom-btn:active {
  border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
  padding: 3px 1px 1px 3px;
}

.icon {
  width: 18px;
  height: 18px;
}

@media (max-width: 768px) {
  .chat-main {
    min-width: unset;
    border-radius: var(--chat-radius, 0);
    width: 100%;
  }

  .messages {
    padding: 12px 10px 16px;
    scroll-padding: 12px;
  }

  .scroll-to-bottom-btn {
    bottom: 80px;
    right: 16px;
    width: 28px;
    height: 28px;
  }
}

@media (max-width: 480px) {
  .scroll-to-bottom-btn {
    bottom: 76px;
    right: 12px;
    width: 24px;
    height: 24px;
  }

  .icon {
    width: 14px;
    height: 14px;
  }
}
</style>
