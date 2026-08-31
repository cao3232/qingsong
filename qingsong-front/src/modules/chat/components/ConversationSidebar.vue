<template>
  <div class="chat-sidebar" :class="{ 'show-delete-actions': showDeleteButtons }">
    <!-- 今日记录 -->
    <div class="today-section">
      <div class="section-header">
        <div class="header-content">
          <div class="header-icon">
            <CalendarIcon />
          </div>
          <h3 class="section-title">聊天记录</h3>
        </div>
        <div class="header-buttons">
          <n-popover :show="showCalendar" placement="right-start" trigger="manual" :show-arrow="false"
            @clickoutside="showCalendar = false">
            <template #trigger>
              <button class="calendar-select-btn" :class="{ active: showCalendar || selectedDateFilter }"
                @click="selectCalendar()" :title="selectedDateFilter ? '切换历史记录日期' : '选择历史记录'">
                <CalendarDaysIcon class="refresh-icon" />
              </button>
            </template>
            <n-config-provider :locale="zhCN" :date-locale="dateZhCN">
              <n-date-picker v-model:value="timestamp" panel type="date" value-format="yyyy.MM.dd HH:mm:ss"
                :is-date-disabled="isDateDisabled" :cell-class="dateCellClass" @update:value="onDateSelected" />
            </n-config-provider>
          </n-popover>
          <button class="search-toggle-btn" :class="{ active: searchExpanded }"
            :title="searchExpanded ? '收起搜索' : '搜索会话 / 消息'" @click="toggleSearch">
            <MagnifyingGlassIcon class="search-toggle-icon" />
          </button>
          <button class="new-chat-btn" @click="createNewChat()" title="新建会话">
            <PlusIcon class="new-chat-icon" />
          </button>
          <!-- 低频操作折叠进「更多」菜单：按钮过多会把标题栏挤压换行撑高 -->
          <n-popover v-model:show="showMoreMenu" trigger="click" placement="bottom-end" :show-arrow="false"
            class="chat-workspace-popover" content-class="chat-workspace-popover-content" :content-style="{
              padding: '4px',
              background: 'var(--chat-panel, #c0c0c0)',
              border: '2px solid var(--chat-bevel-shadow, #808080)',
              borderRadius: 'var(--chat-radius, 0)',
              boxShadow: 'var(--chat-popover-shadow, 2px 2px 0 rgba(0,0,0,0.25))'
            }">
            <template #trigger>
              <button class="more-menu-btn" :class="{ active: showMoreMenu }" title="更多操作">
                <EllipsisHorizontalIcon class="more-menu-icon" />
              </button>
            </template>
            <div class="sidebar-more-menu">
              <button class="more-menu-item" @click="handleMoreAction(openFavoritesPage)">
                <StarIcon class="more-menu-item-icon" /><span>我的收藏</span>
              </button>
              <button class="more-menu-item" @click="handleMoreAction(returnHomePage)">
                <WindowIcon class="more-menu-item-icon" /><span>返回首页</span>
              </button>
              <button class="more-menu-item" @click="handleMoreAction(refreshRoles)">
                <ArrowPathIcon class="more-menu-item-icon" /><span>刷新记录</span>
              </button>
              <button class="more-menu-item" :class="{ active: showDeleteButtons }"
                @click="handleMoreAction(toggleDeleteButtons)">
                <TrashIcon class="more-menu-item-icon" /><span>{{ showDeleteButtons ? "隐藏删除按钮" : "显示删除按钮"
                  }}</span>
              </button>
            </div>
          </n-popover>
        </div>
      </div>

      <!-- 搜索框默认收起为工具行按钮，点击展开；仅 Esc / × / 再点按钮手动收起（收起即清空） -->
      <div v-if="searchExpanded" class="history-search-bar">
        <input ref="searchInputRef" v-model="searchInput" type="text" class="history-search-input"
          placeholder="搜索会话标题 / 消息内容…" @keydown.esc="collapseSearch" />
        <button v-if="searchInput" class="clear-search-btn" @click="collapseSearch" title="清除并收起搜索">×</button>
      </div>

      <!-- 消息内容命中区（仅搜索态显示；会话标题命中即下方列表本身） -->
      <div v-if="searchKeyword" class="message-search-results">
        <div class="search-result-header">
          {{ isSearching ? "正在搜索消息内容…" : `消息内容匹配 ${searchHits.length} 条` }}
        </div>
        <template v-for="group in groupedSearchHits" :key="group.sessionNo">
          <div class="search-result-session" :title="group.sessionTitle">{{ group.sessionTitle || "未命名会话" }}</div>
          <div v-for="hit in group.hits" :key="hit.messageNo" class="search-hit-item"
            @click="handleOpenSearchHit(hit)">
            <span class="hit-role">{{ hit.messageType === "USER" ? "我" : "AI" }}</span>
            <span class="hit-snippet"><template v-for="(seg, i) in hit.segments" :key="i"><mark
                v-if="seg.mark">{{ seg.text }}</mark><template v-else>{{ seg.text }}</template></template></span>
            <span class="hit-time">{{ formatHitTime(hit.createdAt) }}</span>
          </div>
        </template>
      </div>

      <div v-if="selectedDateFilter" class="date-filter-bar">
        <span class="date-filter-text">筛选日期：{{ formatConversationDate(selectedDateFilter) }}</span>
        <button class="clear-date-filter" @click="clearDateFilter" title="清除日期筛选">
          ×
        </button>
      </div>

      <div class="chat-list">
        <div v-if="filteredChats.length === 0" class="empty-placeholder">
          <div class="empty-icon">
            <ChatBubbleIcon />
          </div>
          <p class="empty-text">
            {{ selectedDateFilter ? "该日期没有对话记录" : "还没有对话记录" }}
          </p>
        </div>

        <template v-for="group in groupedChats" :key="group.label">
          <div v-if="group.label" class="chat-group-header">
            <span class="chat-group-label">{{ group.label }}</span>
            <span class="chat-group-count">{{ group.items.length }}</span>
          </div>
          <div v-for="chat in group.items" :key="chat.id" class="chat-item"
            :class="{ active: currentChatId === chat.id }" @click="handleLoadChat(chat.id)"
            @contextmenu.prevent="handleContextMenu($event, chat)">
            <div class="chat-avatar">
              <ChatBubbleIcon />
            </div>
            <div class="chat-content">
              <div class="chat-title-row">
                <span class="chat-title">{{ chat.displayTitle }}</span>
                <span class="chat-time">{{ chat.displayTime }}</span>
              </div>
              <div class="chat-meta">
                <span v-if="chat.name" class="chat-role">{{ chat.name }}</span>
                <span class="chat-count" :title="`${chat.messageCount || 0} 条消息`">{{ chat.messageCount || 0 }} 条</span>
              </div>
            </div>
            <button class="delete-btn" @click.stop="handleDeleteChat(chat)" title="删除对话">
              <TrashIcon class="delete-icon" />
            </button>
          </div>
        </template>

        <!-- 无限滚动哨兵：进入视口即加载下一页（搜索态不分页，搜索结果由接口截断） -->
        <div v-if="historyHasMore && !searchKeyword" ref="historySentinelRef" class="history-load-more">
          {{ historyLoadingMore ? "加载中…" : "向下滚动加载更多" }}
        </div>
      </div>
    </div>

    <!-- 用户消息（懒加载） -->
    <div class="messages-entry">
      <div class="section-header">
        <div class="header-content">
          <div class="header-icon">
            <UserIcon />
          </div>
          <h3 class="section-title">用户消息</h3>
        </div>
        <button class="open-messages-btn" type="button" :disabled="isPreparingUserMessages" @click="openUserMessages">
          {{ isPreparingUserMessages ? "整理中" : "查看" }}
        </button>
      </div>
      <div class="feature-cards">
        <div class="feature-card feature-card-clickable"
          :class="{ active: ragEnabled, 'rag-card-connected': ragEnabled }" @click="handleOpenRag">
          <div class="feature-title">
            <span>RAG 对话</span>
            <span v-if="ragEnabled && selectedKnowledgeBase" class="rag-badge">{{
              selectedKnowledgeBase.name
              }}</span>
          </div>
          <p class="feature-desc">
            {{
              ragEnabled
                ? `已连接: ${selectedKnowledgeBase?.name}`
                : "连接知识库后可基于检索结果进行对话"
            }}
          </p>
          <div class="feature-card-footer">
            <span class="feature-hint">{{
              ragEnabled ? "点击卡片可更换知识库" : "点击卡片配置知识库"
              }}</span>
            <button v-if="ragEnabled" class="feature-action feature-action-compact danger" @click.stop="disableRag">
              断开
            </button>
          </div>
        </div>
        <div class="feature-card feature-card-clickable" :class="{ active: selectedToolCount > 0 }"
          @click="handleOpenTools">
          <div class="feature-title">
            <span>Tools</span>
            <span v-if="selectedToolCount > 0" class="tool-badge">已启用 {{ selectedToolCount }}</span>
          </div>
          <p class="feature-desc">{{ enabledToolsSummary }}</p>
        </div>
      </div>
    </div>

    <NModal v-if="showUserMessagesModal" v-model:show="showUserMessagesModal" preset="card" title="用户消息"
      style="width: min(560px, calc(100vw - 32px)); max-height: 80vh">
      <div class="user-message-modal">
        <div class="modal-header">
          <span class="modal-subtitle">
            {{
              isPreparingUserMessages
                ? "正在整理用户消息..."
                : `已加载 ${userMessages.length} 条用户消息`
            }}
          </span>
        </div>
        <div class="message-list">
          <div v-if="isPreparingUserMessages" class="loading-placeholder">
            <NSpin size="small" />
            <span>正在加载用户消息...</span>
          </div>
          <div v-else-if="userMessages.length === 0" class="empty-placeholder">
            <div class="empty-icon">
              <UserIcon />
            </div>
            <p class="empty-text">暂无消息记录</p>
          </div>

          <div v-for="(message, index) in validUserMessages" :key="message.id || index" class="message-item"
            @click="handleJumpToMessage(message)">
            <div class="message-number">{{ index + 1 }}</div>
            <div class="message-content">
              <div class="message-text">{{ message.truncatedContent }}</div>
              <div class="message-time">{{ message.formattedTime }}</div>
            </div>
          </div>
        </div>
      </div>
    </NModal>

    <NModal v-model:show="showToolsModal" preset="card" title="配置工具"
      style="width: min(640px, calc(100vw - 32px)); max-height: 80vh">
      <div class="tools-modal">
        <div class="tools-modal-header">
          <span class="modal-subtitle">{{ toolsModalSubtitle }}</span>
        </div>

        <div v-if="isLoadingTools" class="loading-state">
          <NSpin size="medium" />
          <span>正在加载工具列表...</span>
        </div>

        <div v-else-if="toolsLoadError" class="tools-empty-state error">
          <p>{{ toolsLoadError }}</p>
          <NButton type="primary" size="small" @click="retryLoadTools">重新加载</NButton>
        </div>

        <div v-else>
          <div v-if="availableToolGroups.length === 0" class="tools-empty-state">
            <p>{{ toolsEmptyStateText }}</p>
          </div>

          <div v-else class="tool-tabs-layout">
            <!-- 横向分组 Tab：点击仅切换查看，启用/停用在下方面板操作 -->
            <div class="tool-group-tabs" role="tablist">
              <button v-for="group in availableToolGroups" :key="group.groupKey" class="tool-group-tab"
                :class="{ active: group.groupKey === activeToolGroupKey, enabled: isGroupSelected(group.groupKey) }"
                role="tab" :aria-selected="group.groupKey === activeToolGroupKey" type="button"
                @click="activeToolGroupKey = group.groupKey">
                <span class="tool-group-tab-dot" aria-hidden="true"></span>
                <span class="tool-group-tab-label">{{ group.groupKey }}</span>
                <span class="tool-group-tab-count">{{ group.tools.length }}</span>
              </button>
            </div>

            <section v-if="activeToolGroup" :key="activeToolGroup.groupKey" class="tool-group-panel">
              <div class="tool-group-panel-header">
                <div class="tool-group-panel-title">
                  <span class="tool-group-panel-name">{{ activeToolGroup.groupKey }}</span>
                  <span class="tool-group-panel-count">共 {{ activeToolGroup.tools.length }} 个工具</span>
                </div>
                <NButton size="small" :type="isGroupSelected(activeToolGroup.groupKey) ? 'default' : 'primary'"
                  @click="selectToolGroup(activeToolGroup.groupKey)">
                  {{ isGroupSelected(activeToolGroup.groupKey) ? "停用该组" : "启用该组全部工具" }}
                </NButton>
              </div>

              <div class="tool-list">
                <div v-for="tool in activeToolGroup.tools" :key="tool.key" class="tool-option"
                  :class="{ selected: isGroupSelected(activeToolGroup.groupKey) }">
                  <div class="tool-option-main">
                    <div class="tool-option-title-row">
                      <span class="tool-option-title">{{ tool.label }}</span>
                      <span class="tool-option-key">{{ tool.name }}</span>
                    </div>
                    <p class="tool-option-desc">{{ tool.description }}</p>
                  </div>
                  <input class="tool-option-checkbox" type="checkbox"
                    :checked="isGroupSelected(activeToolGroup.groupKey)" disabled />
                </div>
              </div>
            </section>
          </div>
        </div>
      </div>
      <template #footer>
        <div class="modal-footer tools-modal-footer">
          <span class="tools-footer-hint">已启用 {{ selectedToolCount }} 个工具</span>
          <div class="modal-footer-actions">
            <NButton :disabled="selectedToolCount === 0" @click="clearSelectedTools">清空</NButton>
            <NButton @click="showToolsModal = false">关闭</NButton>
          </div>
        </div>
      </template>
    </NModal>

    <!-- 知识库选择弹窗 -->
    <NModal v-model:show="showKnowledgeBaseModal" preset="card" title="选择知识库"
      style="width: min(480px, calc(100vw - 32px)); max-height: 80vh">
      <div class="knowledge-base-modal">
        <div v-if="loadingKnowledgeBases" class="loading-state">
          <NSpin size="medium" />
          <span>加载中...</span>
        </div>
        <div v-else-if="knowledgeBases.length === 0" class="empty-kb">
          <div class="empty-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path
                d="M12 6.042A8.967 8.967 0 0 0 6 3.75c-1.052 0-2.062.18-3 .512v14.25A8.987 8.987 0 0 1 6 18c2.305 0 4.408.867 6 2.292m0-14.25a8.966 8.966 0 0 1 6-2.292c1.052 0 2.062.18 3 .512v14.25A8.987 8.987 0 0 0 18 18a8.967 8.967 0 0 0-6 2.292m0-14.25v14.25" />
            </svg>
          </div>
          <p>暂无可用知识库</p>
          <NButton type="primary" size="small" @click="goToKnowledgeBase">去创建</NButton>
        </div>
        <div v-else class="kb-list">
          <div v-for="kb in knowledgeBases" :key="kb.id" class="kb-item"
            :class="{ selected: tempSelectedKB?.id === kb.id }" @click="tempSelectedKB = kb">
            <div class="kb-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path
                  d="M12 6.042A8.967 8.967 0 0 0 6 3.75c-1.052 0-2.062.18-3 .512v14.25A8.987 8.987 0 0 1 6 18c2.305 0 4.408.867 6 2.292m0-14.25a8.966 8.966 0 0 1 6-2.292c1.052 0 2.062.18 3 .512v14.25A8.987 8.987 0 0 0 18 18a8.967 8.967 0 0 0-6 2.292m0-14.25v14.25" />
              </svg>
            </div>
            <div class="kb-info">
              <div class="kb-name">{{ kb.name }}</div>
              <div class="kb-meta">
                <span>{{ kb.documentCount || 0 }} 个文档</span>
                <span>{{ formatDate(kb.createDate) }}</span>
              </div>
            </div>
            <div v-if="tempSelectedKB?.id === kb.id" class="kb-check">
              <svg viewBox="0 0 24 24" fill="currentColor">
                <path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z" />
              </svg>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <div class="modal-footer">
          <NButton @click="showKnowledgeBaseModal = false">取消</NButton>
          <NButton type="primary" @click="confirmKnowledgeBase" :disabled="!tempSelectedKB">确认连接</NButton>
        </div>
      </template>
    </NModal>
  </div>
