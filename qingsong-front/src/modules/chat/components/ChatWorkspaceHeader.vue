ix<template>
  <div class="chat-header">
    <div class="chat-title">
      <div class="role-badge-wrapper">
        <div class="role-badge" @click="copyRoleName" @mouseenter="updateRoleBadgeHoverState(true)"
          @mouseleave="updateRoleBadgeHoverState(false)" title="点击复制角色名称">
          <div class="role-icon-wrapper" :class="{ 'is-copied': isRoleNameCopied }">
            <CheckIcon v-if="isRoleNameCopied" class="role-icon success" />
            <ClipboardDocumentIcon v-else-if="isRoleBadgeHovered" class="role-icon action" />
            <CpuChipIcon v-else class="role-icon" />
          </div>
          <div class="role-name">{{ selectedRoleName || "AI Assistant" }}</div>
        </div>

        <div class="chat-name-section">
          <template v-if="isEditingConversationName">
            <input :ref="conversationNameInputRef" :value="editingConversationName" class="chat-name-input"
              placeholder="输入会话名称" @click.stop @input="handleConversationNameInput" @keyup.enter="saveConversationName"
              @keyup.escape="cancelEditConversationName" />
            <button class="chat-name-btn save" @click.stop="saveConversationName" title="保存">
              <CheckIcon class="icon-small" />
            </button>
            <button class="chat-name-btn cancel" @click.stop="cancelEditConversationName" title="取消">
              <XMarkIcon class="icon-small" />
            </button>
          </template>
          <template v-else>
            <button v-if="currentChatId" class="chat-name-edit-btn" @click.stop="startEditConversationName"
              title="编辑会话名称">
              <PencilIcon class="icon-small" />
            </button>
          </template>
        </div>
      </div>
    </div>

    <div class="chat-actions">
      <div class="action-group">
        <div class="model-selector">
          <select :value="activeSourceId" class="model-select"
            :disabled="isStreaming || isSwitchingModel || sourceOptions.length === 0" title="选择模型来源"
            @focus="refreshSourceOptions" @change="handleSourceSelectionChange">
            <option v-if="sourceOptions.length === 0" value="" disabled>加载中...</option>
            <option v-else-if="!activeSourceId" value="" disabled>请选择来源</option>
            <template v-else>
              <option v-for="source in sourceOptions" :key="source.id" :value="source.id">
                {{ source.sourceName || source.name || source.sourceCode || source.id }}
              </option>
            </template>
          </select>
        </div>

        <div class="model-selector">
          <select :value="activeModelId" class="model-select"
            :disabled="isStreaming || isSwitchingModel || sourceOptions.length === 0" title="选择 AI 模型"
            @focus="refreshModelOptions" @change="handleModelSelectionChange">
            <option v-if="modelOptions.length === 0" value="" disabled>请选择模型</option>
            <option v-else-if="!activeModelId" value="" disabled>请选择模型</option>
            <template v-else>
              <option v-for="model in modelOptions" :key="model.id" :value="model.id">
                {{ model.name }}
              </option>
            </template>
          </select>
        </div>
      </div>

      <div class="action-group search-group">
        <div class="chat-search" :class="{ active: searchQuery }">
          <MagnifyingGlassIcon class="search-icon" />
          <input :value="searchQuery" class="search-input" type="text" placeholder="搜索对话" @input="handleSearchInput"
            @keydown.esc="handleSearchClear" />
          <span v-if="searchQuery" class="search-count">{{ searchActiveIndex }}/{{ searchMatchCount }}</span>
          <button v-if="searchQuery" class="search-nav prev" title="上一个" @click="emit('search-prev')">
            <ChevronUpIcon class="icon" />
          </button>
          <button v-if="searchQuery" class="search-nav next" title="下一个" @click="emit('search-next')">
            <ChevronDownIcon class="icon" />
          </button>
          <button v-if="searchQuery" class="search-close" title="关闭" @click="handleSearchClear">
            <XMarkIcon class="icon" />
          </button>
        </div>
      </div>

      <div class="action-group">
        <button class="action-button collapse-toggle-button" :class="{ active: collapseAssistantMessages }"
          :title="collapseAssistantMessages ? '展开 AI 消息' : '折叠 AI 消息'" @click="toggleAssistantMessages">
          <span class="collapse-toggle-text">{{
            collapseAssistantMessages ? "展开AI" : "折叠AI"
          }}</span>
        </button>

        <button class="action-button" title="删除当前对话" @click="confirmDeleteConversation">
          <TrashIcon class="icon" />
        </button>
      </div>

      <div class="action-group">
        <NPopover trigger="click" placement="bottom-end" :show-arrow="false" class="chat-workspace-popover"
          content-class="chat-workspace-popover-content" :content-style="{
            padding: '4px',
            background: 'var(--chat-panel, #c0c0c0)',
            border: '2px solid var(--chat-bevel-shadow, #808080)',
            borderRadius: 'var(--chat-radius, 0)',
            boxShadow: 'var(--chat-popover-shadow, 2px 2px 0 rgba(0,0,0,0.25))'
          }">
          <template #trigger>
            <button class="action-button" title="更多选项">
              <EllipsisHorizontalIcon class="icon" />
            </button>
          </template>
          <div class="overflow-menu">
            <div class="overflow-menu-section">
              <div class="overflow-menu-heading">信息</div>
              <div class="overflow-menu-item static">
                <span class="overflow-menu-label">消息数</span>
                <span class="overflow-menu-value">{{ currentMessagesLength }} 条</span>
              </div>
              <button v-if="roleDescription" class="overflow-menu-item" type="button" @click="openRoleDescription">
                <span class="overflow-menu-label">角色描述</span>
                <span class="overflow-menu-value">查看</span>
              </button>
              <div v-else class="overflow-menu-item static">
                <span class="overflow-menu-label">角色描述</span>
                <span class="overflow-menu-value">暂无</span>
              </div>
              <button class="overflow-menu-item" type="button" @click="openRoleDescription">
                <span class="overflow-menu-label">聊天历史统计</span>
                <span class="overflow-menu-value">查看</span>
              </button>
              <button class="overflow-menu-item" type="button" @click="openReview">
                <span class="overflow-menu-label">对话复盘</span>
                <span class="overflow-menu-value">日报/周报</span>
              </button>
            </div>

            <div class="overflow-menu-section">
              <div class="overflow-menu-heading">生成参数</div>
              <div class="overflow-menu-item setting-row">
                <span class="overflow-menu-label">温度</span>
                <select class="overflow-select" :value="temperature" :disabled="isStreaming"
                  title="设置生成温度 (0 - 2，越高越随机)" @change="handleTemperatureChange">
                  <option v-for="option in temperatureOptions" :key="option.value" :value="option.value">
                    {{ option.label }}
                  </option>
                </select>
              </div>
              <div class="overflow-menu-item setting-row">
                <span class="overflow-menu-label">上下文</span>
                <input class="overflow-number-input" type="number" min="0" max="30" step="5" :value="contextSize"
                  @change="handleContextSizeChange" title="最近上下文消息条数（0 表示使用默认，每 5 条一档）" />
              </div>
            </div>

            <div class="overflow-menu-section">
              <div class="overflow-menu-heading">语音</div>
              <div class="overflow-menu-item static">
                <span class="overflow-menu-label">当前音色</span>
                <span class="overflow-menu-value"
                  :class="{ 'voice-mode-clone': voiceMode === '克隆', 'voice-mode-design': voiceMode === '设计' }">{{
                  voiceMode
                  }}</span>
              </div>
              <div class="overflow-menu-item setting-row">
                <span class="overflow-menu-label">音色</span>
                <select class="overflow-select" :value="voice" :disabled="voiceMode !== '预置'" title="选择朗读音色"
                  @change="handleVoiceChange">
                  <option v-for="option in TTS_VOICES" :key="option.value" :value="option.value">
                    {{ option.label }}
                  </option>
                </select>
              </div>
              <button class="overflow-menu-item setting-row" type="button" @click="toggleAutoPlay">
                <span class="overflow-menu-label">自动朗读</span>
                <span class="overflow-menu-value">{{ autoPlay ? "已开启" : "已关闭" }}</span>
              </button>
              <div class="overflow-menu-item static clone-row">
                <span class="overflow-menu-label">克隆音色</span>
                <span class="overflow-menu-value" :class="{ 'clone-set': !!cloneSample }">{{ cloneSample ? "已设置" : "未设置"
                  }}</span>
              </div>
              <div class="overflow-menu-item static clone-actions">
                <label class="clone-file-label" title="选择 mp3 / flac / m4a / wav / ogg 音频样本（约 1MB 以内）">
                  <span class="clone-file-text">{{ cloneSample ? "更换样本" : "选择音频" }}</span>
                  <input type="file" hidden
                    accept=".mp3,.flac,.m4a,.wav,.ogg,audio/mpeg,audio/x-flac,audio/flac,audio/mp4,audio/x-m4a,audio/m4a,audio/wav,audio/x-wav,audio/wave,audio/ogg,audio/oga,video/mp4"
                    @change="handleCloneFileChange" />
                </label>
                <button v-if="cloneSample" class="clone-mini-btn" type="button" title="预览克隆音色样本"
                  @click="previewCloneSample">预览</button>
                <button v-if="cloneSample" class="clone-mini-btn" type="button" title="清除克隆音色样本"
                  @click="clearClone">清除</button>
              </div>
              <div v-if="cloneSample" class="overflow-menu-item static clone-name-row">
                <span class="overflow-menu-label">样本文件</span>
                <span class="overflow-menu-value clone-name" :title="cloneSample.name">{{ cloneSample.name }}</span>
              </div>
              <div class="overflow-menu-item static design-row">
                <span class="overflow-menu-label">音色设计</span>
                <div class="design-input-wrap">
                  <input v-model="voiceDesignInput" class="overflow-text-input" type="text"
                    placeholder="描述音色，如：清脆活泼、语速偏快" title="输入想要的音色描述（使用 voicedesign 模型）" @change="saveVoiceDesign" />
                  <button v-if="voiceDesignInput" class="clone-mini-btn" type="button" title="清除音色设计"
                    @click="clearVoiceDesign">清除</button>
                </div>
              </div>
              <button class="overflow-menu-item setting-row" type="button" title="仅音色设计模型支持：自动润色/生成播报文本"
                @click="toggleOptimizePreview">
                <span class="overflow-menu-label">智能优化</span>
                <span class="overflow-menu-value">{{ optimizePreview ? "已开启" : "已关闭" }}</span>
              </button>
              <div class="overflow-menu-item setting-row">
                <span class="overflow-menu-label">倍速</span>
                <select class="overflow-select" :value="playbackRate" title="客户端播放倍速" @change="handleRateChange">
                  <option v-for="option in TTS_PLAYBACK_RATES" :key="option.value" :value="option.value">
                    {{ option.label }}
                  </option>
                </select>
              </div>
            </div>

            <div class="overflow-menu-section">
              <div class="overflow-menu-heading">通知</div>
              <button class="overflow-menu-item setting-row" type="button" @click="toggleAutoSend">
                <span class="overflow-menu-label">自动发飞书</span>
                <span class="overflow-menu-value">{{ autoSendToFeishu ? "已开启" : "已关闭" }}</span>
              </button>
            </div>

            <div class="overflow-menu-section">
              <div class="overflow-menu-heading">语音用量</div>
              <div class="overflow-menu-item static">
                <span class="overflow-menu-label">本次会话</span>
                <span class="overflow-menu-value">{{ ttsUsage.sessionTokens }} tokens</span>
              </div>
              <div class="overflow-menu-item static">
                <span class="overflow-menu-label">累计用量</span>
                <span class="overflow-menu-value">{{ ttsUsage.totalTokens }} tokens</span>
              </div>
              <button class="overflow-menu-item" type="button" @click="resetUsage">
                <span class="overflow-menu-label">重置统计</span>
                <span class="overflow-menu-value">重置</span>
              </button>
            </div>

            <div class="overflow-menu-section">
              <div class="overflow-menu-heading">TTS Key</div>
              <div class="overflow-menu-item static tts-key-row">
                <input v-model="ttsApiKeyInput" class="tts-key-input" type="password" placeholder="留空使用内置 Key"
                  autocomplete="new-password" data-lpignore="true" data-1p-ignore data-bwignore
                  :title="ttsApiKeyInput ? '自定义 MiMo API Key（覆盖内置）' : '使用内置 MiMo API Key'" />
                <button class="tts-key-save" type="button" title="保存 TTS API Key" @click="saveTtsApiKey">保存</button>
              </div>
            </div>
          </div>
        </NPopover>
      </div>
    </div>
  </div>

  <NModal v-if="showRoleDescriptionModal" :show="showRoleDescriptionModal" preset="card"
    :title="roleDescription ? '角色描述' : '聊天历史统计'" style="width: 560px; max-height: 70vh"
    @update:show="handleRoleDescriptionModalUpdate">
    <div class="role-description-modal">
      <div class="modal-header">
        <span class="modal-title">{{ selectedRoleName || "AI Assistant" }}</span>
        <span class="modal-subtitle">{{ roleDescription ? '完整角色描述' : '按时间范围统计' }}</span>
      </div>
      <div v-if="roleDescription" class="modal-content">
        <p class="modal-text">{{ roleDescription }}</p>
      </div>
      <div class="modal-section">
        <div class="section-title">聊天历史统计</div>
        <div v-if="roleStatsLoading" class="stats-empty">统计加载中...</div>
        <div v-else-if="!roleStatsInfo.ranges || roleStatsInfo.ranges.length === 0" class="stats-empty">
          暂无聊天记录
        </div>
        <div v-else class="stats-range-list">
          <div v-for="range in roleStatsInfo.ranges" :key="range.label" class="stats-range">
            <div class="stats-range-head">
              <span class="stats-range-label">{{ range.label }}</span>
              <span class="stats-range-summary">
                {{ range.sessionCount || 0 }} 次会话 · {{ range.messageCount || 0 }} 条消息
              </span>
            </div>
            <div class="stats-range-meta">
              <span>首次：{{ formatStatTime(range.firstChatAt) }}</span>
              <span>最近：{{ formatStatTime(range.lastChatAt) }}</span>
            </div>
          </div>
          <div class="stats-total">
            合计：{{ roleStatsInfo.totalSessionCount || 0 }} 次会话 · {{ roleStatsInfo.totalMessageCount || 0 }} 条消息
            <span v-if="roleStatsInfo.firstChatAt">（{{ formatStatTime(roleStatsInfo.firstChatAt) }} 起）</span>
          </div>
        </div>
      </div>
    </div>
  </NModal>

  <ChatReviewModal v-model:show="showReviewModal" />
