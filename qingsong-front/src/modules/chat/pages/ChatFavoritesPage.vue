<template>
  <div class="favorites-page">
    <div class="page-container">
      <!-- 页头：与知识库等页面一致的简洁范式 -->
      <header class="page-header">
        <div class="title-section">
          <h1 class="page-title">我的收藏</h1>
          <p class="page-subtitle">收藏的消息以副本保存，原对话删除后内容依然保留</p>
        </div>
        <div class="header-actions">
          <span v-if="!isInitialLoading" class="count-text">{{ favorites.length }}{{ hasMore ? '+' : '' }} 条</span>
          <n-button quaternary :loading="isRefreshing" @click="handleRefresh">
            <template #icon>
              <n-icon size="16">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M3 12a9 9 0 0 1 9-9 9 9 0 0 1 8.49 6" />
                  <polyline points="21 3 21 9 15 9" />
                  <path d="M21 12a9 9 0 0 1-9 9 9 9 0 0 1-8.49-6" />
                  <polyline points="3 21 3 15 9 15" />
                </svg>
              </n-icon>
            </template>
            刷新
          </n-button>
        </div>
      </header>

      <!-- 工具栏：吸附常驻 -->
      <div class="toolbar">
        <n-input
          v-model:value="searchInput"
          placeholder="搜索收藏内容或会话标题…"
          clearable
          class="search-input"
          @keydown.esc="searchInput = ''"
        >
          <template #prefix>
            <n-icon size="15">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="11" cy="11" r="7" />
                <line x1="21" y1="21" x2="16.65" y2="16.65" />
              </svg>
            </n-icon>
          </template>
        </n-input>
        <n-select
          v-model:value="roleFilter"
          :options="roleOptions"
          clearable
          placeholder="全部角色"
          class="role-select"
        />
        <span v-if="keyword && !isInitialLoading" class="match-count">匹配 {{ favorites.length }}{{ hasMore ? '+' : '' }} 条</span>
      </div>

      <!-- 加载骨架 -->
      <div v-if="isInitialLoading" class="card-list">
        <div v-for="i in 3" :key="i" class="favorite-card">
          <n-skeleton text style="width: 42%" />
          <n-skeleton text :repeat="3" style="margin-top: 10px" />
        </div>
      </div>

      <!-- 空态 -->
      <n-empty v-else-if="favorites.length === 0" class="empty-state" :description="hasActiveFilter ? '没有匹配的收藏，换个关键词或角色试试' : '还没有收藏，去聊天页点消息上的 ★ 收藏第一条吧'">
        <template #extra>
          <n-button v-if="!hasActiveFilter" type="primary" @click="goChat">前往 AI 对话</n-button>
          <n-button v-else @click="clearFilters">清空筛选</n-button>
        </template>
      </n-empty>

      <!-- 收藏卡片列表 -->
      <main v-else class="card-list">
        <article v-for="item in favorites" :key="item.messageNo" class="favorite-card">
          <header class="card-header">
            <svg viewBox="0 0 24 24" fill="currentColor" class="star-icon">
              <path d="M12 2l2.9 6.26 6.6.56-5 4.36 1.5 6.46L12 16.9 5.99 19.64l1.5-6.46-5-4.36 6.6-.56L12 2z" />
            </svg>
            <span class="role-name">{{ item.roleCode || '默认角色' }}</span>
            <span class="session-title" :title="item.sessionTitle">{{ item.sessionTitle || '未命名会话' }}</span>
            <span class="header-right">
              <span class="type-tag" :class="isUserMessage(item) ? 'type-user' : 'type-ai'">
                {{ isUserMessage(item) ? '我的消息' : 'AI 回答' }}
              </span>
              <span class="fav-time" :title="formatTime(item.createdAt)">{{ formatRelativeTime(item.createdAt) }}</span>
            </span>
          </header>

          <!-- 浏览态：markdown 渲染；搜索态：纯文本 + 关键词高亮 -->
          <div
            v-if="!keyword"
            class="card-content md-body"
            :class="{ clamped: isClamped(item) }"
            v-html="renderedContent(item)"
          ></div>
          <div v-else class="card-content plain-body" :class="{ clamped: isClamped(item) }">
            <template v-for="(seg, idx) in segmentsOf(item.content)" :key="idx">
              <mark v-if="seg.mark" class="hit-mark">{{ seg.text }}</mark>
              <template v-else>{{ seg.text }}</template>
            </template>
          </div>
          <button
            v-if="isLongContent(item)"
            type="button"
            class="expand-toggle"
            @click="toggleExpand(item.messageNo)"
          >
            {{ expandedMap[item.messageNo] ? '收起' : '展开全文' }}
          </button>

          <footer class="card-footer">
            <span v-if="item.chatModel" class="model-name" :title="`生成模型 ${item.chatModel}`">{{ item.chatModel }}</span>
            <div class="card-actions">
              <n-button
                size="small"
                quaternary
                :disabled="!canViewOriginal(item)"
                :title="viewOriginalTitle(item)"
                @click="viewOriginal(item)"
              >
                <template #icon>
                  <n-icon size="14">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M7 17L17 7M9 7h8v8" />
                    </svg>
                  </n-icon>
                </template>
                {{ canViewOriginal(item) ? '查看原文' : '原文已删除' }}
              </n-button>
              <n-button size="small" quaternary @click="copyContent(item)">
                <template #icon>
                  <n-icon size="14">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <rect x="9" y="9" width="12" height="12" rx="2" />
                      <path d="M5 15V5a2 2 0 0 1 2-2h10" />
                    </svg>
                  </n-icon>
                </template>
                复制
              </n-button>
              <n-popconfirm @positive-click="removeFavorite(item)">
                <template #trigger>
                  <n-button size="small" quaternary>
                    <template #icon>
                      <n-icon size="14">
                        <svg viewBox="0 0 24 24" fill="currentColor">
                          <path d="M12 2l2.9 6.26 6.6.56-5 4.36 1.5 6.46L12 16.9 5.99 19.64l1.5-6.46-5-4.36 6.6-.56L12 2z" />
                        </svg>
                      </n-icon>
                    </template>
                    取消收藏
                  </n-button>
                </template>
                确定取消收藏这条消息吗？
              </n-popconfirm>
            </div>
          </footer>
        </article>

        <div v-if="hasMore" ref="sentinelRef" class="load-sentinel">
          <n-spin v-if="isLoadingMore" size="small" />
          <span v-else>向下滚动加载更多</span>
        </div>
        <p v-else class="list-end">没有更多了</p>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NEmpty, NIcon, NInput, NPopconfirm, NSelect, NSkeleton, NSpin, useMessage } from 'naive-ui'