</template>

<script setup>
import { chatAPI, chatKnowledgeAPI } from "../services/index.js";
import { ref, computed, nextTick, watch, onMounted, onBeforeUnmount } from "vue";
import { useRouter } from "vue-router";
import {
  useMessage,
  useDialog,
  NModal,
  NButton,
  NSpin,
  NDatePicker,
  NPopover,
  NConfigProvider,
  zhCN,
  dateZhCN,
} from "naive-ui";
import {
  ArrowPathIcon,
  PlusIcon,
  TrashIcon,
  CalendarDaysIcon,
  EllipsisHorizontalIcon,
  MagnifyingGlassIcon,
  WindowIcon,
  StarIcon,
} from "@heroicons/vue/24/outline";
import CalendarIcon from "./icons/CalendarIcon.vue";
import ChatBubbleIcon from "./icons/ChatBubbleIcon.vue";
import UserIcon from "./icons/UserIcon.vue";
import { highlightKeyword } from "../utils/messageSearch.js";
import { dayRangeOf } from "../utils/chatHistoryPager.js";

const TOOL_GROUP_STORAGE_KEY = "ai-chat-selected-tool-group";

const props = defineProps({
  selectedRole: {
    type: Object,
    default: null,
  },
  selectedRoleName: {
    type: String,
    default: "",
  },
  currentChatId: {
    type: [Number, String],
    default: null,
  },
  chatHistory: {
    type: Array,
    default: () => [],
  },
  currentMessages: {
    type: Array,
    default: () => [],
  },
  // 会话列表分页：是否还有下一页 / 是否正在加载下一页
  historyHasMore: {
    type: Boolean,
    default: false,
  },
  historyLoadingMore: {
    type: Boolean,
    default: false,
  },
  // 有会话记录的日期集合（yyyy-MM-dd），由父组件从 /dates 接口加载
  historyDates: {
    type: Array,
    default: () => [],
  },
});