</template>

<script setup>
import {
  CheckIcon,
  ChevronDownIcon,
  ChevronUpIcon,
  ClipboardDocumentIcon,
  CpuChipIcon,
  EllipsisHorizontalIcon,
  MagnifyingGlassIcon,
  PencilIcon,
  TrashIcon,
  XMarkIcon,
} from "@heroicons/vue/24/outline";
import { NModal, NPopover } from "naive-ui";
import { computed, defineEmits, defineProps, ref } from "vue";
import { useTtsPlayback } from "../composables/index.js";
import {
  TTS_CLONE_MAX_BASE64_CHARS,
  arrayBufferToBase64,
  dataUrlToBlob,
  detectAudioMime,
  getConfiguredTtsApiKey,
  setConfiguredTtsApiKey
} from "../services/index.js";
import ChatReviewModal from "./ChatReviewModal.vue";
import { useMessage } from "naive-ui";

const props = defineProps({
  activeSourceId: {
    type: String,
    default: "",
  },

  activeModelId: {
    type: String,
    default: "",
  },
  autoSendToFeishu: {
    type: Boolean,
    default: false,
  },
  collapseAssistantMessages: {
    type: Boolean,
    default: false,
  },
  conversationNameInputRef: {
    type: Object,
    default: null,
  },
  contextSize: {
    type: Number,
    default: 0,
  },
  currentChatId: {
    type: [Number, String],
    default: null,
  },
  currentChatName: {
    type: String,
    default: "",
  },
  currentMessagesLength: {
    type: Number,
    default: 0,
  },
  searchQuery: {
    type: String,
    default: "",
  },
  searchMatchCount: {
    type: Number,
    default: 0,
  },
  searchActiveIndex: {
    type: Number,
    default: 0,
  },
  editingConversationName: {
    type: String,
    default: "",
  },
  isEditingConversationName: {
    type: Boolean,
    default: false,
  },
  isRoleBadgeHovered: {
    type: Boolean,
    default: false,
  },
  isRoleNameCopied: {
    type: Boolean,
    default: false,
  },
  isStreaming: {
    type: Boolean,
    default: false,
  },
  isSwitchingModel: {
    type: Boolean,
    default: false,
  },
  modelOptions: {
    type: Array,
    default: () => [],
  },
  sourceOptions: {
    type: Array,
    default: () => [],
  },
  temperature: {
    type: [Number, String],
    default: 0.7,
  },
  roleDescription: {
    type: String,
    default: "",
  },
  roleStatsInfo: {
    type: Object,
    default: () => ({}),
  },
  roleStatsLoading: {
    type: Boolean,
    default: false,
  },
  selectedRoleName: {
    type: String,
    default: "",
  },
  showRoleDescriptionModal: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits([
  "cancel-edit-conversation-name",
  "confirm-delete-conversation",
  "copy-role-name",
  "model-selection-change",
  "open-role-description",
  "refresh-model-options",
  "refresh-source-options",
  "save-conversation-name",
  "start-edit-conversation-name",
  "toggle-assistant-messages",
  "toggle-auto-send",
  "update:search-query",
  "search-prev",
  "search-next",
  "search-clear",
  "update:activeModelId",
  "update:contextSize",
  "update:editingConversationName",
  "update:isRoleBadgeHovered",
  "update:activeSourceId",
  "update:showRoleDescriptionModal",
  "update:temperature",
]);

const formatStatTime = (value) => {
  if (!value) {
    return "—";
  }
  // 后端可能返回 ISO（含 T）或 yyyy-MM-dd HH:mm:ss 两种格式
  const str = String(value).replace("T", " ");
  return str.length > 19 ? str.slice(0, 19) : str;
};

const copyRoleName = () => emit("copy-role-name");
const openRoleDescription = () => emit("open-role-description");
const showReviewModal = ref(false);
const openReview = () => {
  showReviewModal.value = true;
};
const startEditConversationName = () => emit("start-edit-conversation-name");
const cancelEditConversationName = () => emit("cancel-edit-conversation-name");
const saveConversationName = () => emit("save-conversation-name");
const refreshModelOptions = () => emit("refresh-model-options");

const refreshSourceOptions = () => emit("refresh-source-options");
const toggleAssistantMessages = () => emit("toggle-assistant-messages");
const toggleAutoSend = () => emit("toggle-auto-send");

const handleSearchInput = (event) => emit("update:search-query", event.target.value);
const handleSearchClear = () => emit("search-clear");
const confirmDeleteConversation = () => emit("confirm-delete-conversation");
const updateRoleBadgeHoverState = (value) => emit("update:isRoleBadgeHovered", value);

const handleConversationNameInput = (event) => {
  emit("update:editingConversationName", event.target.value);
};

const handleSourceSelectionChange = (event) => {
  const sourceId = event.target.value;
  emit("update:activeSourceId", sourceId);
  emit("refresh-model-options", sourceId);
};

const handleModelSelectionChange = (event) => {
  emit("update:activeModelId", event.target.value);
  emit("model-selection-change");
};

const handleRoleDescriptionModalUpdate = (value) => {
  emit("update:showRoleDescriptionModal", value);
};

const temperatureOptions = [
  { value: 0, label: "0 - 严谨" },
  { value: 0.2, label: "0.2" },
  { value: 0.4, label: "0.4" },
  { value: 0.6, label: "0.6" },
  { value: 0.7, label: "0.7 - 默认" },
  { value: 0.8, label: "0.8" },
  { value: 1.0, label: "1.0 - 均衡" },
  { value: 1.2, label: "1.2" },
  { value: 1.5, label: "1.5" },
  { value: 2.0, label: "2.0 - 发散" },
];

const handleContextSizeChange = (event) => {
  emit("update:contextSize", event.target.value);
};

const handleTemperatureChange = (event) => {
  emit("update:temperature", Number(event.target.value));
};

// —— 语音播放（TTS）：音色 + 自动朗读（模块级单例，直接消费，无需 props 透传）——
const ttsPlayback = useTtsPlayback()
const TTS_VOICES = ttsPlayback.TTS_VOICES
const TTS_PLAYBACK_RATES = ttsPlayback.TTS_PLAYBACK_RATES
const voice = ttsPlayback.voice
const autoPlay = ttsPlayback.autoPlay
const cloneSample = ttsPlayback.cloneSample
const voiceDesign = ttsPlayback.voiceDesign
const optimizePreview = ttsPlayback.optimizePreview
const playbackRate = ttsPlayback.playbackRate
const ttsUsage = ttsPlayback.usage
const headerMessage = useMessage()
const ttsApiKeyInput = ref(getConfiguredTtsApiKey())

// 当前生效的音色模式：克隆 > 设计 > 预置
const voiceMode = computed(() => {
  if (cloneSample.value?.dataUrl) return '克隆'
  if (voiceDesign.value?.trim()) return '设计'
  return '预置'
})

const handleVoiceChange = (event) => {
  ttsPlayback.setVoice(event.target.value)
}

const toggleAutoPlay = () => {
  ttsPlayback.setAutoPlay(!ttsPlayback.autoPlay.value)
}

// —— 音色设计（voicedesign 模型）+ 智能优化（optimize_text_preview）——
const voiceDesignInput = ref(voiceDesign.value || '')
const saveVoiceDesign = () => {
  ttsPlayback.setVoiceDesign(voiceDesignInput.value)
  headerMessage.success(voiceDesignInput.value.trim() ? "音色设计已保存" : "已清除音色设计，恢复默认")
}
const clearVoiceDesign = () => {
  voiceDesignInput.value = ''
  ttsPlayback.setVoiceDesign('')
  headerMessage.success("已清除音色设计，恢复默认")
}
const toggleOptimizePreview = () => {
  const next = !ttsPlayback.optimizePreview.value
  ttsPlayback.setOptimizePreview(next)
  headerMessage.success(next ? "智能优化已开启（自动润色播报文本）" : "智能优化已关闭")
}

// —— 倍速播放（客户端 Web Audio playbackRate）——
const handleRateChange = (event) => {
  ttsPlayback.setPlaybackRate(event.target.value)
}

const saveTtsApiKey = () => {
  setConfiguredTtsApiKey(ttsApiKeyInput.value)
  headerMessage.success(ttsApiKeyInput.value.trim() ? "TTS API Key 已保存" : "已清除自定义 Key，恢复使用内置 Key")
}

// —— 音色克隆：选择音频样本 → 按字节魔数检测真实 MIME 构造 DataURL 存入 localStorage ——
// voiceclone 模型要求 audio.voice 为完整 DataURL：data:{MIME_TYPE};base64,$BASE64_AUDIO
// MIME 必须与音频真实容器一致（不能信扩展名 / file.type）：
// 手机录音常被改名成 .mp3/.wav，实际是 m4a/opus，靠扩展名映射仍会报 invalid audio format。
const CLONE_MIME_BY_EXT = {
  '.mp3': 'audio/mpeg',
  '.wav': 'audio/wav',
  '.flac': 'audio/flac',
  '.m4a': 'audio/mp4',
  '.ogg': 'audio/ogg'
}
const handleCloneFileChange = (event) => {
  const file = event.target.files?.[0]
  // 清空 input，允许重复选择同一文件
  event.target.value = ''
  if (!file) return

  const extension = `.${(file.name.split('.').pop() || '').toLowerCase()}`
  const fallbackMime = CLONE_MIME_BY_EXT[extension]

  const reader = new FileReader()
  reader.onload = () => {
    const base64 = arrayBufferToBase64(reader.result)
    if (!base64) {
      headerMessage.error('读取音频样本失败')
      return
    }
    if (base64.length > TTS_CLONE_MAX_BASE64_CHARS) {
      headerMessage.warning('音频样本过大，请选择 1MB 以内的文件')
      return
    }

    // 关键：优先按字节魔数检测真实格式，其次按扩展名兜底
    const mime = detectAudioMime(reader.result) || fallbackMime
    if (!mime) {
      headerMessage.warning('无法识别音频格式，请选择 mp3 / flac / m4a / wav / ogg 音频样本')
      return
    }

    const dataUrl = `data:${mime};base64,${base64}`
    ttsPlayback.setCloneSample({ name: file.name, dataUrl, mime })
    headerMessage.success(`已设置克隆音色：${file.name}（朗读与导出将使用该音色）`)
  }
  reader.onerror = () => {
    headerMessage.error('读取音频样本失败')
  }
  reader.readAsArrayBuffer(file)
}

const clearClone = () => {
  ttsPlayback.clearClone()
  headerMessage.success('已清除克隆音色，恢复使用预置音色')
}

// 预览克隆音色样本
const previewCloneSample = () => {
  const sample = cloneSample.value
  if (!sample?.dataUrl) return
  const url = URL.createObjectURL(dataUrlToBlob(sample.dataUrl))
  const audioEl = new Audio(url)
  audioEl.onended = () => URL.revokeObjectURL(url)
  audioEl.onerror = () => {
    URL.revokeObjectURL(url)
    headerMessage.error('音频样本预览失败')
  }
  audioEl.play().catch(() => {
    URL.revokeObjectURL(url)
    headerMessage.error('音频样本预览失败')
  })
}

const resetUsage = () => {
  ttsPlayback.resetUsage()
  headerMessage.success('语音用量统计已重置')
}
</script>

<style scoped lang="scss">
/* ===== RETRO OS WINDOW TITLE BAR - NAVY BLUE ===== */

.chat-header {
  padding: 5px 12px;
  border-bottom: none;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 6px 16px;
  flex-shrink: 0;
  min-height: 40px;
  height: auto;
  position: relative;
  z-index: 1;
  /* Navy blue title bar */
  background: linear-gradient(90deg, var(--chat-titlebar-start, #000080) 0%, var(--chat-titlebar-end, #1084d0) 100%);
  box-shadow: inset 0 -1px 0 var(--chat-titlebar-hairline, transparent);
  /* 允许工具行在空间不足时换行，而不是头部内横向滚动 */
  flex-wrap: wrap;
}

.chat-title {
  flex-direction: row;
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex: 1 1 auto;
  overflow: hidden;
}

.role-badge-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: nowrap;
  min-width: 0;
  flex: 0 1 auto;
  overflow: hidden;
}

.role-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 3px 10px;
  background: var(--chat-panel, #c0c0c0);
  border: 2px solid;
  border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
  border-radius: var(--chat-radius, 0);
  box-shadow: var(--chat-shadow, none);
  cursor: pointer;
  user-select: none;
  min-width: 0;
  flex-shrink: 1;
  max-width: 300px;
  transition: none;

  &:hover {
    background: var(--chat-panel-hover, #d4d4d4);
  }

  &:active {
    border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
  }
}

.role-icon-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: var(--chat-radius, 0);
  background: transparent;
  transition: none;
  flex-shrink: 0;

  .role-icon {
    width: 16px;
    height: 16px;
    color: var(--chat-text, #000000);
    transition: none;
    opacity: 1;
  }

  &.is-copied .role-icon {
    color: var(--chat-success, #008000);
  }
}

.role-name {
  font-size: 13px;
  font-weight: bold;
  color: var(--chat-text, #000000);
  letter-spacing: 0.2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);
}

.chat-name-section {
  display: flex;
  align-items: center;
  gap: 3px;
  min-width: 0;
  flex-shrink: 1;
}

.chat-name-input {
  padding: 1px 6px;
  font-size: 11px;
  border: 2px solid var(--chat-accent, #000080);
  border-radius: var(--chat-radius, 0);
  background: var(--chat-surface, #ffffff);
  color: var(--chat-text, #000000);
  outline: none;
  width: 110px;
  font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);

  &:focus {
    border-color: var(--chat-accent, #000080);
    box-shadow: var(--chat-shadow, none);
  }
}

.chat-name-edit-btn,
.chat-name-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: var(--chat-radius, 0);
  cursor: pointer;
  background: var(--chat-panel, #c0c0c0);
  border: 1px solid;
  border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
  transition: none;
}

.chat-name-edit-btn {
  color: var(--chat-accent, #000080);

  &:hover {
    background: var(--chat-panel-hover, #d4d4d4);
  }

  &:active {
    border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
  }
}

.chat-name-btn.save {
  color: var(--chat-success, #008000);

  &:hover {
    background: var(--chat-success-tint, #ccffcc);
  }
}

.chat-name-btn.cancel {
  color: var(--chat-danger-text, #800000);

  &:hover {
    background: var(--chat-danger-tint, #ffcccc);
  }
}

.icon-small {
  width: 13px;
  height: 13px;
}

.chat-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 1;
  /* 控件按「分组」换行，避免窄屏时头部内部横向滚动 */
  flex-wrap: wrap;
  row-gap: 4px;
  min-width: 0;
  justify-content: flex-end;
}

/* 控件分组：同一组内的控件横向排列，组之间用细分隔线隔开 */
.action-group {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  min-width: 0;
  padding-right: 14px;
  margin-right: 14px;
  border-right: 1px solid var(--chat-titlebar-separator, rgba(255, 255, 255, 0.3));

  &:last-child {
    margin-right: 0;
    padding-right: 0;
    border-right: none;
  }
}

.model-selector {
  margin-right: 0;
  flex-shrink: 0;
}

.model-select {
  height: 26px;
  padding: 0 8px;
  border: 2px solid;
  border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
  border-radius: var(--chat-radius, 0);
  background: var(--chat-surface, #ffffff);
  color: var(--chat-text, #000000);
  font-size: 12px;
  font-weight: normal;
  cursor: pointer;
  transition: none;
  outline: none;
  max-width: 180px;
  font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);

  &:hover:not(:disabled) {
    background: var(--chat-surface, #ffffff);
  }

  &:focus {
    outline: 2px dotted var(--chat-text, #000000);
    outline-offset: -2px;
  }

  &:disabled {
    opacity: 0.7;
    cursor: not-allowed;
    background: var(--chat-panel, #c0c0c0);
    border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
    color: var(--chat-text-muted, #808080);
  }
}

.context-size-input {
  width: 60px;
  height: 26px;
  box-sizing: border-box;
  padding: 0 6px;
  text-align: center;
  font-size: 12px;
  font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);
  color: var(--chat-text, #000000);
  background: var(--chat-surface, #ffffff);
  border: 2px solid;
  border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
  border-radius: var(--chat-radius, 0);
  outline: none;
  transition: none;

  &:focus {
    outline: 2px dotted var(--chat-text, #000000);
    outline-offset: -2px;
  }
}

/* 「更多」溢出菜单（NPopover teleport 到 body，菜单项仍带本组件 scoped 标识，样式可命中） */
.overflow-menu {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 170px;
  max-height: min(70vh, 70dvh, 560px);
  overflow-y: auto;
  overscroll-behavior: contain;

  &::-webkit-scrollbar {
    width: var(--chat-scrollbar-size, 17px);
  }

  &::-webkit-scrollbar-track {
    background: var(--chat-scrollbar-track, repeating-conic-gradient(#c0c0c0 0% 25%, #ffffff 0% 50%) 50% / 2px 2px);
    border: 1px solid var(--chat-scrollbar-border, #808080);
  }

  &::-webkit-scrollbar-thumb {
    background: var(--chat-scrollbar-thumb, #c0c0c0);
    border: 2px solid;
    border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);

    &:hover {
      background: var(--chat-panel-hover, #d4d4d4);
    }
  }

  .overflow-menu-section {
    display: flex;
    flex-direction: column;
    gap: 2px;

    &+.overflow-menu-section {
      margin-top: 6px;
      padding-top: 6px;
      border-top: 1px solid var(--chat-bevel-shadow, #808080);
    }
  }

  .overflow-menu-heading {
    padding: 1px 8px;
    font-size: 11px;
    font-weight: bold;
    color: var(--chat-text-muted, #808080);
    font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);
  }

  .overflow-menu-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 5px 8px;
    font-size: 12px;
    color: var(--chat-text, #000000);
    background: transparent;
    border: 1px solid transparent;
    border-radius: var(--chat-radius, 0);
    cursor: pointer;
    text-align: left;
    font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);

    &:hover {
      background: var(--chat-accent, #000080);
      color: var(--chat-text-on-accent, #ffffff);
    }

    &.static {
      cursor: default;

      &:hover {
        background: transparent;
        color: var(--chat-text, #000000);
      }
    }

    &.setting-row {
      cursor: default;

      &:hover {
        background: transparent;
        color: var(--chat-text, #000000);
      }
    }
  }

  .overflow-menu-label {
    white-space: nowrap;
  }

  .overflow-menu-value {
    opacity: 0.7;
    white-space: nowrap;

    &.voice-mode-clone {
      opacity: 1;
      color: var(--chat-success, #008000);
    }

    &.voice-mode-design {
      opacity: 1;
      color: var(--chat-accent, #000080);
    }
  }

  .overflow-select {
    flex: 1 1 auto;
    min-width: 0;
    max-width: 130px;
    height: 22px;
    box-sizing: border-box;
    padding: 0 6px;
    border: 2px solid;
    border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
    border-radius: var(--chat-radius, 0);
    background: var(--chat-surface, #ffffff);
    color: var(--chat-text, #000000);
    font-size: 11px;
    outline: none;
    font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);

    &:disabled {
      opacity: 0.7;
      cursor: not-allowed;
      background: var(--chat-panel, #c0c0c0);
      color: var(--chat-text-muted, #808080);
    }

    &:focus {
      outline: 2px dotted var(--chat-text, #000000);
      outline-offset: -2px;
    }
  }

  .overflow-number-input {
    width: 70px;
    height: 22px;
    box-sizing: border-box;
    padding: 0 6px;
    text-align: center;
    font-size: 11px;
    font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);
    color: var(--chat-text, #000000);
    background: var(--chat-surface, #ffffff);
    border: 2px solid;
    border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
    border-radius: var(--chat-radius, 0);
    outline: none;

    &:focus {
      outline: 2px dotted var(--chat-text, #000000);
      outline-offset: -2px;
    }
  }

  .clone-row,
  .clone-actions,
  .clone-name-row {
    cursor: default;
  }

  .clone-row .overflow-menu-value.clone-set {
    opacity: 1;
    color: var(--chat-success, #008000);
  }

  .clone-actions {
    gap: 8px;
    padding: 4px 8px;

    .clone-file-label {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      flex: 1 1 auto;
      min-width: 0;
      height: 24px;
      padding: 0 10px;
      border: 2px solid;
      border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
      border-radius: var(--chat-radius, 0);
      background: var(--chat-panel, #c0c0c0);
      color: var(--chat-text, #000000);
      font-size: 11px;
      cursor: pointer;
      font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);

      &:hover {
        background: var(--chat-panel-hover, #d4d4d4);
      }

      &:active {
        border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
      }
    }

    .clone-mini-btn {
      flex-shrink: 0;
      height: 24px;
      padding: 0 10px;
      border: 2px solid;
      border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
      border-radius: var(--chat-radius, 0);
      background: var(--chat-panel, #c0c0c0);
      color: var(--chat-text, #000000);
      font-size: 11px;
      cursor: pointer;
      font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);

      &:hover {
        background: var(--chat-panel-hover, #d4d4d4);
      }

      &:active {
        border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
      }
    }
  }

  .clone-name-row .clone-name {
    max-width: 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    opacity: 1;
  }

  .design-row {
    cursor: default;
  }

  .design-input-wrap {
    display: flex;
    align-items: center;
    gap: 6px;
    flex: 1 1 auto;
    min-width: 0;
  }

  .overflow-text-input {
    flex: 1 1 auto;
    min-width: 0;
    height: 24px;
    padding: 0 6px;
    border: 2px solid;
    border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
    border-radius: var(--chat-radius, 0);
    background: var(--chat-surface, #ffffff);
    color: var(--chat-text, #000000);
    font-size: 11px;
    outline: none;
    font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);

    &::placeholder {
      color: var(--chat-text-muted, #808080);
    }

    &:focus {
      outline: 2px dotted var(--chat-text, #000000);
      outline-offset: -2px;
    }
  }

  .tts-key-row {
    flex-wrap: nowrap;
    gap: 6px;
    padding: 4px 8px;

    .tts-key-input {
      flex: 1 1 auto;
      min-width: 0;
      height: 24px;
      padding: 0 6px;
      border: 2px solid;
      border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
      border-radius: var(--chat-radius, 0);
      background: var(--chat-surface, #ffffff);
      color: var(--chat-text, #000000);
      font-size: 11px;
      outline: none;
      font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);

      &:focus {
        outline: 2px dotted var(--chat-text, #000000);
        outline-offset: -2px;
      }
    }

    .tts-key-save {
      flex-shrink: 0;
      height: 22px;
      padding: 0 10px;
      border: 2px solid;
      border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
      border-radius: var(--chat-radius, 0);
      background: var(--chat-panel, #c0c0c0);
      color: var(--chat-text, #000000);
      font-size: 11px;
      cursor: pointer;
      font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);

      &:hover {
        background: var(--chat-panel-hover, #d4d4d4);
      }

      &:active {
        border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
        padding: 1px 9px 0 11px;
      }
    }
  }
}

.chat-search {
  display: flex;
  align-items: center;
  gap: 3px;
  flex-shrink: 1;
  min-width: 0;
  max-width: 200px;
  height: 26px;
  box-sizing: border-box;
  padding: 0 6px;
  background: var(--chat-panel, #c0c0c0);
  border: 2px solid;
  border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
  border-radius: var(--chat-radius, 0);
  transition: none;

  &.active,
  &:focus-within {
    background: var(--chat-surface, #ffffff);
  }

  .search-icon {
    width: 14px;
    height: 14px;
    flex-shrink: 0;
    color: var(--chat-accent, #000080);
    pointer-events: none;
  }

  .search-input {
    flex: 1;
    min-width: 40px;
    width: 100%;
    border: none;
    outline: none;
    background: transparent;
    font-size: 11px;
    color: var(--chat-text, #000000);
    font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);

    &::placeholder {
      color: var(--chat-text-muted, #808080);
    }
  }

  .search-count {
    flex-shrink: 0;
    font-size: 10px;
    color: var(--chat-accent, #000080);
    font-weight: bold;
    white-space: nowrap;
    font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);
  }

  .search-nav,
  .search-close {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 18px;
    height: 18px;
    flex-shrink: 0;
    border: 1px solid;
    border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
    background: var(--chat-panel, #c0c0c0);
    color: var(--chat-text, #000000);
    cursor: pointer;
    transition: none;

    &:hover {
      background: var(--chat-panel-hover, #d4d4d4);
    }

    &:active {
      border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
    }

    .icon {
      width: 12px;
      height: 12px;
    }
  }

  .search-close:hover {
    color: var(--chat-danger-text, #800000);
  }
}

.action-button {
  background: var(--chat-panel, #c0c0c0);
  border: 2px solid;
  border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
  width: 26px;
  height: 26px;
  border-radius: var(--chat-radius, 0);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--chat-text, #000000);
  transition: none;

  &:hover {
    background: var(--chat-panel-hover, #d4d4d4);
  }

  &:active {
    border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
    padding: 1px 0 0 1px;
  }

  &.active {
    background: var(--chat-accent, #000080);
    color: var(--chat-text-on-accent, #ffffff);
    border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);

    &:hover {
      background: var(--chat-accent-hover, #0000cc);
    }
  }

  &.collapse-toggle-button {
    width: auto;
    min-width: 60px;
    height: 26px;
    padding: 0 8px;
    border: 2px solid;
    border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
    background: var(--chat-panel, #c0c0c0);

    &:hover {
      background: var(--chat-panel-hover, #d4d4d4);
      color: var(--chat-text, #000000);
    }

    &:active {
      border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
    }
  }

  .icon {
    width: 16px;
    height: 16px;
  }
}

.collapse-toggle-text {
  font-size: 11px;
  font-weight: bold;
  line-height: 1;
  white-space: nowrap;
  font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);
}

.role-description-modal {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-height: 62vh;
  overflow-y: auto;
  padding-right: 4px;

  &::-webkit-scrollbar {
    width: 8px;
  }

  &::-webkit-scrollbar-thumb {
    background: var(--chat-border-soft, rgba(226, 232, 240, 0.9));
    border-radius: 4px;
  }
}

.modal-header {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.modal-title {
  font-size: 14px;
  font-weight: bold;
  color: var(--chat-text, #000000);
  font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);
}

.modal-subtitle {
  font-size: 11px;
  color: var(--chat-text-muted, #808080);
  font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);
}

.modal-content {
  max-height: 50vh;
  overflow-y: auto;
  padding: 8px;
  border-radius: var(--chat-radius, 0);
  background: var(--chat-surface, #ffffff);
  border: 2px solid;
  border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);

  &::-webkit-scrollbar {
    width: var(--chat-scrollbar-size, 17px);
  }

  &::-webkit-scrollbar-track {
    background: var(--chat-scrollbar-track, repeating-conic-gradient(#c0c0c0 0% 25%, #ffffff 0% 50%) 50% / 2px 2px);
    border: 1px solid var(--chat-scrollbar-border, #808080);
  }

  &::-webkit-scrollbar-thumb {
    background: var(--chat-scrollbar-thumb, #c0c0c0);
    border: 2px solid;
    border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
  }
}

.modal-text {
  margin: 0;
  white-space: pre-wrap;
  font-size: 12px;
  line-height: 1.45;
  color: var(--chat-text, #000000);
  font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);
}

@media (max-width: 1024px) {
  .chat-header {
    padding: 3px 8px;
    gap: 6px 8px;
  }

  .chat-actions {
    gap: 4px;
  }
}

@media (max-width: 768px) {
  .chat-header {
    flex-direction: column;
    align-items: stretch;
    padding: 2px 8px;
    gap: 2px;
    height: auto;
    min-height: 0;
  }

  /* 标题行与工具栏各占一行，工具栏内部按分组换行 */
  .chat-title {
    width: 100%;
    flex: none;
  }

  .chat-actions {
    width: 100%;
    flex: none;
    justify-content: flex-start;
    gap: 2px 4px;
    row-gap: 2px;
  }

  .action-group {
    padding-right: 6px;
    margin-right: 6px;
  }
}

/* 小屏（手机）精简工具栏：隐藏次要控件，减少换行行数 */
@media (max-width: 480px) {
  .chat-header {
    padding: 2px 6px;
    gap: 2px;
    min-height: 30px;
  }

  .chat-title {
    min-width: 0;
  }

  .chat-search {
    max-width: 140px;
  }

  .chat-actions {
    gap: 2px;
  }
}

/* ===== ROLE DESCRIPTION MODAL STATS ===== */
.modal-header {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.modal-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--chat-surface-text, #111827);
  font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);
}

.modal-subtitle {
  font-size: 11px;
  color: var(--chat-text-muted, #808080);
  font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);
}

.modal-content {
  max-height: 40vh;
  overflow-y: auto;
  font-size: 12px;
  line-height: 1.5;
  color: var(--chat-text, #000000);
  white-space: pre-wrap;
  word-break: break-word;
  padding: 8px;
  border-radius: var(--chat-radius, 0);
  background: var(--chat-surface, #ffffff);
  border: 2px solid;
  border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);

  &::-webkit-scrollbar {
    width: var(--chat-scrollbar-size, 17px);
  }

  &::-webkit-scrollbar-track {
    background: var(--chat-scrollbar-track, repeating-conic-gradient(#c0c0c0 0% 25%, #ffffff 0% 50%) 50% / 2px 2px);
    border: 1px solid var(--chat-scrollbar-border, #808080);
  }

  &::-webkit-scrollbar-thumb {
    background: var(--chat-scrollbar-thumb, #c0c0c0);
    border: 2px solid;
    border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
  }
}

.modal-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 10px;
  border-top: 1px solid var(--chat-bevel-shadow, #808080);
}

.section-title {
  font-size: 12px;
  font-weight: 700;
  color: var(--chat-surface-text, #111827);
  font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);
  padding: 1px 2px;
}

.stats-empty {
  font-size: 12px;
  color: var(--chat-text-muted, #808080);
  padding: 6px 8px;
}

.stats-range-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.stats-range {
  border: 2px solid;
  border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
  border-radius: var(--chat-radius, 0);
  padding: 7px 10px;
  background: var(--chat-panel, #c0c0c0);
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);
}

.stats-range-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.stats-range-label {
  font-size: 12px;
  font-weight: 700;
  color: var(--chat-surface-text, #111827);
}

.stats-range-summary {
  font-size: 12px;
  font-weight: 700;
  color: var(--chat-surface-text, #111827);
}

.stats-range-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 16px;
  font-size: 11px;
  color: var(--chat-text-muted, #555555);
}

.stats-total {
  margin-top: 4px;
  padding-top: 8px;
  border-top: 1px solid var(--chat-bevel-shadow, #808080);
  font-size: 12px;
  font-weight: 700;
  color: var(--chat-surface-text, #111827);
  font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);
}
</style>