import { chatAPI, favoriteAPI } from '@/modules/chat/services'
import { useFavoriteMessages } from '../composables/index.js'
import { highlightKeyword } from '@/modules/chat/utils/messageSearch.js'
import { renderMarkdownLite } from '@/modules/chat/utils/markdownLite.js'

const router = useRouter()
const message = useMessage()
// 聊天页星标单例：本页取消收藏后同步，避免回聊天页星标仍亮
const favoriteMessages = useFavoriteMessages()

// —— 筛选状态：关键词防抖 300ms，角色下拉即时生效，均触发首页重载 ——
const searchInput = ref('')
const keyword = ref('')
const roleFilter = ref(null)
let searchDebounceTimer = null

watch(searchInput, () => {
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  searchDebounceTimer = setTimeout(() => {
    keyword.value = searchInput.value.trim()
    reload()
  }, 300)
})

watch(roleFilter, () => reload())

const hasActiveFilter = computed(() => Boolean(keyword.value || roleFilter.value))

// —— 角色列表：筛选选项 + roleCode(角色名)→roleId 映射（跳转原文的路由参数是角色 id）——
const roleList = ref([])
const roleOptions = computed(() =>
  roleList.value
    .map(item => ({ label: item?.value?.name || item?.name || '', value: item?.value?.name || item?.name || '' }))
    .filter(option => option.value)
)

const roleIdOf = roleCode =>
  roleList.value.find(item => String(item?.value?.name || item?.name || '') === String(roleCode))?.value?.id

const loadRoleOptions = async () => {
  try {
    roleList.value = await chatAPI.getRoles()
  } catch (error) {
    console.error('加载角色选项失败:', error)
  }
}