const showUserMessagesModal = ref(false);
const isPreparingUserMessages = ref(false);
const userMessages = ref([]);
const validUserMessages = computed(() => userMessages.value.filter(Boolean));
const showDeleteButtons = ref(false);
const router = useRouter();
const showToolsModal = ref(false);
const isLoadingTools = ref(false);
const toolsLoadError = ref("");
const availableToolGroups = ref([]);
const selectedToolGroupKeys = ref([]);
// 配置工具弹窗中当前查看的工具分组（横向 Tab），仅控制展示，不影响启用状态
const activeToolGroupKey = ref("");

if (typeof window !== "undefined") {
  try {
    const savedToolGroup = window.localStorage.getItem(TOOL_GROUP_STORAGE_KEY);
    if (savedToolGroup) {
      const parsed = JSON.parse(savedToolGroup);
      if (Array.isArray(parsed)) {
        selectedToolGroupKeys.value = parsed.filter(
          (key) => typeof key === "string" && key.length > 0
        );
      }
    }
  } catch {
    selectedToolGroupKeys.value = [];
  }
}

watch(
  () => selectedToolGroupKeys.value,
  (keys) => {
    if (typeof window === "undefined") {
      return;
    }
    if (keys.length === 0) {
      window.localStorage.removeItem(TOOL_GROUP_STORAGE_KEY);
      return;
    }
    window.localStorage.setItem(TOOL_GROUP_STORAGE_KEY, JSON.stringify(keys));
  },
  { deep: true }
);

// RAG 相关状态
const showKnowledgeBaseModal = ref(false);
const knowledgeBases = ref([]);
const selectedKnowledgeBase = ref(null);
const tempSelectedKB = ref(null);
const loadingKnowledgeBases = ref(false);
const ragEnabled = ref(false);

const emit = defineEmits([
  "load-chat",
  "load-roles",
  "load-history",
  "load-more-history",
  "apply-history-filter",
  "open-chat-at-message",
  "load-latest-chat",
  "jump-to-message",
  "create-new-chat",
  "remove-chat",
  "rag-change",
]);

const message = useMessage();
const dialog = useDialog();

let userMessagesBuildTimer = null;

const toggleDeleteButtons = () => {
  showDeleteButtons.value = !showDeleteButtons.value;
};

const isGroupSelected = (groupKey) => selectedToolGroupKeys.value.includes(groupKey);

const selectedToolCount = computed(() =>
  availableToolGroups.value
    .filter((group) => isGroupSelected(group.groupKey))
    .reduce((sum, group) => sum + group.tools.length, 0)
);

const selectedGroupLabels = computed(() =>
  availableToolGroups.value
    .filter((group) => isGroupSelected(group.groupKey))
    .map((group) => group.groupKey)
);

// 当前查看的工具分组：按 key 查找，key 失效时回退到第一组
const activeToolGroup = computed(() => {
  const groups = availableToolGroups.value;
  if (groups.length === 0) {
    return null;
  }

  return (
    groups.find((group) => group.groupKey === activeToolGroupKey.value) ||
    groups[0]
  );
});

// 工具分组数据变化后，初始化/修复当前查看的分组 Tab
watch(
  () => availableToolGroups.value,
  (groups) => {
    if (!Array.isArray(groups) || groups.length === 0) {
      activeToolGroupKey.value = "";
      return;
    }

    const exists = groups.some(
      (group) => group.groupKey === activeToolGroupKey.value
    );
    if (!exists) {
      activeToolGroupKey.value = groups[0].groupKey;
    }
  }
);

const toolsModalSubtitle = computed(() => {
  if (availableToolGroups.value.length === 0) {
    return "从接口读取可用工具，当前没有可展示的工具分组。";
  }

  return "选择工具分组可一键启用该组全部工具，取消选择则停用该组。";
});

const toolsEmptyStateText = computed(() => {
  if (availableToolGroups.value.length === 0) {
    return "接口当前没有返回可用工具分组。";
  }

  return "当前没有可展示的工具分组。";
});

const enabledToolsSummary = computed(() => {
  if (selectedToolCount.value === 0) {
    return "未启用工具，可点击工具分组一键启用该组全部工具。";
  }

  const preview = selectedGroupLabels.value.slice(0, 2).join("、");

  if (selectedGroupLabels.value.length <= 2) {
    return `已启用：${preview}，共 ${selectedToolCount.value} 个工具`;
  }

  return `已启用：${preview} 等 ${selectedGroupLabels.value.length} 个分组，共 ${selectedToolCount.value} 个工具`;
});

const formatMessageTime = (timestamp, now) => {
  if (!timestamp) {
    return "历史消息";
  }

  const date = new Date(timestamp);
  const diff = now - date;

  if (diff < 60000) return "刚刚";
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`;
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`;
  return date.toLocaleDateString("zh-CN");
};

const buildUserMessages = () => {
  try {
    const now = new Date();
    const nextMessages = [];
    const messages = Array.isArray(props.currentMessages) ? props.currentMessages : [];

    for (let index = messages.length - 1; index >= 0; index -= 1) {
      const currentMessage = messages[index];

      if (!currentMessage || currentMessage.role !== "user") {
        continue;
      }

      const hasAccurateTimestamp =
        currentMessage.hasAccurateTimestamp !== false &&
        Boolean(currentMessage.timestamp);
      const timestamp = hasAccurateTimestamp ? currentMessage.timestamp : null;
      const content = currentMessage.content || "";

      nextMessages.push({
        ...currentMessage,
        sourceMessageIndex: index,
        timestamp,
        formattedTime: formatMessageTime(timestamp, now),
        truncatedContent:
          content.length > 30 ? `${content.substring(0, 30)}...` : content,
      });

      if (nextMessages.length >= 50) {
        break;
      }
    }

    userMessages.value = nextMessages;
  } finally {
    // 无论构建是否成功，都必须结束加载状态，避免“正在加载用户消息”卡死
    isPreparingUserMessages.value = false;
    userMessagesBuildTimer = null;
  }
};

const resolveChatDisplayTime = (chat) => {
  const rawValue =
    chat?.lastMessageAt ?? chat?.createdAt ?? chat?.timestamp ?? chat?.created_at ?? null;

  if (!rawValue) {
    return null;
  }

  const date = new Date(rawValue);
  return Number.isNaN(date.getTime()) ? null : date;
};

const normalizeConversationText = (value) =>
  String(value || "")
    .replace(/\s+/g, " ")
    .trim();

const buildConversationPreviewTitle = (value) => {
  const normalizedText = normalizeConversationText(value);

  if (!normalizedText) {
    return "新会话";
  }

  return normalizedText.length > 24
    ? `${normalizedText.slice(0, 24)}...`
    : normalizedText;
};

const resolveConversationTitle = (chat) => {
  const explicitTitle = normalizeConversationText(
    chat?.title || chat?.name || chat?.previewTitle || chat?.firstMessagePreview
  );

  if (explicitTitle) {
    return buildConversationPreviewTitle(explicitTitle);
  }

  const firstUserMessage = Array.isArray(chat?.messages)
    ? chat.messages.find(
      (item) => item?.role === "user" && normalizeConversationText(item.content)
    )
    : null;

  return buildConversationPreviewTitle(firstUserMessage?.content);
};

const formatConversationDate = (timestamp) => {
  if (!timestamp) {
    return "未知日期";
  }

  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) {
    return "未知日期";
  }

  const now = new Date();
  const isCurrentYear = date.getFullYear() === now.getFullYear();

  return new Intl.DateTimeFormat("zh-CN", {
    ...(isCurrentYear ? {} : { year: "2-digit" }),
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(date);
};

const scheduleUserMessagesBuild = async () => {
  if (!showUserMessagesModal.value) {
    return;
  }

  if (userMessagesBuildTimer) {
    clearTimeout(userMessagesBuildTimer);
    userMessagesBuildTimer = null;
  }

  isPreparingUserMessages.value = true;
  userMessages.value = [];
  await nextTick();

  userMessagesBuildTimer = window.setTimeout(() => {
    buildUserMessages();
  }, 0);
};

const openUserMessages = async () => {
  if (showUserMessagesModal.value) {
    return;
  }

  showUserMessagesModal.value = true;
  // 直接触发构建，避免完全依赖 watch 触发导致加载状态卡住
  await scheduleUserMessagesBuild();
};

const handleOpenRag = async () => {
  showKnowledgeBaseModal.value = true;
  tempSelectedKB.value = selectedKnowledgeBase.value;
  await fetchKnowledgeBases();
};

const fetchKnowledgeBases = async () => {
  loadingKnowledgeBases.value = true;
  try {
    const data = await chatKnowledgeAPI.getBases();
    knowledgeBases.value = data.filter((kb) => kb.active !== false);
  } catch (error) {
    message.error("获取知识库列表失败");
  } finally {
    loadingKnowledgeBases.value = false;
  }
};

const confirmKnowledgeBase = () => {
  if (!tempSelectedKB.value) return;

  selectedKnowledgeBase.value = tempSelectedKB.value;
  ragEnabled.value = true;
  showKnowledgeBaseModal.value = false;

  emit("rag-change", {
    enabled: true,
    knowledgeBase: selectedKnowledgeBase.value,
  });

  message.success(`已连接知识库: ${selectedKnowledgeBase.value.name}`);
};

const disableRag = () => {
  ragEnabled.value = false;
  selectedKnowledgeBase.value = null;
  tempSelectedKB.value = null;

  emit("rag-change", {
    enabled: false,
    knowledgeBase: null,
  });

  message.info("已断开知识库连接");
};

const goToKnowledgeBase = () => {
  router.push("/knowledge-base");
  showKnowledgeBaseModal.value = false;
};

const formatDate = (dateStr) => {
  if (!dateStr) return "";
  const date = new Date(dateStr);
  return date.toLocaleDateString("zh-CN");
};

const fetchAvailableTools = async ({ force = false } = {}) => {
  if (isLoadingTools.value) {
    return;
  }

  if (!force && availableToolGroups.value.length > 0 && !toolsLoadError.value) {
    return;
  }

  isLoadingTools.value = true;
  toolsLoadError.value = "";

  try {
    availableToolGroups.value = await chatAPI.getAvailableTools();
  } catch (error) {
    console.error("加载工具列表失败:", error);
    toolsLoadError.value = error.message || "工具列表加载失败，请稍后重试";
    availableToolGroups.value = [];
  } finally {
    isLoadingTools.value = false;
  }
};

const handleOpenTools = async () => {
  showToolsModal.value = true;
  await fetchAvailableTools({ force: true });
};

const selectToolGroup = (groupKey) => {
  if (!groupKey) {
    return;
  }

  if (isGroupSelected(groupKey)) {
    selectedToolGroupKeys.value = selectedToolGroupKeys.value.filter(
      (key) => key !== groupKey
    );
  } else {
    selectedToolGroupKeys.value = [...selectedToolGroupKeys.value, groupKey];
  }
};

const retryLoadTools = async () => {
  await fetchAvailableTools({ force: true });
};

const clearSelectedTools = () => {
  if (selectedToolGroupKeys.value.length === 0) {
    return
  }

  selectedToolGroupKeys.value = []
};

watch(
  () => showToolsModal.value,
  async (isOpen) => {
    if (!isOpen) {
      return;
    }

    await fetchAvailableTools({ force: true });
  }
);

onMounted(() => {
  fetchAvailableTools().catch((error) => {
    console.error("初始化工具列表失败:", error);
  });
});

watch(
  () => [
    showUserMessagesModal.value,
    Array.isArray(props.currentMessages) ? props.currentMessages.length : 0,
  ],
  async ([isOpen]) => {
    if (!isOpen) {
      if (userMessagesBuildTimer) {
        clearTimeout(userMessagesBuildTimer);
        userMessagesBuildTimer = null;
      }
      isPreparingUserMessages.value = false;
      return;
    }

    await scheduleUserMessagesBuild();
  }
);

// 聊天记录展示：日期/关键词过滤已由服务端分页接口处理，这里只补展示字段并排序
// 排序基准为每条会话的 lastMessageAt（无则回退 createdAt），与后端 COALESCE 口径一致
const filteredChats = computed(() => {
  return props.chatHistory
    .map((chat) => {
      const date = resolveChatDisplayTime(chat);
      const displayTime = date
        ? date.toLocaleTimeString("zh-CN", {
          hour: "2-digit",
          minute: "2-digit",
          hour12: false,
        })
        : "未知时间";

      return {
        ...chat,
        timestamp: date ?? chat.timestamp ?? null,
        numericTimestamp: date ? date.getTime() : 0,
        displayTime,
        displayTitle: resolveConversationTitle(chat),
      };
    })
    .sort((a, b) => b.numericTimestamp - a.numericTimestamp); // 按数字时间戳倒序排列
});

const DAY_MS = 24 * 60 * 60 * 1000;

const startOfDay = (value) => {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
};

// 分组标签：今天 / 昨天 / N天前 / 更早
const groupLabelForDate = (value) => {
  const todayStart = startOfDay(new Date());
  const targetStart = startOfDay(value);
  if (todayStart == null || targetStart == null) return "未知日期";
  const diffDays = Math.round((todayStart - targetStart) / DAY_MS);
  if (diffDays <= 0) return "今天";
  if (diffDays === 1) return "昨天";
  if (diffDays < 7) return `${diffDays}天前`;
  return "更早";
};

// 按日期分组展示；启用日期筛选时保持平铺（筛选栏已展示具体日期）
const groupedChats = computed(() => {
  if (selectedDateFilter.value) {
    return [{ label: "", items: filteredChats.value }];
  }

  const groups = new Map();
  for (const chat of filteredChats.value) {
    const label = groupLabelForDate(chat.timestamp);
    if (!groups.has(label)) {
      groups.set(label, []);
    }
    groups.get(label).push(chat);
  }
  return Array.from(groups, ([label, items]) => ({ label, items }));
});

// 防抖标志
let isLoadingChat = false;
let isJumpingToMessage = false;

const handleLoadChat = (chatId) => {
  // 防抖处理
  if (isLoadingChat) {
    return;
  }
  isLoadingChat = true;

  emit("load-chat", chatId);

  // 短暂延迟后重置状态
  setTimeout(() => {
    isLoadingChat = false;
  }, 500);
};

// 跳转到指定消息
const handleJumpToMessage = (messageData) => {
  // 防抖处理
  if (isJumpingToMessage) {
    return;
  }
  isJumpingToMessage = true;

  // 添加消息索引信息，帮助更准确地定位。优先使用列表构建时保存的原始索引，避免重复内容或无时间戳时定位失败。
  const messageIndex = Number.isInteger(messageData.sourceMessageIndex)
    ? messageData.sourceMessageIndex
    : props.currentMessages.findIndex(
      (msg) =>
        msg.content === messageData.content &&
        msg.role === messageData.role &&
        (!messageData.timestamp ||
          Math.abs(
            new Date(msg.timestamp).getTime() -
            new Date(messageData.timestamp).getTime()
          ) < 1000)
    );

  const enhancedMessageData = {
    ...messageData,
    messageNo: messageData.messageNo || messageData.id,
    messageIndex: messageIndex >= 0 ? messageIndex : -1,
    totalMessages: props.currentMessages.length,
  };

  emit("jump-to-message", enhancedMessageData);
  showUserMessagesModal.value = false;

  // 短暂延迟后重置状态
  setTimeout(() => {
    isJumpingToMessage = false;
  }, 300);
};

const refreshRoles = async () => {
  const isSuccess = await chatAPI.refreshRoles();
  if (!isSuccess) {
    message.error("刷新失败");
    return;
  }
  emit("load-roles"); // 触发事件让父组件刷新角色和历史记录
  message.success("刷新成功");
};

// 删除对话
const handleDeleteChat = (chat) => {
  dialog.warning({
    title: "删除确认",
    content: "确定要删除这条对话记录吗？此操作不可恢复。",
    positiveText: "确定",
    negativeText: "取消",
    onPositiveClick: async () => {
      try {
        const result = await chatAPI.deleteChat(chat.id, "chat", props.selectedRoleName);
        if (result) {
          message.success("删除成功");

          // 记录删除的是否是当前对话
          const isDeletingCurrentChat = props.currentChatId === chat.id;

          if (isDeletingCurrentChat) {
            // 删除当前对话：只调用 load-latest-chat（内部会刷新历史记录）
            emit("load-latest-chat");
          } else {
            // 删除其他对话：本地移除该条目（列表已分页，不再延迟全量重拉历史）
            emit("remove-chat", chat.id);
          }
        } else {
          message.error("删除失败");
        }
      } catch (error) {
        console.error("删除对话失败:", error);
        message.error("删除出错");
      }
    },
  });
};

const handleContextMenu = (event, chat) => {
  // 暂时不实现右键菜单，使用悬浮按钮
};

// 新建会话
const createNewChat = () => {
  if (!props.selectedRole) {
    message.warning("请先选择一个角色");
    return;
  }
  // 创建新会话时，传递一个标识表示这是从历史记录面板创建的新会话
  // 而不是切换角色，这样就不会触发角色切换逻辑
  emit("create-new-chat", {
    role: props.selectedRole,
    fromHistory: true,
  });
};
const returnHomePage = () => {
  router.push("/");
};
const openFavoritesPage = () => {
  router.push("/chat-favorites");
};

const showCalendar = ref(false);
const timestamp = ref(null);
const selectedDateFilter = ref(null);

// 将 Date 格式化为日期选择器 value-format 同款的字符串（用于打开日历时定位月份）
const toValueString = (date) => {
  const pad = (n) => String(n).padStart(2, "0");
  return `${date.getFullYear()}.${pad(date.getMonth() + 1)}.${pad(
    date.getDate()
  )} 00:00:00`;
};

// 最新的有记录日期（按 lastMessageAt / createdAt 取最大值），用于打开日历时定位到最新月份
const latestAvailableDate = computed(() => {
  let max = null;
  for (const chat of props.chatHistory) {
    const date = new Date(chat?.lastMessageAt ?? chat?.createdAt ?? null);
    if (!Number.isNaN(date.getTime())) {
      if (!max || date.getTime() > max.getTime()) {
        max = date;
      }
    }
  }
  return max;
});

const selectCalendar = () => {
  const next = !showCalendar.value;
  showCalendar.value = next;
  // 打开时定位到已选日期或最新记录所在月份，避免总停留在当月
  if (next) {
    if (selectedDateFilter.value) {
      timestamp.value = selectedDateFilter.value;
    } else if (latestAvailableDate.value) {
      timestamp.value = toValueString(latestAvailableDate.value);
    }
  }
};

// 当前生效筛选（关键词 + 日期范围），统一提交给父组件驱动服务端分页查询
const currentFilter = () => ({
  keyword: searchInput.value.trim(),
  ...(selectedDateFilter.value
    ? dayRangeOf(toDateKey(selectedDateFilter.value))
    : { start: null, end: null }),
});

const onDateSelected = (value) => {
  if (!value) {
    return;
  }
  selectedDateFilter.value = value;
  timestamp.value = value;
  showCalendar.value = false;
  // 日期筛选走后端：以 [当日, 次日) 范围参数重新查第一页
  emit("apply-history-filter", currentFilter());
};

// 统一将 Date / 时间戳数字 / ISO 字符串 转成 'YYYY-MM-DD' 键
const toDateKey = (raw) => {
  if (raw == null) {
    return null;
  }
  const date = raw instanceof Date ? raw : new Date(raw);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  const pad = (n) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
};

// 有会话记录的日期集合：由父组件从 /dates 接口加载（列表分页后本地不再持有全量）
const availableDateSet = computed(() => new Set(props.historyDates));

const isDateDisabled = (date) => {
  return !availableDateSet.value.has(toDateKey(date));
};

// 给有记录的日期单元格加高亮 class（加粗 + 圆点提示）
const dateCellClass = (current) => {
  return availableDateSet.value.has(toDateKey(current)) ? "available-date" : "";
};

const clearDateFilter = () => {
  selectedDateFilter.value = null;
  timestamp.value = null;
  showCalendar.value = false;
  emit("apply-history-filter", currentFilter());
};

// —— 搜索状态机：关键词防抖 300ms，同时驱动标题过滤（分页接口 keyword）与消息内容搜索 ——
const searchInput = ref("");
const searchKeyword = ref("");
const searchHits = ref([]);
const isSearching = ref(false);
let searchDebounceTimer = null;

watch(searchInput, () => {
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer);
  searchDebounceTimer = setTimeout(() => {
    searchKeyword.value = searchInput.value.trim();
    emit("apply-history-filter", currentFilter());
    runMessageSearch();
  }, 300);
});