// —— 游标分页列表：before/beforeId 取上一页末条，哨兵进入视口加载下一页 ——
const favorites = ref([])
const hasMore = ref(false)
const isInitialLoading = ref(false)
const isLoadingMore = ref(false)
const isRefreshing = ref(false)
const expandedMap = ref({})
// markdown 渲染缓存：同一 messageNo 只渲染一次（内容快照不变）
const renderCache = new Map()

const renderedContent = item => {
  const key = String(item.messageNo)
  if (!renderCache.has(key)) {
    renderCache.set(key, renderMarkdownLite(item.content))
  }
  return renderCache.get(key)
}

const cursorOf = item => {
  if (!item) return null
  const before = formatLocalDateTime(item.createdAt)
  if (!before || item.favoriteId == null) return null
  return { before, beforeId: item.favoriteId }
}

// Date/时间字符串 → 后端 @DateTimeFormat(ISO.DATE_TIME) 可解析的本地 ISO（yyyy-MM-ddTHH:mm:ss）
const formatLocalDateTime = value => {
  if (!value) return null
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return null
  const pad = n => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const fetchPage = async (cursor = null) => {
  return favoriteAPI.getFavoritePage({
    keyword: keyword.value || undefined,
    roleCode: roleFilter.value || undefined,
    before: cursor?.before,
    beforeId: cursor?.beforeId,
    limit: 15
  })
}

const reload = async () => {
  isInitialLoading.value = true
  try {
    const page = await fetchPage()
    favorites.value = page.list
    hasMore.value = page.hasMore
    expandedMap.value = {}
  } catch (error) {
    message.error(error?.message || '获取收藏列表失败')
  } finally {
    isInitialLoading.value = false
  }
}

const loadMore = async () => {
  if (isLoadingMore.value || !hasMore.value || favorites.value.length === 0) return
  const cursor = cursorOf(favorites.value[favorites.value.length - 1])
  if (!cursor) {
    hasMore.value = false
    return
  }
  isLoadingMore.value = true
  try {
    const page = await fetchPage(cursor)
    const seen = new Set(favorites.value.map(item => item.messageNo))
    favorites.value = [...favorites.value, ...page.list.filter(item => !seen.has(item.messageNo))]
    hasMore.value = page.hasMore
  } catch (error) {
    message.error(error?.message || '加载更多失败')
  } finally {
    isLoadingMore.value = false
  }
}

const handleRefresh = async () => {
  isRefreshing.value = true
  try {
    renderCache.clear()
    await reload()
  } finally {
    isRefreshing.value = false
  }
}

// —— 无限滚动哨兵 ——
const sentinelRef = ref(null)
let sentinelObserver = null

watch(sentinelRef, el => {
  if (sentinelObserver) {
    sentinelObserver.disconnect()
    sentinelObserver = null
  }
  if (!el) return
  sentinelObserver = new IntersectionObserver(
    entries => {
      if (entries.some(entry => entry.isIntersecting)) {
        loadMore()
      }
    },
    { threshold: 0.1 }
  )
  sentinelObserver.observe(el)
})

// —— 卡片交互 ——
const isUserMessage = item => String(item.messageType || '').toUpperCase() === 'USER'

const LONG_CONTENT_THRESHOLD = 260
const isLongContent = item => (item.content || '').length > LONG_CONTENT_THRESHOLD
const isClamped = item => isLongContent(item) && !expandedMap.value[item.messageNo]

const toggleExpand = messageNo => {
  expandedMap.value = { ...expandedMap.value, [messageNo]: !expandedMap.value[messageNo] }
}

// 关键词高亮：复用消息搜索的 highlightKeyword
const segmentsOf = text => highlightKeyword(text || '', keyword.value)

const formatTime = value => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const pad = n => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

// 相对时间：今天 HH:mm / 昨天 HH:mm / MM-DD / yyyy-MM-DD
const formatRelativeTime = value => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const now = new Date()
  const pad = n => String(n).padStart(2, '0')
  const hm = `${pad(date.getHours())}:${pad(date.getMinutes())}`
  const startOf = d => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
  const dayDiff = Math.round((startOf(now) - startOf(date)) / 86400000)
  if (dayDiff === 0) return `今天 ${hm}`
  if (dayDiff === 1) return `昨天 ${hm}`
  if (date.getFullYear() === now.getFullYear()) return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${hm}`
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

// 会话与消息都在才能跳转定位（消息可能被重试物理删除，此时跳过去也找不到目标）
const canViewOriginal = item => Boolean(item.sessionAlive && item.messageAlive !== false)

const viewOriginalTitle = item => {
  if (!item.sessionAlive) return '原会话已删除，无法跳转'
  if (item.messageAlive === false) return '原消息已删除，无法定位'
  return '跳回原对话定位到该消息'
}

const viewOriginal = item => {
  if (!canViewOriginal(item)) return
  // 路由 /chat/:roleId/:chatId 的 roleId 是角色 id（非角色名），按收藏的 roleCode 反查
  const roleId = roleIdOf(item.roleCode)
  if (!roleId) {
    message.warning('角色不存在或已删除，无法跳转原文')
    return
  }
  router.push({
    path: `/chat/${encodeURIComponent(roleId)}/${encodeURIComponent(item.sessionNo)}`,
    query: { msg: item.messageNo }
  })
}

const copyContent = async item => {
  try {
    await navigator.clipboard.writeText(item.content || '')
    message.success('已复制')
  } catch (error) {
    console.error('复制失败:', error)
    message.error('复制失败，请手动选择文本复制')
  }
}

const removeFavorite = async item => {
  try {
    await favoriteAPI.unfavorite(item.messageNo)
    favorites.value = favorites.value.filter(entry => entry.messageNo !== item.messageNo)
    renderCache.delete(String(item.messageNo))
    // 同步聊天页星标单例：回到聊天页对应消息不再显示已收藏
    favoriteMessages.unmarkFavorited(item.messageNo)
    message.success('已取消收藏')
  } catch (error) {
    message.error(error?.message || '取消收藏失败')
  }
}

const clearFilters = () => {
  searchInput.value = ''
  keyword.value = ''
  roleFilter.value = null
  reload()
}

const goChat = () => router.push('/chat')

onMounted(() => {
  loadRoleOptions()
  reload()
})

onBeforeUnmount(() => {
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  if (sentinelObserver) sentinelObserver.disconnect()
})
</script>

<style scoped lang="scss">
// 与首页 app-card 同基调：纯色背景 + 柔和面板卡片，无装饰层
.favorites-page {
  min-height: 100vh;
  background: var(--app-background, #f8fafc);
  color: var(--app-text-primary, #374151);
  font-family: var(--app-font-family, system-ui, -apple-system, sans-serif);
  padding: 24px 20px 40px;
  box-sizing: border-box;
}

.page-container {
  max-width: 1400px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

// —— 页头 ——
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: var(--app-text-primary, #374151);
}

.page-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--app-text-secondary, #6b7280);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.count-text {
  font-size: 13px;
  color: var(--app-text-secondary, #6b7280);
}

// —— 工具栏：吸附常驻 ——
.toolbar {
  position: sticky;
  top: 8px;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 14px;
  border: 1px solid var(--app-border-color, rgba(0, 0, 0, 0.06));
  background: rgba(var(--app-panel-background-rgb, 255, 255, 255), 0.95);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}

.search-input {
  flex: 1;
  min-width: 0;
}

.role-select {
  width: 160px;
  flex-shrink: 0;
}

.match-count {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  color: #d97706;
  white-space: nowrap;
}

// —— 卡片列表：多列网格，宽屏 2-3 列；哨兵/结束语跨满整行 ——
.card-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 14px;
  align-items: start;
}

.favorite-card {
  border: 1px solid var(--app-border-color, rgba(0, 0, 0, 0.06));
  border-radius: 16px;
  background: rgba(var(--app-panel-background-rgb, 255, 255, 255), 0.95);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: box-shadow 0.2s ease, transform 0.2s ease;

  &:hover {
    box-shadow: 0 8px 20px rgba(0, 0, 0, 0.12);
    transform: translateY(-1px);
  }
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.star-icon {
  width: 15px;
  height: 15px;
  color: #f59e0b;
  flex-shrink: 0;
}

.role-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--app-text-primary, #374151);
  flex-shrink: 0;
}

.session-title {
  font-size: 13px;
  color: var(--app-text-secondary, #6b7280);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
  flex-shrink: 0;
}

.type-tag {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}

.type-ai {
  background: rgba(245, 158, 11, 0.12);
  color: #d97706;
}

.type-user {
  background: var(--app-bg-secondary, #f3f4f6);
  color: var(--app-text-secondary, #6b7280);
}

.fav-time {
  font-size: 12px;
  color: var(--app-text-secondary, #6b7280);
  white-space: nowrap;
}

.card-content {
  font-size: 14px;
  line-height: 1.75;
  color: var(--app-text-primary, #374151);
  word-break: break-word;
}

.plain-body {
  white-space: pre-wrap;

  &.clamped {
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 8;
    overflow: hidden;
  }
}

.md-body {
  &.clamped {
    max-height: 260px;
    overflow: hidden;
    position: relative;

    &::after {
      content: '';
      position: absolute;
      left: 0;
      right: 0;
      bottom: 0;
      height: 48px;
      background: linear-gradient(180deg, transparent, rgba(var(--app-panel-background-rgb, 255, 255, 255), 0.95));
      pointer-events: none;
    }
  }

  :deep(h1), :deep(h2), :deep(h3), :deep(h4) {
    margin: 12px 0 6px;
    line-height: 1.4;
    color: var(--app-text-primary, #374151);
  }

  :deep(h1) { font-size: 18px; }
  :deep(h2) { font-size: 16px; }
  :deep(h3), :deep(h4) { font-size: 15px; }

  :deep(p) {
    margin: 6px 0;
  }

  :deep(ul), :deep(ol) {
    margin: 6px 0;
    padding-left: 22px;
  }

  :deep(li) {
    margin: 3px 0;
  }

  :deep(code) {
    padding: 1px 5px;
    border-radius: 5px;
    background: var(--app-bg-secondary, #f3f4f6);
    font-size: 12.5px;
    font-family: var(--code-font-family, Consolas, Monaco, monospace);
  }

  :deep(pre) {
    margin: 8px 0;
    padding: 10px 12px;
    border-radius: 10px;
    background: var(--app-bg-secondary, #f3f4f6);
    border: 1px solid var(--app-border-color, rgba(0, 0, 0, 0.06));
    overflow-x: auto;

    code {
      padding: 0;
      background: transparent;
    }
  }

  :deep(blockquote) {
    margin: 8px 0;
    padding: 2px 12px;
    border-left: 3px solid rgba(245, 158, 11, 0.45);
    color: var(--app-text-secondary, #6b7280);
  }

  :deep(table) {
    border-collapse: collapse;
    margin: 8px 0;
    font-size: 13px;
  }

  :deep(th), :deep(td) {
    border: 1px solid var(--app-border-color, rgba(0, 0, 0, 0.06));
    padding: 5px 10px;
  }

  :deep(th) {
    background: var(--app-bg-secondary, #f3f4f6);
  }

  :deep(a) {
    color: #d97706;
  }

  :deep(img) {
    max-width: 100%;
    border-radius: 8px;
  }

  :deep(hr) {
    border: none;
    border-top: 1px solid var(--app-border-color, rgba(0, 0, 0, 0.06));
    margin: 12px 0;
  }
}

.hit-mark {
  background: rgba(245, 158, 11, 0.35);
  color: inherit;
  border-radius: 3px;
  padding: 0 1px;
}

.expand-toggle {
  align-self: flex-start;
  border: none;
  background: transparent;
  padding: 0;
  font-size: 12px;
  cursor: pointer;
  color: #d97706;

  &:hover {
    text-decoration: underline;
  }
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.model-name {
  font-size: 11px;
  color: var(--app-text-secondary, #6b7280);
  font-family: var(--code-font-family, Consolas, Monaco, monospace);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 40%;
}

.card-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
}

.load-sentinel {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 48px;
  font-size: 12px;
  color: var(--app-text-secondary, #6b7280);
}

.list-end {
  grid-column: 1 / -1;
  font-size: 12px;
  color: var(--app-text-secondary, #6b7280);
  text-align: center;
  margin: 0;
  padding: 8px 0;
}

.empty-state {
  margin-top: 96px;
}

@media (max-width: 640px) {
  .favorites-page {
    padding: 16px 12px 28px;
  }

  .toolbar {
    flex-wrap: wrap;
  }

  .role-select {
    width: 100%;
  }

  .session-title {
    display: none;
  }

  .model-name {
    max-width: 100%;
  }
}
</style>