const runMessageSearch = async () => {
  const keyword = searchInput.value.trim();
  if (!keyword) {
    searchHits.value = [];
    return;
  }
  isSearching.value = true;
  try {
    const hits = await chatAPI.searchChatMessages(keyword, {
      type: "chat",
      role: props.selectedRoleName,
    });
    searchHits.value = hits.map((hit) => ({
      ...hit,
      segments: highlightKeyword(hit.snippet, keyword),
    }));
  } finally {
    isSearching.value = false;
  }
};

const clearSearch = () => {
  searchInput.value = "";
  searchKeyword.value = "";
  searchHits.value = [];
  emit("apply-history-filter", currentFilter());
};

// —— 搜索框按需展开：默认收起为工具行按钮，点击展开并自动聚焦；仅 Esc / × / 再点按钮手动收起 ——
const searchExpanded = ref(false);
const searchInputRef = ref(null);

// 收起即清空：避免收起后列表仍被不可见的关键词过滤
const collapseSearch = () => {
  clearSearch();
  searchExpanded.value = false;
};

const toggleSearch = async () => {
  if (searchExpanded.value) {
    collapseSearch();
    return;
  }
  searchExpanded.value = true;
  await nextTick();
  searchInputRef.value?.focus();
};

// —— 「更多」菜单：收藏/首页/刷新/删除模式等低频操作折叠，避免标题栏按钮过多挤压换行 ——
const showMoreMenu = ref(false);
const handleMoreAction = (action) => {
  showMoreMenu.value = false;
  action();
};

// 命中结果按会话分组（保持接口返回的时间倒序）
const groupedSearchHits = computed(() => {
  const groups = new Map();
  for (const hit of searchHits.value) {
    if (!groups.has(hit.sessionNo)) {
      groups.set(hit.sessionNo, {
        sessionNo: hit.sessionNo,
        sessionTitle: hit.sessionTitle,
        hits: [],
      });
    }
    groups.get(hit.sessionNo).hits.push(hit);
  }
  return Array.from(groups.values());
});

const handleOpenSearchHit = (hit) => {
  // 带上关键词：跳转后在消息体内做字符级高亮定位
  emit("open-chat-at-message", {
    chatId: hit.sessionNo,
    messageNo: hit.messageNo,
    keyword: searchInput.value.trim(),
  });
};

const formatHitTime = (value) => {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "" : date.toLocaleDateString("zh-CN");
};

// 切角色时静默清空筛选输入（父组件已重置 filter 并重载，这里只清 UI，不再 emit 防双重加载）
watch(
  () => props.selectedRoleName,
  () => {
    searchInput.value = "";
    searchKeyword.value = "";
    searchHits.value = [];
    selectedDateFilter.value = null;
    timestamp.value = null;
  }
);

// —— 会话列表无限滚动：哨兵进入视口即加载下一页 ——
const historySentinelRef = ref(null);
let historyObserver = null;

watch(historySentinelRef, (el) => {
  if (historyObserver) {
    historyObserver.disconnect();
    historyObserver = null;
  }
  if (!el) return;
  historyObserver = new IntersectionObserver(
    (entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        emit("load-more-history");
      }
    },
    { threshold: 0.1 }
  );
  historyObserver.observe(el);
});

onBeforeUnmount(() => {
  historyObserver?.disconnect();
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer);
});
</script>

<style lang="scss" scoped>
/* ===== RETRO OS SIDEBAR - 90s WINDOW STYLE ===== */

.chat-sidebar {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  /* Classic Windows 95 grey */
  background: var(--chat-panel, #c0c0c0);
  /* Chunky 3D raised border */
  border: 2px solid;
  border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
  border-radius: var(--chat-radius, 0);
  overflow: hidden;
  flex-shrink: 0;
  padding: 0;
  gap: 0;
  min-height: 0;
  box-sizing: border-box;
  font-family: var(--app-font-family, system-ui);

  .today-section,
  .messages-entry {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-height: 0;
    background: var(--chat-panel, #c0c0c0);
    border: none;
    box-shadow: var(--chat-shadow, none);
    border-radius: var(--chat-radius, 0);
    overflow: hidden;
    contain: layout paint;

    .section-header {
      flex-shrink: 0;
      padding: 3px 5px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      /* Window title bar */
      background: linear-gradient(90deg, var(--chat-titlebar-start, #000080) 0%, var(--chat-titlebar-end, #1084d0) 100%);
      border: 1px solid var(--chat-titlebar-hairline, transparent);

      .header-content {
        display: flex;
        align-items: center;
        gap: 6px;
        /* 标题侧不收缩：工具按钮增多时由按钮组吸收挤压，标题栏保持单行不被撑高 */
        flex-shrink: 0;

        .header-icon {
          width: 16px;
          height: 16px;
          color: var(--chat-titlebar-text-muted, #c0c0c0);
          display: flex;
          align-items: center;
          justify-content: center;

          svg {
            width: 16px;
            height: 16px;
          }
        }

        .section-title {
          font-weight: bold;
          font-size: 11px;
          color: var(--chat-titlebar-text, #ffffff);
          text-shadow: var(--chat-titlebar-text-shadow, 1px 1px 0 #000000);
          margin: 0;
          font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);
          letter-spacing: 0.3px;
          /* 空间不足时不换行（换行会把标题栏撑成两行高） */
          white-space: nowrap;
        }
      }

      .header-buttons {
        display: flex;
        align-items: center;
        gap: 3px;

        >* {
          display: flex;
          align-items: center;
        }
      }

      .new-chat-btn,
      .search-toggle-btn,
      .more-menu-btn,
      .calendar-select-btn {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 24px;
        height: 22px;
        border-radius: var(--chat-radius, 0);
        /* Raised button surface */
        background: var(--chat-panel, #c0c0c0);
        border: 2px solid;
        border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
        cursor: pointer;
        transition: none;

        &:hover {
          background: var(--chat-panel-hover, #d4d4d4);
        }

        &:active {
          border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
          padding: 1px 0 0 1px;
        }
      }

      .refresh-icon,
      .new-chat-icon,
      .more-menu-icon,
      .search-toggle-icon {
        width: 13px;
        height: 13px;
        color: var(--chat-text, #000000);
      }

      /* 搜索展开态：凹陷（pressed）+ 图标高亮，与日历展开态一致 */
      .search-toggle-btn.active {
        background: var(--chat-panel-hover, #d4d4d4);
        border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);

        .search-toggle-icon {
          color: var(--chat-accent, #000080);
        }
      }

      /* 更多菜单展开态：凹陷（pressed）+ 图标高亮 */
      .more-menu-btn.active {
        background: var(--chat-panel-hover, #d4d4d4);
        border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);

        .more-menu-icon {
          color: var(--chat-accent, #000080);
        }
      }

      .open-messages-btn {
        padding: 2px 8px;
        border-radius: var(--chat-radius, 0);
        border: 2px solid;
        border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
        background: var(--chat-panel, #c0c0c0);
        color: var(--chat-text, #000000);
        font-size: 10px;
        font-weight: bold;
        cursor: pointer;
        transition: none;
        font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);

        &:hover:not(:disabled) {
          background: var(--chat-panel-hover, #d4d4d4);
        }

        &:active:not(:disabled) {
          border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
          padding: 3px 7px 1px 9px;
        }

        &:disabled {
          color: var(--chat-text-muted, #808080);
          cursor: wait;
          opacity: 0.8;
        }
      }

      .message-badge {
        background: var(--chat-panel, #c0c0c0);
        color: var(--chat-text, #000000);
        padding: 2px 6px;
        border-radius: var(--chat-radius, 0);
        border: 1px solid;
        border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
        font-size: 10px;
        font-weight: bold;
        box-shadow: var(--chat-shadow, none);
        font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);
      }
    }

    .feature-cards {
      display: grid;
      grid-template-columns: 1fr;
      gap: 4px;
      margin: 4px 4px 8px;
      padding-right: 0;
      overflow-y: auto;
      min-height: 0;

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

        &:hover {
          background: var(--scrollbar-thumb-hover);
        }
      }
    }

    .feature-card {
      padding: 6px 8px;
      border-radius: var(--chat-radius, 0);
      background: var(--chat-panel, #c0c0c0);
      border: 2px solid;
      border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
      display: flex;
      flex-direction: column;
      gap: 4px;
      transition: none;
      box-shadow: var(--chat-shadow, none);
      content-visibility: auto;
      contain-intrinsic-size: 92px;
      cursor: default;

      &.active {
        background: var(--chat-accent, #000080);
        border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
        box-shadow: inset 1px 1px 0 var(--chat-bevel-frame-dark, #404040), inset -1px -1px 0 var(--chat-bevel-frame-light, #ffffff);

        .feature-title {
          color: var(--chat-text-on-accent, #ffffff);
        }

        .feature-desc {
          color: var(--chat-active-muted, #c0c0c0);
        }

        /* 选中卡片 hover：保持与 .active 常态完全一致（深色强调底 + 浅字 + 立体 bevel），
           只阻止背景回落到浅色 panel-hover，避免白/灰字落到浅底造成「白对白」对比度崩溃 */
        &.feature-card-clickable:hover {
          background: var(--chat-accent, #000080);
        }
      }

      &.feature-card-clickable {
        cursor: pointer;

        /* 仅未选中卡片用浅色 hover 底；选中卡片由 .active 分支自行处理 */
        &:not(.active):hover {
          background: var(--chat-panel-hover, #d4d4d4);
        }

        &:active {
          border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
          padding: 7px 7px 5px 9px;
        }
      }
    }

    .feature-title {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 11px;
      font-weight: bold;
      color: var(--chat-text, #000000);
      font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);
    }

    .rag-badge {
      font-size: 9px;
      font-weight: bold;
      padding: 1px 4px;
      background: var(--chat-rag, #800080);
      color: var(--chat-text-on-accent, #ffffff);
      border: 1px solid;
      border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
      border-radius: var(--chat-radius, 0);
    }

    .tool-badge {
      font-size: 9px;
      font-weight: bold;
      padding: 1px 4px;
      background: var(--chat-accent, #000080);
      color: var(--chat-text-on-accent, #ffffff);
      border: 1px solid;
      border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
      border-radius: var(--chat-radius, 0);
    }

    .feature-desc {
      margin: 0;
      font-size: 10px;
      color: var(--chat-text, #000000);
      line-height: 1.3;
      opacity: 0.75;
      font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);
    }

    .feature-card-footer {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 6px;
    }

    .feature-hint {
      font-size: 11px;
      line-height: 1.3;
      color: var(--chat-text-muted, #808080);
    }

    .feature-action {
      align-self: flex-start;
      padding: 3px 8px;
      border-radius: var(--chat-radius, 0);
      border: 2px solid;
      border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
      background: var(--chat-panel, #c0c0c0);
      color: var(--chat-text, #000000);
      font-size: 10px;
      font-weight: bold;
      cursor: pointer;
      transition: none;
      font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);

      &:hover {
        background: var(--chat-panel-hover, #d4d4d4);
      }

      &:active {
        border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
        padding: 4px 7px 2px 9px;
      }

      &.primary {
        background: var(--chat-panel, #c0c0c0);
        border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
        color: var(--chat-accent, #000080);

        &:hover {
          background: var(--chat-panel-hover, #d4d4d4);
        }
      }

      &.danger {
        background: var(--chat-panel, #c0c0c0);
        color: var(--chat-danger-text, #800000);

        &:hover {
          background: var(--chat-danger-tint, #ffcccc);
        }
      }

      &.subtle {
        background: var(--chat-panel, #c0c0c0);
        color: var(--chat-accent, #000080);

        &:hover {
          background: #ccccff;
        }
      }
    }

    .feature-action-compact {
      padding: 2px 6px;
      border-radius: var(--chat-radius, 0);
      font-size: 9px;
      line-height: 1.2;
      flex-shrink: 0;
    }

    .chat-list,
    .message-list {
      flex: 1;
      overflow-y: auto;
      padding: 4px;
      display: flex;
      flex-direction: column;
      gap: 2px;
      contain: content;
      overscroll-behavior: contain;
      -webkit-overflow-scrolling: touch;
      background: transparent;

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

        &:hover {
          background: var(--scrollbar-thumb-hover);
        }
      }

      .empty-placeholder {
        display: flex;
        flex: 1;
        min-height: 200px;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        padding: 30px 15px;
        text-align: center;

        .empty-icon {
          width: 32px;
          height: 32px;
          color: var(--chat-text-muted, #808080);
          margin-bottom: 8px;

          svg {
            width: 32px;
            height: 32px;
          }
        }

        .empty-text {
          color: var(--chat-text-muted, #808080);
          font-size: 11px;
          font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);
          margin: 0;
        }
      }

      .chat-group-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
        padding: 3px 6px;
        margin: 6px 2px 2px;
        background: linear-gradient(90deg, var(--chat-titlebar-start, #000080) 0%, var(--chat-titlebar-end, #1084d0) 100%);
        border: 1px solid var(--chat-titlebar-hairline, transparent);
        color: var(--chat-titlebar-text, #ffffff);
        font-size: 10px;
        font-weight: bold;
        letter-spacing: 0.3px;
        text-shadow: var(--chat-titlebar-text-shadow, 1px 1px 0 #000000);
        font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);

        .chat-group-count {
          flex-shrink: 0;
          font-size: 9px;
          font-weight: normal;
          color: var(--chat-titlebar-text-muted, #c0c0c0);
        }
      }

      .chat-item {
        position: relative;
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 6px 8px;
        border-radius: var(--chat-radius, 0);
        cursor: pointer;
        transition: none;
        /* Raised panel */
        background: var(--chat-panel, #c0c0c0);
        border: 2px solid;
        border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
        backdrop-filter: none;
        -webkit-backdrop-filter: none;
        contain: layout paint;
        content-visibility: auto;
        contain-intrinsic-size: 56px;

        &:hover {
          background: var(--chat-panel-hover, #d4d4d4);
        }

        &.active {
          background: var(--chat-accent, #000080);
          border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
          transform: none;
          box-shadow: inset 1px 1px 0 var(--chat-bevel-frame-dark, #404040), inset -1px -1px 0 var(--chat-bevel-frame-light, #ffffff);

          .chat-title {
            color: var(--chat-active-title, #e54242) !important;
            font-weight: bold;
          }

          .chat-time {
            color: var(--chat-active-muted, #c0c0c0);
          }

          .chat-role {
            color: var(--chat-favorite-on, #ffff00);
            background: var(--chat-accent, #000080);
            border-color: var(--chat-bevel-shadow, #808080);
          }

          .chat-avatar {
            color: var(--chat-text-on-accent, #ffffff);
            background: transparent;
          }
        }

        .chat-avatar {
          width: 28px;
          height: 28px;
          border-radius: var(--chat-radius, 0);
          background: transparent;
          border: 1px solid;
          border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
          display: flex;
          align-items: center;
          justify-content: center;
          color: var(--chat-text, #000000);
          flex-shrink: 0;
          transition: none;

          svg {
            width: 16px;
            height: 16px;
          }
        }

        .chat-content {
          flex: 1;
          min-width: 0;

          .chat-title-row {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 6px;
            min-width: 0;
            margin-bottom: 2px;
          }

          .chat-title {
            flex: 1;
            min-width: 0;
            font-size: 11px;
            font-weight: normal;
            color: var(--chat-text, #000000);
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);
          }

          .chat-time {
            flex-shrink: 0;
            font-size: 9px;
            color: var(--chat-text-muted, #808080);
            line-height: 1;
            white-space: nowrap;
            font-variant-numeric: tabular-nums;
          }

          .chat-meta {
            display: flex;
            align-items: center;
            gap: 4px;
            min-width: 0;
            min-height: 14px;
          }

          .chat-role {
            font-size: 9px;
            color: var(--chat-accent, #000080);
            background: var(--chat-panel, #c0c0c0);
            border: 1px solid;
            border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
            padding: 1px 4px;
            border-radius: var(--chat-radius, 0);
            font-weight: normal;
            max-width: 100px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            margin-left: 4px;
          }

          .chat-count {
            font-size: 9px;
            color: var(--chat-text-muted, #808080);
            background: var(--chat-panel, #c0c0c0);
            border: 1px solid;
            border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
            padding: 1px 4px;
            border-radius: var(--chat-radius, 0);
            font-weight: normal;
            white-space: nowrap;
            flex-shrink: 0;
          }
        }

        .delete-btn {
          /* 悬浮覆盖在条目右侧，不占布局空间，平时内容可占满整行宽度 */
          position: absolute;
          right: 8px;
          top: 50%;
          transform: translateY(-50%);
          display: flex;
          align-items: center;
          justify-content: center;
          width: 22px;
          height: 20px;
          border-radius: var(--chat-radius, 0);
          background: var(--chat-panel, #c0c0c0);
          border: 2px solid;
          border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
          cursor: pointer;
          transition: none;
          color: var(--chat-danger-text, #800000);
          opacity: 0;
          pointer-events: none;

          &:hover {
            background: var(--chat-danger-tint, #ffcccc);
          }

          &:active {
            border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
            padding: 3px 1px 1px 3px;
          }

          .delete-icon {
            width: 12px;
            height: 12px;
          }
        }
      }

      .message-item {
        display: flex;
        align-items: flex-start;
        gap: 8px;
        padding: 6px 8px;
        border-radius: var(--chat-radius, 0);
        cursor: pointer;
        transition: none;
        background: var(--chat-panel, #c0c0c0);
        border: 2px solid;
        border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
        backdrop-filter: none;
        -webkit-backdrop-filter: none;
        contain: layout paint;
        content-visibility: auto;
        contain-intrinsic-size: 52px;

        &:hover {
          background: var(--chat-panel-hover, #d4d4d4);
        }

        &:active {
          border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
          padding: 7px 7px 5px 9px;
        }

        .message-number {
          width: 20px;
          height: 18px;
          border-radius: var(--chat-radius, 0);
          background: var(--chat-panel, #c0c0c0);
          border: 1px solid;
          border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 10px;
          font-weight: bold;
          color: var(--chat-text, #000000);
          flex-shrink: 0;
          margin-top: 1px;
          font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);
        }

        .message-content {
          flex: 1;
          min-width: 0;

          .message-text {
            font-size: 11px;
            color: var(--chat-text, #000000);
            line-height: 1.35;
            margin-bottom: 2px;
            word-break: break-word;
            font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);
          }

          .message-time {
            font-size: 9px;
            color: var(--chat-text-muted, #808080);
          }
        }
      }
    }
  }

  .today-section {
    flex: 1 1 auto;
    margin-bottom: 0;
    border-bottom: 2px solid;
    border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
  }

  .messages-entry {
    flex: 0 0 auto;
    min-height: 160px;
    max-height: clamp(160px, 28vh, 240px);
  }
}

.chat-sidebar.show-delete-actions {
  .chat-list .chat-item {
    /* 删除模式下按钮常驻显示，给内容让出右侧空间避免被悬浮按钮遮挡 */
    .chat-content {
      padding-right: 26px;
    }

    .delete-btn {
      opacity: 0.9;
      pointer-events: auto;
    }
  }
}

.user-message-modal {
  .modal-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;
  }

  .modal-subtitle {
    font-size: 12px;
    color: #64748b;
  }

  .message-list {
    max-height: 60vh;
    overflow-y: auto;
    padding: 4px;
    display: flex;
    flex-direction: column;
    gap: 6px;

    .loading-placeholder {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      min-height: 120px;
      font-size: 13px;
      color: #64748b;
    }

    .empty-placeholder {
      display: flex;
      min-height: 160px;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 10px;
      padding: 24px;
      color: var(--chat-text-muted, #94a3b8);
      text-align: center;

      .empty-icon {
        width: 40px;
        height: 40px;
        color: currentColor;

        svg {
          display: block;
          width: 100%;
          height: 100%;
        }
      }

      .empty-text {
        margin: 0;
        font-size: 13px;
      }
    }

    &::-webkit-scrollbar {
      width: 4px;
    }

    &::-webkit-scrollbar-track {
      background: transparent;
    }

    &::-webkit-scrollbar-thumb {
      background: rgba(59, 130, 246, 0.3);
      border-radius: 2px;
    }

    .message-item {
      display: flex;
      gap: 12px;
      padding: 10px 12px;
      border-radius: 12px;
      background: rgba(255, 255, 255, 0.55);
      border: 1px solid rgba(148, 163, 184, 0.2);
      cursor: pointer;
      transition: background-color 0.2s ease, border-color 0.2s ease, transform 0.2s ease;

      &:hover {
        background: rgba(255, 255, 255, 0.85);
        border-color: rgba(59, 130, 246, 0.45);
        transform: translateY(-1px);
      }

      .message-number {
        min-width: 26px;
        height: 26px;
        border-radius: 8px;
        background: rgba(59, 130, 246, 0.15);
        color: #1d4ed8;
        font-size: 12px;
        font-weight: 600;
        display: flex;
        align-items: center;
        justify-content: center;
      }

      .message-content {
        flex: 1;
        min-width: 0;
      }

      .message-text {
        font-size: 13px;
        color: var(--chat-text, #1f2937);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .message-time {
        margin-top: 4px;
        font-size: 11px;
        color: var(--chat-text-muted, #94a3b8);
      }
    }
  }
}

.tools-modal {
  display: flex;
  flex-direction: column;
  gap: 12px;

  .tools-modal-header {
    display: flex;
    align-items: flex-start;
    gap: 12px;
  }

  .loading-state {
    min-height: 180px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 10px;
    color: #64748b;
  }

  .tool-tabs-layout {
    display: flex;
    flex-direction: column;
    gap: 12px;
    min-height: 0;
  }

  .tool-group-tabs {
    display: flex;
    align-items: center;
    gap: 8px;
    overflow-x: auto;
    padding: 2px 4px 8px;
    border-bottom: 1px solid rgba(148, 163, 184, 0.18);

    &::-webkit-scrollbar {
      height: 4px;
    }

    &::-webkit-scrollbar-track {
      background: transparent;
    }

    &::-webkit-scrollbar-thumb {
      background: rgba(14, 165, 233, 0.3);
      border-radius: 999px;
    }
  }

  .tool-group-tab {
    flex-shrink: 0;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    border: 1px solid rgba(148, 163, 184, 0.28);
    background: rgba(255, 255, 255, 0.88);
    color: #334155;
    border-radius: 999px;
    padding: 6px 10px;
    font-size: 11px;
    font-weight: 600;
    line-height: 1;
    cursor: pointer;
    transition: all 0.18s ease;

    &.active {
      border-color: rgba(14, 165, 233, 0.42);
      background: linear-gradient(135deg,
          rgba(14, 165, 233, 0.16),
          rgba(59, 130, 246, 0.1));
      color: #0c4a6e;
      box-shadow: 0 8px 18px rgba(14, 165, 233, 0.1);
    }
  }

  .tool-group-tab-dot {
    width: 6px;
    height: 6px;
    border-radius: 999px;
    background: rgba(148, 163, 184, 0.45);
    flex-shrink: 0;
    transition: background-color 0.18s ease, box-shadow 0.18s ease;
  }

  .tool-group-tab.enabled .tool-group-tab-dot {
    background: #0ea5e9;
    box-shadow: 0 0 0 3px rgba(14, 165, 233, 0.18);
  }

  .tool-group-tab-count {
    min-width: 18px;
    height: 18px;
    border-radius: 999px;
    background: rgba(255, 255, 255, 0.82);
    color: inherit;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    padding: 0 5px;
    font-size: 10px;
  }

  .tool-group-panel {
    display: flex;
    flex-direction: column;
    gap: 10px;
    min-height: 0;
    max-height: 48vh;
  }

  .tool-group-panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
    flex-shrink: 0;
  }

  .tool-group-panel-title {
    display: inline-flex;
    align-items: baseline;
    gap: 8px;
    min-width: 0;
  }

  .tool-group-panel-name {
    font-size: 13px;
    font-weight: 600;
    color: #0f172a;
    word-break: break-all;
  }

  .tool-group-panel-count {
    font-size: 11px;
    color: #64748b;
    white-space: nowrap;
  }

  .tool-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
    min-height: 0;
    overflow-y: auto;
    padding: 2px 4px;

    &::-webkit-scrollbar {
      width: 4px;
    }

    &::-webkit-scrollbar-track {
      background: transparent;
    }

    &::-webkit-scrollbar-thumb {
      background: rgba(14, 165, 233, 0.3);
      border-radius: 999px;
    }
  }

  .tool-option {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    padding: 10px 12px;
    border-radius: 12px;
    border: 1px solid rgba(148, 163, 184, 0.2);
    background: rgba(255, 255, 255, 0.82);
    transition: border-color 0.18s ease, background-color 0.18s ease,
      box-shadow 0.18s ease;

    &.selected {
      border-color: rgba(14, 165, 233, 0.45);
      background: linear-gradient(135deg,
          rgba(14, 165, 233, 0.12),
          rgba(59, 130, 246, 0.08));
    }
  }

  .tool-option-main {
    flex: 1;
    min-width: 0;
  }

  .tool-option-title-row {
    display: flex;
    align-items: center;
    justify-content: flex-start;
    flex-wrap: wrap;
    gap: 6px;
    margin-bottom: 4px;
    line-height: 1.2;
  }

  .tool-option-title {
    font-size: 13px;
    font-weight: 600;
    color: #0f172a;
  }

  .tool-option-key {
    font-size: 10px;
    color: #0369a1;
    background: rgba(14, 165, 233, 0.12);
    border-radius: 999px;
    padding: 2px 7px;
    white-space: nowrap;
  }

  .tool-option-desc {
    margin: 0;
    font-size: 11px;
    line-height: 1.4;
    color: var(--chat-text, #475569);
    white-space: pre-wrap;
    word-break: break-word;
  }

  .tool-option-checkbox {
    width: 16px;
    height: 16px;
    margin-top: 1px;
    accent-color: var(--chat-accent, #0ea5e9);
    flex-shrink: 0;
  }
}

.tools-empty-state {
  min-height: 180px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #64748b;
  text-align: center;

  p {
    margin: 0;
    font-size: 13px;
  }

  &.error {
    color: #b91c1c;
  }
}

.knowledge-base-modal {
  .loading-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 40px;
    gap: 12px;
    color: #64748b;
  }

  .empty-kb {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 40px;
    gap: 12px;
    color: #64748b;

    .empty-icon {
      width: 48px;
      height: 48px;
      color: #cbd5e1;

      svg {
        width: 100%;
        height: 100%;
      }
    }

    p {
      margin: 0;
      font-size: 14px;
    }
  }

  .kb-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
    max-height: 400px;
    overflow-y: auto;
    padding: 4px;

    &::-webkit-scrollbar {
      width: 4px;
    }

    &::-webkit-scrollbar-track {
      background: transparent;
    }

    &::-webkit-scrollbar-thumb {
      background: rgba(99, 102, 241, 0.3);
      border-radius: 2px;
    }
  }

  .kb-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px 14px;
    border-radius: 10px;
    background: rgba(255, 255, 255, 0.6);
    border: 1px solid rgba(148, 163, 184, 0.2);
    cursor: pointer;
    transition: background-color 0.2s ease, border-color 0.2s ease;

    &:hover {
      background: rgba(255, 255, 255, 0.85);
      border-color: rgba(99, 102, 241, 0.3);
    }

    &.selected {
      background: linear-gradient(135deg,
          rgba(99, 102, 241, 0.1) 0%,
          rgba(139, 92, 246, 0.1) 100%);
      border-color: rgba(99, 102, 241, 0.4);
    }

    .kb-icon {
      width: 36px;
      height: 36px;
      border-radius: 8px;
      background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
      display: flex;
      align-items: center;
      justify-content: center;
      color: white;
      flex-shrink: 0;

      svg {
        width: 20px;
        height: 20px;
      }
    }

    .kb-info {
      flex: 1;
      min-width: 0;
    }

    .kb-name {
      font-size: 14px;
      font-weight: 500;
      color: var(--chat-text, #1f2937);
      margin-bottom: 4px;
    }

    .kb-meta {
      display: flex;
      gap: 12px;
      font-size: 12px;
      color: var(--chat-text-muted, #6b7280);
    }

    .kb-check {
      width: 20px;
      height: 20px;
      border-radius: 50%;
      background: #6366f1;
      display: flex;
      align-items: center;
      justify-content: center;
      color: white;
      flex-shrink: 0;

      svg {
        width: 14px;
        height: 14px;
      }
    }
  }
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.tools-modal-footer {
  align-items: center;
  justify-content: space-between;
}

.tools-footer-hint {
  font-size: 12px;
  color: #64748b;
}

.modal-footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-height: 900px) {
  .chat-sidebar {
    padding: 6px;

    .today-section {
      margin-bottom: 4px;
    }

    .messages-entry {
      min-height: 168px;
      max-height: min(30vh, 220px);
    }

    .feature-cards {
      margin: 4px 10px 10px;
      gap: 6px;
    }

    .feature-card {
      padding: 9px 10px;
      gap: 5px;
    }

    .chat-list,
    .message-list {
      padding: 6px;
    }

    .chat-list .chat-item,
    .message-list .message-item {
      padding: 10px;
      gap: 10px;
    }
  }
}

.calendar-select-btn {
  &.active {
    background: var(--chat-panel-hover, #d4d4d4);
    border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);

    .refresh-icon {
      color: var(--chat-accent, #000080);
    }
  }
}

/* 「更多」折叠菜单（NPopover teleport 到 body，菜单项仍带本组件 scoped 标识，样式可命中） */
.sidebar-more-menu {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 132px;

  .more-menu-item {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
    padding: 5px 8px;
    font-size: 12px;
    text-align: left;
    color: var(--chat-text, #000000);
    background: transparent;
    border: 1px solid transparent;
    border-radius: var(--chat-radius, 0);
    cursor: pointer;
    white-space: nowrap;
    font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);

    /* 开启中的模式（如删除模式）给红色提示，hover 态优先级更高 */
    &.active {
      color: var(--chat-danger-text, #800000);
    }

    &:hover {
      background: var(--chat-accent, #000080);
      color: var(--chat-text-on-accent, #ffffff);
    }

    .more-menu-item-icon {
      width: 13px;
      height: 13px;
      flex-shrink: 0;
    }
  }
}

/* 会话搜索框（retro 凹陷输入框） */
.history-search-bar {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 4px 2px;

  .history-search-input {
    flex: 1;
    min-width: 0;
    height: 24px;
    padding: 0 6px;
    font-size: 11px;
    font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);
    color: var(--chat-text, #000000);
    background: var(--chat-input-bg, #ffffff);
    border: 2px solid;
    border-color: var(--chat-inset-shadow, #808080) var(--chat-inset-light, #ffffff) var(--chat-inset-light, #ffffff) var(--chat-inset-shadow, #808080);
    border-radius: var(--chat-radius, 0);
    outline: none;
    box-sizing: border-box;
  }

  .clear-search-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 20px;
    height: 20px;
    flex-shrink: 0;
    cursor: pointer;
    font-size: 13px;
    font-weight: bold;
    line-height: 1;
    color: var(--chat-danger-text, #800000);
    background: var(--chat-panel, #c0c0c0);
    border: 2px solid;
    border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
    border-radius: var(--chat-radius, 0);

    &:active {
      border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
    }
  }
}

/* 消息内容搜索结果区 */
.message-search-results {
  max-height: 40%;
  overflow-y: auto;
  margin: 2px 4px;
  border: 2px solid;
  border-color: var(--chat-inset-shadow, #808080) var(--chat-inset-light, #ffffff) var(--chat-inset-light, #ffffff) var(--chat-inset-shadow, #808080);
  border-radius: var(--chat-radius, 0);
  background: var(--chat-input-bg, #ffffff);

  .search-result-header {
    padding: 4px 6px;
    font-size: 10px;
    color: var(--chat-text-dim, #444444);
    border-bottom: 1px solid var(--chat-bevel-shadow, #808080);
  }

  .search-result-session {
    padding: 3px 6px 1px;
    font-size: 11px;
    font-weight: bold;
    color: var(--chat-text, #000000);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .search-hit-item {
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 3px 6px;
    cursor: pointer;
    font-size: 11px;
    color: var(--chat-text, #000000);

    &:hover {
      background: var(--chat-active-bg, #000080);
      color: var(--chat-text-on-accent, #ffffff);

      mark {
        color: #000000;
      }
    }

    .hit-role {
      flex-shrink: 0;
      font-weight: bold;
    }

    .hit-snippet {
      flex: 1;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .hit-time {
      flex-shrink: 0;
      opacity: 0.7;
      font-size: 10px;
    }

    mark {
      background: var(--chat-favorite-on, #ffff00);
      color: inherit;
      padding: 0;
    }
  }
}

/* 无限滚动加载更多哨兵 */
.history-load-more {
  text-align: center;
  padding: 6px;
  font-size: 10px;
  color: var(--chat-text-dim, #444444);
}

.date-filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  margin: 2px 4px 0;
  padding: 3px 6px;
  background: var(--chat-accent, #000080);
  border: 1px solid;
  border-color: var(--chat-bevel-frame-dark, #404040) var(--chat-bevel-frame-light, #dfdfdf) var(--chat-bevel-frame-light, #dfdfdf) var(--chat-bevel-frame-dark, #404040);
  color: var(--chat-text-on-accent, #ffffff);
  font-size: 10px;
  font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);
  .date-filter-text {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .clear-date-filter {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 18px;
    height: 16px;
    flex-shrink: 0;
    border-radius: var(--chat-radius, 0);
    background: var(--chat-panel, #c0c0c0);
    border: 2px solid;
    border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
    color: var(--chat-danger-text, #800000);
    font-size: 13px;
    font-weight: bold;
    line-height: 1;
    cursor: pointer;

    &:hover {
      background: var(--chat-danger-tint, #ffcccc);
    }

    &:active {
      border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
    }
  }
}

/* 响应式设计 */
</style>

<!-- 日历单元格高亮需全局样式：日期选择器渲染在 teleport 到 body 的 popover 内，scoped 样式无法命中 -->
<style lang="scss">
.n-date-picker .available-date {
  position: relative;

  .n-date-picker-cell__text {
    font-weight: 700;
    color: #2080f0;
  }

  &::after {
    content: "";
    position: absolute;
    left: 50%;
    bottom: 3px;
    transform: translateX(-50%);
    width: 4px;
    height: 4px;
    border-radius: 50%;
    background: #2080f0;
  }
}
</style>
