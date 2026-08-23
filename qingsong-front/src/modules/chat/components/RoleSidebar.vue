<template>
  <div
    class="role-drawer"
    :class="{ expanded: !collapsed, mobile: !isDesktop }"
    @mouseenter="cancelAutoCollapse"
    @mouseleave="scheduleAutoCollapse"
  >
    <div
      class="rail"
      :class="{ clickable: collapsed }"
      :title="collapsed ? '点击展开角色面板' : ''"
      @click="handleRailClick"
    >
      <button
        class="trigger"
        :title="collapsed ? '展开角色面板' : '收起角色面板'"
        @click.stop="togglePanel"
      >
        <ChevronRightIcon v-if="collapsed" class="icon" />
        <ChevronLeftIcon v-else class="icon" />
      </button>
      <div class="rail-meta">
        <span>角色</span>
        <small>{{ previewModels.length ? '点击展开' : '快速入口' }}</small>
      </div>
      <div v-if="previewModels.length" class="rail-avatars">
        <template v-for="(group, groupIndex) in previewGroups" :key="group.key">
          <span
            v-if="groupIndex > 0"
            class="avatar-divider"
            :class="{ 'recent-separator': group.key === 'ranked' || group.key === 'recent' }"
            aria-hidden="true"
          ></span>
          <div class="avatar-group">
            <button
              v-for="model in group.models"
              :key="model.id"
              class="preview-avatar"
              :class="{ selected: model.selected }"
              :style="model.avatarStyle"
              :title="model.name"
              @click.stop="startNewChat(model.role)"
              @mouseenter="showPreviewTip(model, group.key, $event)"
              @mouseleave="hidePreviewTip"
            >
              <span class="avatar-text">{{ model.avatarText }}</span>
              <span v-if="model.selected" class="active-dot"></span>
            </button>
          </div>
        </template>
      </div>
      <div v-else class="rail-empty">角色</div>
      <div class="rail-summary">
        <span class="summary-count">{{ railCount }}</span>
      </div>
    </div>

    <div class="panel">
      <div class="surface">
        <div class="header">
          <div class="title">
            <h3>角色列表</h3>
            <p>{{ panelSubtitle }}</p>
          </div>
          <div class="actions">
            <button
              class="filter-btn"
              :class="{ active: showOnlyFavorites }"
              title="只看收藏角色"
              @click="toggleFavoriteFilter"
            >
              <StarIcon class="icon" />
            </button>
            <button
              class="filter-btn"
              :class="{ active: isSortMode, disabled: !canSort && !isSortMode }"
              :disabled="!canSort && !isSortMode"
              title="拖拽排序"
              @click="toggleSortMode"
            >
              <ArrowsUpDownIcon class="icon" />
            </button>
          </div>
          <button
            class="filter-btn"
            :title="collapsed ? '展开角色面板' : '收起角色面板'"
            @click="togglePanel"
          >
            <ChevronLeftIcon v-if="!collapsed" class="icon" />
            <ChevronRightIcon v-else class="icon" />
          </button>
        </div>

        <div class="search-wrap">
          <div class="search-box">
            <MagnifyingGlassIcon class="icon muted" />
            <input
              v-model="searchInput"
              class="search-input"
              placeholder="搜索角色名或描述"
              @focus="isSearchFocused = true"
              @blur="isSearchFocused = false"
              @compositionstart="isSearchComposing = true"
              @compositionend="isSearchComposing = false"
            />
            <button
              v-if="searchInput"
              class="clear-btn"
              title="清空搜索"
              @click="searchInput = ''"
            >
              <XMarkIcon class="icon muted" />
            </button>
          </div>
        </div>

        <div class="list custom-scrollbar">
          <section v-for="section in visibleSections" :key="section.key" class="section">
            <div class="section-head">
              <span>{{ section.label }}</span>
              <small>{{ section.models.length }}</small>
            </div>

            <!-- 榜单分区：只读展示排名 + 使用次数 -->
            <div v-if="section.ranked" class="rank-list">
              <div
                v-for="(model, index) in section.models"
                :key="model.id"
                class="rank-card"
                :class="{ selected: model.selected }"
                @click="startNewChat(model.role)"
              >
                <span class="rank-no" :class="`rank-${index + 1}`">{{ index + 1 }}</span>
                <div class="avatar" :style="model.avatarStyle">
                  <span class="avatar-text">{{ model.avatarText }}</span>
                </div>
                <div class="content">
                  <div class="main-line">
                    <span class="name" :title="`${model.name}（今日 ${model.usageCount} 次）`">{{
                      model.name
                    }}</span>
                    <span v-if="model.selected" class="badge">当前</span>
                  </div>
                </div>
                <span class="rank-count" :title="`今日使用 ${model.usageCount} 次`">{{
                  model.usageCount
                }}</span>
              </div>
            </div>

            <draggable
              v-else
              :model-value="section.models"
              item-key="id"
              class="grid"
              :class="{ sorting: isSortMode }"
              :disabled="!isSortMode"
              :handle="dragHandle"
              ghost-class="ghost"
              chosen-class="chosen"
              drag-class="drag"
              @update:model-value="updateSectionModels(section.key, $event)"
              @start="handleDragStart"
              @end="onDragEnd"
            >
              <template #item="{ element }">
                <div
                  v-memo="[element.id, element.selected, element.favorite, isSortMode]"
                  class="card"
                  :class="{ selected: element.selected }"
                  @click="startNewChat(element.role)"
                >
                  <div class="avatar" :style="element.avatarStyle">
                    <span class="avatar-text">{{ element.avatarText }}</span>
                  </div>
                  <div class="content">
                    <div class="main-line">
                      <span class="name" :title="`${element.name} (ID: ${element.id})`">{{
                        element.name
                      }}</span>
                      <span v-if="element.selected" class="badge">当前</span>
                    </div>
                    <p
                      v-if="element.hasDescription"
                      class="desc"
                      :title="element.previewText"
                    >
                      {{ element.previewText }}
                    </p>
                  </div>
                  <div class="ops">
                    <button
                      class="favorite-btn"
                      :class="{ on: element.favorite }"
                      :title="element.favorite ? '取消收藏' : '收藏角色'"
                      @click.stop="toggleFavorite(element.role)"
                    >
                      <StarIcon class="mini-icon" />
                    </button>
                    <div v-if="isSortMode" class="drag-handle" title="拖拽排序">
                      <Bars3Icon class="mini-icon" />
                    </div>
                  </div>
                </div>
              </template>
            </draggable>
          </section>

          <div v-if="visibleCount === 0" class="empty">{{ emptyTitle }}</div>
        </div>
      </div>
    </div>

    <div
      v-if="previewTip"
      class="preview-tip"
      :class="{ mobile: !isDesktop }"
      :style="previewTipPos"
    >
      <div class="pt-head">
        <span class="pt-name">{{ previewTip.name }}</span>
        <span v-if="previewTip.favorite" class="pt-fav" title="已收藏">★</span>
        <span v-if="previewTip.selected" class="pt-badge">当前</span>
        <span v-else-if="previewTip.key === 'recent'" class="pt-badge recent">最近</span>
        <span v-else-if="previewTip.key === 'ranked'" class="pt-badge recent">总榜</span>
      </div>
      <p v-if="previewTip.hasDescription" class="pt-desc">{{ previewTip.previewText }}</p>
      <p v-else class="pt-desc muted">暂无描述（ID: {{ previewTip.id }}）</p>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { refDebounced } from "@vueuse/core";
import {
  ArrowsUpDownIcon,
  Bars3Icon,
  ChevronLeftIcon,
  ChevronRightIcon,
  MagnifyingGlassIcon,
  StarIcon,
  XMarkIcon,
} from "@heroicons/vue/24/outline";
import draggable from "vuedraggable";
import { useMessage } from "naive-ui";
import { roleAPI } from "../services/index.js";

const props = defineProps({
  roles: { type: Array, required: true, default: () => [] },
  stats: { type: Object, default: () => ({ total: [], today: [] }) },
  selectedRole: { type: Object, default: null },
  selectedRoleName: { type: String, default: "" },
  initialCollapsed: { type: Boolean, default: true },
});
const emit = defineEmits(["start-new-chat", "roles-updated", "panel-toggle"]);

const MEDIA = "(min-width: 769px)";
const PREVIEW_ROLE_LIMIT = 8;
const isDesktop = ref(
  typeof window === "undefined" ? true : window.matchMedia(MEDIA).matches
);
const collapsed = ref(props.initialCollapsed);
const showOnlyFavorites = ref(false);
const isSortMode = ref(false);
const isSearchFocused = ref(false);
const isSearchComposing = ref(false);
const localRoles = ref([]);
const searchInput = ref("");
const searchQuery = refDebounced(searchInput, 120);
const message = useMessage();
const avatarCache = new Map();
let dragSnapshot = [];
let clickLock = false;
let mediaQuery = null;
let collapseTimer = null;
let previewShowTimer = null;
let previewHideTimer = null;
let pendingTipTarget = null;
const previewTip = ref(null);
const previewTipPos = ref({});

const descOf = (role) =>
  String(
    role?.value?.desc || role?.value?.description || role?.desc || role?.description || ""
  )
    .replace(/\s+/g, " ")
    .trim();
const avatarTextOf = (name) =>
  String(name || "?")
    .trim()
    .charAt(0)
    .toUpperCase() || "?";
const sortNum = (role) => Number.parseInt(role?.value?.sort, 10) || 0;
const selectedRoleId = computed(() =>
  String(props.selectedRole?.id ?? props.selectedRole?.value?.id ?? "")
);

const normalizeRole = (role) => {
  const raw = role?.value || {};
  const name = role?.name || raw.name || "未命名角色";
  const id = String(raw.id ?? role?.id ?? name);
  const description = descOf(role);
  return {
    ...role,
    name,
    value: {
      ...raw,
      name,
      id,
      sort:
        raw.sort === null || raw.sort === undefined || raw.sort === ""
          ? "0"
          : String(raw.sort),
      favor:
        raw.favor === null || raw.favor === undefined || raw.favor === ""
          ? "0"
          : String(raw.favor),
    },
    meta: {
      description,
      previewText: description || `ID ${id}`,
      avatarText: avatarTextOf(name),
      searchText: `${name} ${description}`.toLowerCase(),
    },
  };
};

const cloneRoles = (roles) =>
  (roles || []).map((role) =>
    normalizeRole({ ...role, value: { ...(role?.value || {}) } })
  );
const orderedRoles = (favoriteRoles, regularRoles) => [
  ...cloneRoles(favoriteRoles),
  ...cloneRoles(regularRoles),
];
const syncSort = (roles) =>
  roles.map((role, index) =>
    normalizeRole({ ...role, value: { ...role.value, sort: String((index + 1) * 100) } })
  );

const setCollapsed = (value) => {
  if (collapsed.value === value) return;
  collapsed.value = value;
  emit("panel-toggle", value);
};
const syncViewport = (desktop) => {
  if (desktop) setCollapsed(Boolean(props.initialCollapsed));
  else {
    // 移动端默认收起为图标栏（rail-only），避免整列角色列表挤占聊天区；
    // 用户点击图标栏上的触发按钮可展开为下拉浮层。
    setCollapsed(true);
    isSortMode.value = false;
  }
};

watch(
  () => props.roles,
  (roles) => {
    avatarCache.clear();
    localRoles.value = cloneRoles(roles);
  },
  { immediate: true }
);
watch(
  () => props.initialCollapsed,
  (value) => {
    if (isDesktop.value) setCollapsed(Boolean(value));
  }
);
watch(searchQuery, (query) => {
  if (query.trim() && isSortMode.value) isSortMode.value = false;
});

const avatarStyleOf = (role) => {
  const key = `${role.value.id}-${role.value.avatar || role.value.icon || ""}`;
  if (avatarCache.has(key)) return avatarCache.get(key);
  if (role.value.avatar || role.value.icon) {
    const url = role.value.avatar || role.value.icon;
    if (String(url).startsWith("http") || String(url).startsWith("/")) {
      const style = {
        backgroundImage: `url(${url})`,
        backgroundSize: "cover",
        backgroundPosition: "center",
      };
      avatarCache.set(key, style);
      return style;
    }
  }
  const gradients = [
    "linear-gradient(135deg,#2563eb,#1d4ed8)",
    "linear-gradient(135deg,#0f766e,#14b8a6)",
    "linear-gradient(135deg,#ea580c,#f59e0b)",
    "linear-gradient(135deg,#7c3aed,#a855f7)",
    "linear-gradient(135deg,#dc2626,#f43f5e)",
    "linear-gradient(135deg,#0891b2,#38bdf8)",
  ];
  let hash = 0;
  for (const char of String(role.name || ""))
    hash = char.charCodeAt(0) + ((hash << 5) - hash);
  const style = { background: gradients[Math.abs(hash) % gradients.length] };
  avatarCache.set(key, style);
  return style;
};

const models = computed(() =>
  localRoles.value.map((role) => ({
    id: role.value.id,
    name: role.name,
    role,
    favorite: role.value.favor === "1",
    selected: selectedRoleId.value
      ? role.value.id === selectedRoleId.value
      : role.name === props.selectedRoleName ||
        role.value.name === props.selectedRoleName,
    hasDescription: Boolean(role.meta.description),
    previewText: role.meta.previewText,
    avatarText: role.meta.avatarText,
    avatarStyle: avatarStyleOf(role),
  }))
);
const canSort = computed(() => !searchQuery.value.trim());
const dragHandle = computed(() => (isSortMode.value ? ".drag-handle" : undefined));

// 后端统计 → { roleId: count }
const statsCountMap = (list) => {
  const map = new Map();
  (Array.isArray(list) ? list : []).forEach((item) =>
    map.set(String(item?.id), Number(item?.count) || 0)
  );
  return map;
};
const rankModels = (list) => {
  const countMap = statsCountMap(list);
  return models.value
    .map((model) => {
      const count = countMap.get(model.id);
      return count ? { ...model, usageCount: count } : null;
    })
    .filter(Boolean)
    .sort((a, b) => b.usageCount - a.usageCount);
};
const totalRankedModels = computed(() => rankModels(props.stats?.total));
const todayRankedModels = computed(() => rankModels(props.stats?.today));
const filteredModels = computed(() => {
  let list = models.value;
  if (searchQuery.value.trim()) {
    const query = searchQuery.value.trim().toLowerCase();
    list = list.filter((model) => model.role.meta.searchText.includes(query));
  }
  if (showOnlyFavorites.value) list = list.filter((model) => model.favorite);
  return list;
});
const favoriteModels = computed(() =>
  filteredModels.value.filter((model) => model.favorite)
);
const regularModels = computed(() =>
  filteredModels.value.filter((model) => !model.favorite)
);
const visibleSections = computed(() => {
  const sections = [];
  // 今日使用榜：搜索状态下隐藏，避免干扰查找
  if (!searchQuery.value.trim()) {
    sections.push({
      key: "today",
      label: "今日使用榜",
      models: todayRankedModels.value,
      ranked: true,
    });
  }
  sections.push({ key: "favorite", label: "收藏角色", models: favoriteModels.value });
  sections.push({
    key: "regular",
    label: showOnlyFavorites.value ? "其他角色" : "全部角色",
    models: regularModels.value,
  });
  return sections.filter((section) => section.models.length > 0);
});
// 最近对话角色（后端统计 lastRole），仅在现有角色中存在时返回
const lastRoleModel = computed(() => {
  const item = props.stats?.lastRole;
  if (!item?.id) return null;
  return models.value.find((model) => model.id === String(item.id)) || null;
});

const previewGroups = computed(() => {
  const selectedModel = models.value.find((model) => model.selected);

  // 总预览名额固定为 PREVIEW_ROLE_LIMIT：selected 优先占用 1 个名额，其余名额依次分配给「最近对话角色」与总榜 Top-N
  let budget = PREVIEW_ROLE_LIMIT;
  if (selectedModel) budget -= 1;

  const groups = [];
  if (selectedModel) {
    groups.push({ key: "selected", models: [selectedModel] });
  }

  const shownIds = new Set(groups.flatMap((g) => g.models.map((m) => m.id)));

  const lastRole = lastRoleModel.value;
  if (lastRole && !shownIds.has(lastRole.id) && budget > 0) {
    groups.push({ key: "recent", models: [lastRole] });
    budget -= 1;
    shownIds.add(lastRole.id);
  }

  // 主数据源：总榜（后端统计），排除当前角色与最近对话角色
  const rankedModels = totalRankedModels.value
    .filter((model) => !shownIds.has(model.id))
    .slice(0, budget);
  if (rankedModels.length) {
    groups.push({ key: "ranked", models: rankedModels });
  }

  return groups.filter((group) => group.models.length > 0);
});
const previewModels = computed(() =>
  previewGroups.value.flatMap((group) => group.models)
);
const railCount = computed(() => models.value.length);
const visibleCount = computed(() => filteredModels.value.length);
const emptyTitle = computed(() =>
  searchQuery.value.trim()
    ? "没有找到匹配角色"
    : showOnlyFavorites.value
    ? "暂无收藏角色"
    : "暂无角色"
);
const panelSubtitle = computed(() =>
  searchQuery.value.trim()
    ? `匹配 ${visibleCount.value} 个角色`
    : showOnlyFavorites.value
    ? `收藏 ${visibleCount.value} 个角色`
    : `共 ${localRoles.value.length} 个角色`
);
const keepPanelOpen = computed(
  () =>
    isSearchFocused.value ||
    isSearchComposing.value ||
    Boolean(searchInput.value.trim()) ||
    isSortMode.value
);

const updateSectionModels = (key, nextModels) => {
  if (!canSort.value) return;
  if (key === "favorite")
    localRoles.value = orderedRoles(
      nextModels.map((model) => model.role),
      localRoles.value.filter((role) => role.value.favor !== "1")
    );
  else
    localRoles.value = orderedRoles(
      localRoles.value.filter((role) => role.value.favor === "1"),
      nextModels.map((model) => model.role)
    );
};

const togglePanel = () => setCollapsed(!collapsed.value);
const handleRailClick = () => {
  if (collapsed.value) togglePanel();
};
const cancelAutoCollapse = () => {
  if (collapseTimer) {
    clearTimeout(collapseTimer);
    collapseTimer = null;
  }
};
const scheduleAutoCollapse = () => {
  if (!isDesktop.value || collapsed.value) return;
  cancelAutoCollapse();
  collapseTimer = setTimeout(() => {
    if (!keepPanelOpen.value) setCollapsed(true);
  }, 120);
};
const toggleFavoriteFilter = () => {
  showOnlyFavorites.value = !showOnlyFavorites.value;
  if (isSortMode.value) isSortMode.value = false;
};
const toggleSortMode = () => {
  if (!canSort.value && !isSortMode.value)
    return message.info("搜索状态下不支持排序，先清空搜索再拖拽。");
  isSortMode.value = !isSortMode.value;
};

const startNewChat = (role) => {
  if (isSortMode.value || clickLock) return;
  clickLock = true;
  emit("start-new-chat", role);
  if (!isDesktop.value) setCollapsed(true);
  setTimeout(() => {
    clickLock = false;
  }, 300);
};

const showPreviewTip = (model, key, event) => {
  if (previewHideTimer) {
    clearTimeout(previewHideTimer);
    previewHideTimer = null;
  }
  const rect = event.currentTarget.getBoundingClientRect();
  pendingTipTarget = { model, key, rect };
  if (previewShowTimer) return;
  previewShowTimer = setTimeout(applyPreviewTip, 180);
};
const applyPreviewTip = () => {
  previewShowTimer = null;
  if (!pendingTipTarget) return;
  const { model, key, rect } = pendingTipTarget;
  pendingTipTarget = null;
  previewTip.value = { ...model, key };
  const TIP_WIDTH = 220;
  if (rect.right + TIP_WIDTH + 8 > window.innerWidth) {
    previewTipPos.value = {
      top: `${rect.top}px`,
      right: `${window.innerWidth - rect.left + 8}px`,
    };
  } else {
    previewTipPos.value = { top: `${rect.top}px`, left: `${rect.right + 8}px` };
  }
};
const hidePreviewTip = () => {
  if (previewShowTimer) {
    clearTimeout(previewShowTimer);
    previewShowTimer = null;
  }
  pendingTipTarget = null;
  if (previewHideTimer) clearTimeout(previewHideTimer);
  previewHideTimer = setTimeout(() => {
    previewHideTimer = null;
    previewTip.value = null;
  }, 200);
};

const handleDragStart = () => {
  dragSnapshot = cloneRoles(localRoles.value);
};
const onDragEnd = async (event) => {
  if (
    event.oldIndex === undefined ||
    event.newIndex === undefined ||
    event.oldIndex === event.newIndex
  )
    return void (dragSnapshot = []);
  const loading = message.loading("正在更新角色排序...", { duration: 0 });
  try {
    const ids = localRoles.value.map((role) => String(role.value.id));
    const result = await roleAPI.updateRoleOrder(ids, localRoles.value);
    if (!result || result.ok === 0) throw new Error(result?.msg || "排序更新失败");
    localRoles.value = syncSort(localRoles.value);
    emit("roles-updated", cloneRoles(localRoles.value));
    message.success("角色排序已更新");
  } catch (error) {
    localRoles.value = cloneRoles(dragSnapshot);
    message.error(error?.message || "排序更新失败，请稍后重试");
    console.error("Update role order failed:", error);
  } finally {
    loading.destroy();
    dragSnapshot = [];
  }
};

const toggleFavorite = async (role) => {
  const next = role.value.favor === "1" ? "0" : "1";
  const loading = message.loading("正在更新收藏状态...", { duration: 0 });
  try {
    const result = await roleAPI.favorRole(String(role.value.id), next);
    if (!result || result.ok === 0) throw new Error(result?.msg || "操作失败");
    const updated = localRoles.value.map((item) =>
      String(item.value.id) === String(role.value.id)
        ? normalizeRole({ ...item, value: { ...item.value, favor: next } })
        : item
    );
    localRoles.value = orderedRoles(
      updated
        .filter((item) => item.value.favor === "1")
        .sort((a, b) => sortNum(a) - sortNum(b)),
      updated
        .filter((item) => item.value.favor !== "1")
        .sort((a, b) => sortNum(a) - sortNum(b))
    );
    emit("roles-updated", cloneRoles(localRoles.value));
    message.success(next === "1" ? "已添加到收藏" : "已取消收藏");
  } catch (error) {
    message.error(error?.message || "操作失败，请稍后重试");
    console.error("Toggle favorite failed:", error);
  } finally {
    loading.destroy();
  }
};

const onMediaChange = (event) => {
  isDesktop.value = event.matches;
  syncViewport(event.matches);
};
onMounted(() => {
  if (typeof window === "undefined") return;
  mediaQuery = window.matchMedia(MEDIA);
  isDesktop.value = mediaQuery.matches;
  syncViewport(mediaQuery.matches);
  mediaQuery.addEventListener("change", onMediaChange);
});
onBeforeUnmount(() => {
  cancelAutoCollapse();
  if (previewShowTimer) clearTimeout(previewShowTimer);
  if (previewHideTimer) clearTimeout(previewHideTimer);
  mediaQuery?.removeEventListener("change", onMediaChange);
});
</script>

<style lang="scss" scoped>
/* ===== RETRO OS ROLE SIDEBAR - 90s WINDOW STYLE ===== */

.role-drawer {
  --rail: 60px;
  --panel: 260px;
  position: relative;
  width: 100%;
  height: 100%;
  pointer-events: none;
  font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);
}

.rail,
.panel {
  pointer-events: auto;
}

/* Expanded states */
.role-drawer.expanded .panel {
  opacity: 1;
  transform: translateX(0) scale(1);
  pointer-events: auto;
  z-index: 30;
}

.role-drawer.expanded .rail {
  border-color: var(--chat-accent, #000080);
  box-shadow: inset -1px -1px 0 var(--chat-bevel-shadow, #808080), inset 1px 1px 0 var(--chat-bevel-light, #ffffff);
  z-index: 31;
}

/* Rail sidebar */
.rail {
  width: var(--rail);
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 4px 4px;
  border: 2px solid;
  border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
  border-radius: var(--chat-radius, 0);
  background: var(--chat-panel, #c0c0c0);
  transition: none;

  &.clickable {
    cursor: pointer;

    &:hover {
      background: var(--chat-panel-hover, #d4d4d4);
    }
  }
}

.trigger,
.filter-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 2px solid;
  border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
  background: var(--chat-panel, #c0c0c0);
  color: var(--chat-text, #000000);
  cursor: pointer;
  transition: none;
  border-radius: var(--chat-radius, 0);

  &:hover {
    background: var(--chat-panel-hover, #d4d4d4);
    color: var(--chat-accent, #000080);
  }

  &:active {
    border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
    padding: 1px 0 0 1px;
  }

  .icon {
    width: 16px;
    height: 16px;
  }
}

.trigger {
  width: 32px;
  height: 28px;
}

.rail-meta,
.rail-summary {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.rail-meta span,
.rail-summary span {
  font-size: 11px;
  font-weight: bold;
  color: var(--chat-text, #000000);
}

.rail-meta small {
  font-size: 9px;
  color: var(--chat-text-muted, #808080);
}

.rail-avatars,
.avatar-group {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
}

.rail-avatars {
  gap: 2px;
}

.avatar-group {
  gap: 2px;
}

.avatar-divider {
  width: 32px;
  height: 2px;
  background: #808080;
}
.avatar-divider.recent-separator {
  width: 24px;
  height: 2px;
  margin: 4px 0;
  background: transparent;
  border-top: 2px dashed #888;
}

.preview-avatar,
.rail-empty,
.avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--chat-text-on-accent, #fff);
  border-radius: var(--chat-radius, 0);
  font-family: var(--chat-font-family, 'MS Sans Serif', 'Segoe UI', Tahoma, sans-serif);
}

.preview-avatar,
.rail-empty {
  width: 32px;
  height: 30px;
}

.preview-avatar {
  position: relative;
  border: 2px solid;
  border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
  cursor: pointer;
  box-shadow: inset -1px -1px 0 var(--chat-bevel-frame-dark, #404040), inset 1px 1px 0 var(--chat-bevel-frame-light, #dfdfdf);
  transition: none;

  &:hover {
    outline: 1px dotted var(--chat-accent, #000080);
  }
}

.role-drawer:not(.expanded) .preview-avatar:not(.selected) {
  opacity: 0.7;
  filter: grayscale(0.3);
}

.role-drawer:not(.expanded) .preview-avatar:not(.selected):hover {
  opacity: 0.9;
  filter: none;
}

.preview-avatar.selected {
  outline: 2px solid var(--chat-accent, #000080);
  outline-offset: 0;
  border-color: var(--chat-accent, #000080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-accent, #000080);
  opacity: 1;
  filter: none;
}

.active-dot {
  position: absolute;
  right: -2px;
  bottom: -2px;
  width: 10px;
  height: 10px;
  border: 1px solid #ffffff;
  border-radius: var(--chat-radius, 0);
  background: var(--chat-success, #008000);
}

.rail-empty {
  border: 2px solid;
  border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
  background: var(--chat-panel, #c0c0c0);
  color: var(--chat-text-muted, #808080);
  font-size: 10px;
  font-weight: bold;
}

.rail-summary {
  margin-top: auto;
  gap: 2px;
}

.summary-count,
.badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  white-space: nowrap;
  line-height: 1;
  border-radius: var(--chat-radius, 0);
  font-size: 9px;
  font-weight: bold;
  font-family: var(--chat-font-family, 'MS Sans Serif', 'Segoe UI', Tahoma, sans-serif);
  border: 2px solid;
  border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
}

.summary-count {
  min-width: 20px;
  height: 18px;
  padding: 0 5px;
  background: var(--chat-panel, #c0c0c0);
  color: var(--chat-accent, #000080);
}

/* Panel */
.panel {
  position: absolute;
  top: 0;
  left: 8px;
  width: calc(var(--panel) + var(--rail));
  height: 100%;
  padding-left: calc(var(--rail) - 6px);
  opacity: 0;
  transform: translateX(-8px) scale(0.985);
  pointer-events: none;
  transition: opacity 0.05s ease, transform 0.05s ease;
}

.surface {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 2px solid;
  border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
  border-radius: var(--chat-radius, 0);
  background: var(--chat-panel, #c0c0c0);
  box-shadow: 2px 2px 0 var(--chat-inset-shadow, #808080), -1px -1px 0 var(--chat-inset-light, #ffffff);
}

.header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 3px 6px;
  border-bottom: 2px solid;
  border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
  background: linear-gradient(90deg, var(--chat-titlebar-start, #000080) 0%, var(--chat-titlebar-end, #1084d0) 100%);

  .title h3 {
    margin: 0;
    font-size: 12px;
    font-weight: bold;
    color: var(--chat-titlebar-text, #ffffff);
    text-shadow: var(--chat-titlebar-text-shadow, 1px 1px 0 #000000);
    font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);
  }

  .title p {
    margin: 1px 0 0;
    font-size: 9px;
    color: var(--chat-titlebar-text-muted, #c0c0c0);
    font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);
  }

  .actions {
    display: flex;
    gap: 3px;
    margin-left: auto;
  }

  .filter-btn {
    width: 26px;
    height: 22px;
    border-radius: var(--chat-radius, 0);

    &.active {
      background: var(--chat-accent, #000080);
      color: var(--chat-text-on-accent, #ffffff);
      border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
    }

    &.disabled,
    &:disabled {
      opacity: 0.45;
      cursor: not-allowed;
    }
  }
}

.search-wrap {
  padding: 4px 6px;
  border-bottom: 2px solid;
  border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
}

.search-box {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 2px 3px;
  border: 2px solid;
  border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
  border-radius: var(--chat-radius, 0);
  background: #ffffff;
  background: var(--chat-surface, #ffffff);
  overflow: hidden;

  &:focus-within {
    outline: 2px dotted var(--chat-text, #000000);
    outline-offset: -2px;
  }

  .icon {
    width: 14px;
    height: 14px;
    flex: 0 0 14px;
    color: var(--chat-text-muted, #808080);
  }
}

.search-input {
  flex: 1;
  min-width: 0;
  width: 100%;
  border: none;
  outline: none;
  background: transparent;
  color: var(--chat-text, #000000);
  font-size: 11px;
  font-family: var(--chat-font-family, 'MS Sans Serif', 'Segoe UI', Tahoma, sans-serif);

  &::placeholder {
    color: var(--chat-text-muted, #808080);
  }
}

.clear-btn {
  width: 16px;
  height: 16px;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 16px;
  color: var(--chat-text-muted, #808080);

  .icon {
    width: 12px;
    height: 12px;
  }

  &:hover {
    color: #800000;
  }
}

.muted {
  color: var(--chat-text-muted, #808080);
}

.list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 4px;
  background: var(--chat-panel, #c0c0c0);

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
}

.section + .section {
  margin-top: 6px;
  border-top: 1px solid #808080;
  padding-top: 4px;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
  padding: 2px 4px;
  font-size: 11px;
  font-weight: bold;
  color: var(--chat-text-on-accent, #ffffff);
  background: #808080;
  border: 1px solid;
  border-color: var(--chat-bevel-frame-dark, #404040) var(--chat-bevel-frame-light, #dfdfdf) var(--chat-bevel-frame-light, #dfdfdf) var(--chat-bevel-frame-dark, #404040);
  font-family: var(--chat-font-family, 'MS Sans Serif', 'Segoe UI', Tahoma, sans-serif);

  small {
    font-size: 9px;
    color: var(--chat-panel, #c0c0c0);
  }
}

.grid {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

/* 榜单分区（今日使用榜） */
.rank-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.rank-card {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 6px;
  border: 2px solid;
  border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
  border-radius: var(--chat-radius, 0);
  background: var(--chat-panel, #c0c0c0);
  cursor: pointer;
  transition: none;

  &:hover {
    background: var(--chat-panel-hover, #d4d4d4);
  }

  &:active {
    border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
    padding: 5px 5px 3px 7px;
  }

  &.selected {
    border-color: var(--chat-accent, #000080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-accent, #000080);
    background: var(--chat-accent, #000080);

    .name {
      color: var(--chat-text-on-accent, #ffffff);
    }

    .rank-count {
      color: var(--chat-panel, #c0c0c0);
    }
  }
}

.rank-no {
  flex: 0 0 18px;
  width: 18px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: bold;
  color: var(--chat-text, #000000);
  background: var(--chat-panel, #c0c0c0);
  border: 2px solid;
  border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
  border-radius: var(--chat-radius, 0);
  font-family: var(--chat-font-family, 'MS Sans Serif', 'Segoe UI', Tahoma, sans-serif);

  &.rank-1 {
    color: var(--chat-text-on-accent, #ffffff);
    background: var(--chat-accent, #000080);
  }

  &.rank-2 {
    background: var(--chat-panel-hover, #d4d4d4);
  }

  &.rank-3 {
    background: #e8e8e8;
  }
}

.rank-count {
  margin-left: auto;
  flex-shrink: 0;
  font-size: 10px;
  font-weight: bold;
  color: var(--chat-accent, #000080);
  white-space: nowrap;
  font-family: var(--chat-font-family, 'MS Sans Serif', 'Segoe UI', Tahoma, sans-serif);
}

.card {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 6px;
  border: 2px solid;
  border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
  border-radius: var(--chat-radius, 0);
  background: var(--chat-panel, #c0c0c0);
  cursor: pointer;
  transition: none;

  &:hover {
    background: var(--chat-panel-hover, #d4d4d4);
  }

  &:active {
    border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
    padding: 5px 5px 3px 7px;
  }

  &.selected {
    border-color: var(--chat-accent, #000080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-accent, #000080);
    background: var(--chat-accent, #000080);
    box-shadow: inset 1px 1px 0 var(--chat-bevel-frame-dark, #404040), inset -1px -1px 0 var(--chat-bevel-frame-light, #ffffff);

    .name {
      color: var(--chat-text-on-accent, #ffffff);
    }

    .desc {
      color: var(--chat-panel, #c0c0c0);
    }

    .badge {
      background: var(--chat-panel, #c0c0c0);
      color: var(--chat-accent, #000080);
      border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
    }

    .avatar {
      border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
    }
  }
}

.avatar {
  width: 32px;
  height: 30px;
  flex-shrink: 0;
  border-radius: var(--chat-radius, 0);
  border: 2px solid;
  border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
  box-shadow: inset -1px -1px 0 var(--chat-bevel-frame-dark, #404040), inset 1px 1px 0 var(--chat-bevel-frame-light, #dfdfdf);
}

.avatar-text {
  font-size: 13px;
  font-weight: bold;
  color: var(--chat-text-on-accent, #ffffff);
  text-shadow: 1px 1px 0 rgba(0, 0, 0, 0.4);
  font-family: var(--chat-font-family, 'MS Sans Serif', 'Segoe UI', Tahoma, sans-serif);
}

.content {
  flex: 1;
  min-width: 0;
}

.main-line {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.name {
  display: inline-block;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
  font-weight: bold;
  color: var(--chat-text, #000000);
  font-family: var(--chat-font-family, 'MS Sans Serif', 'Segoe UI', Tahoma, sans-serif);
}

.badge {
  min-width: 24px;
  height: 16px;
  padding: 0 4px;
  background: var(--chat-accent, #000080);
  color: var(--chat-text-on-accent, #ffffff);
  font-size: 9px;
  border: 2px solid;
  border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
  border-radius: var(--chat-radius, 0);
}

.desc {
  margin: 2px 0 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 9px;
  line-height: 1.3;
  color: var(--chat-text, #000000);
  opacity: 0.75;
  font-family: var(--chat-font-family, 'MS Sans Serif', 'Segoe UI', Tahoma, sans-serif);
}

.ops {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-left: auto;
}

.favorite-btn,
.drag-handle {
  width: 22px;
  height: 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--chat-radius, 0);
  border: 2px solid;
  border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
  background: var(--chat-panel, #c0c0c0);
  cursor: pointer;
  transition: none;
}

.favorite-btn {
  color: var(--chat-text-muted, #808080);

  &:hover {
    background: var(--chat-favorite-tint, #ffffcc);
    color: var(--chat-favorite, #808000);
  }

  &.on {
    color: var(--chat-favorite, #808000);
    background: var(--chat-favorite-on, #ffff00);
    border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);

    .mini-icon {
      fill: currentColor;
    }
  }
}

.drag-handle {
  background: var(--chat-panel, #c0c0c0);
  color: var(--chat-text-muted, #808080);
  cursor: grab;

  &:hover {
    background: var(--chat-panel-hover, #d4d4d4);
  }
}

.mini-icon {
  width: 12px;
  height: 12px;
}

.ghost {
  opacity: 0.4;
}

.chosen {
  transform: rotate(0.5deg);
}

.drag {
  opacity: 0.85;
}

.empty {
  padding: 16px;
  border: 2px dashed #808080;
  border-radius: var(--chat-radius, 0);
  background: var(--chat-panel, #c0c0c0);
  text-align: center;
  font-size: 12px;
  font-weight: bold;
  color: var(--chat-text-muted, #808080);
  font-family: var(--chat-font-family, 'MS Sans Serif', 'Segoe UI', Tahoma, sans-serif);
}

.preview-tip {
  position: fixed;
  z-index: 60;
  max-width: 220px;
  padding: 4px 6px;
  pointer-events: none;
  border: 2px solid;
  border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
  border-radius: var(--chat-radius, 0);
  background: var(--chat-tooltip, #ffffe1);
  color: var(--chat-text, #000000);
  box-shadow: 2px 2px 0 var(--chat-inset-shadow, #808080);
  font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);

  .pt-head {
    display: flex;
    align-items: center;
    gap: 4px;
    margin-bottom: 2px;
  }

  .pt-name {
    font-size: 11px;
    font-weight: bold;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .pt-fav {
    color: var(--chat-favorite, #808000);
    font-size: 11px;
    flex-shrink: 0;
  }

  .pt-badge {
    margin-left: auto;
    padding: 0 4px;
    height: 15px;
    display: inline-flex;
    align-items: center;
    font-size: 9px;
    font-weight: bold;
    color: var(--chat-text-on-accent, #ffffff);
    background: var(--chat-accent, #000080);
    border: 1px solid;
    border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
    flex-shrink: 0;

    &.recent {
      color: var(--chat-accent, #000080);
      background: var(--chat-panel, #c0c0c0);
    }
  }

  .pt-desc {
    margin: 0;
    font-size: 10px;
    line-height: 1.35;
    word-break: break-word;

    &.muted {
      color: var(--chat-text-muted, #808080);
    }
  }
}

@media (max-width: 1024px) {
  .role-drawer {
    --rail: 54px;
    --panel: 230px;
  }

  .panel {
    padding-left: calc(var(--rail) - 2px);
  }
}

@media (max-width: 768px) {
  .role-drawer {
    --rail: 100%;
    --panel: 100%;
    height: auto;
    pointer-events: auto;
  }

  .rail {
    width: 100%;
    height: auto;
    flex-direction: row;
    justify-content: space-between;
    padding: 3px 6px;
    border-radius: var(--chat-radius, 0);
    align-items: center;
  }

  .trigger {
    width: 36px;
    height: 30px;
    flex-shrink: 0;
  }

  .rail-meta {
    align-items: center;
    text-align: left;
    flex-shrink: 0;
  }

  .rail-meta span {
    font-size: 12px;
  }

  .rail-meta small {
    display: none;
  }

  .rail-avatars {
    flex-direction: row;
    justify-content: flex-end;
    gap: 3px;
    flex: 1 1 auto;
    min-width: 0;
    overflow: hidden;
  }

  .avatar-group {
    flex-direction: row;
    gap: 3px;
    width: auto;
  }

  .avatar-divider {
    width: 1px;
    height: 24px;
    background: #808080;
  }
  .avatar-divider.recent-separator {
    width: 1px;
    height: 20px;
    margin: 0 3px;
    background: transparent;
    border-left: 2px dashed #888;
  }

  .preview-avatar,
  .rail-empty {
    width: 28px;
    height: 26px;
    border-radius: var(--chat-radius, 0);
  }

  .rail-summary {
    margin-top: 0;
    align-items: flex-end;
    text-align: right;
    flex-shrink: 0;
  }

  /* 角色面板 - 移动端全屏浮层 */
  .panel {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    width: 100%;
    height: 100%;
    max-height: 100vh;
    max-height: 100dvh;
    padding-left: 0;
    opacity: 1;
    transform: none;
    pointer-events: auto;
    z-index: 100;
    overflow: hidden;
    box-shadow: none;
  }

  .surface {
    height: 100%;
    border: none;
    border-radius: var(--chat-radius, 0);
    background: var(--chat-panel, #c0c0c0);
  }

  .role-drawer.mobile:not(.expanded) .panel {
    display: none;
  }

  .header {
    padding: 4px 8px;
    min-height: 36px;
  }

  .header .title h3 {
    font-size: 13px;
  }

  .header .title p {
    font-size: 10px;
  }

  .search-wrap {
    padding: 4px 6px;
  }

  .search-input {
    font-size: 13px;
    min-height: 20px;
  }

  .list {
    padding: 4px;
    -webkit-overflow-scrolling: touch;
  }

  .card {
    padding: 6px 8px;
    min-height: 44px;
  }

  .rank-card {
    padding: 6px 8px;
    min-height: 44px;
  }

  .name {
    font-size: 12px;
  }

  .desc {
    font-size: 10px;
  }
}

/* 手机窄屏：精简角色栏，保留少量头像快速切换 */
@media (max-width: 480px) {
  .rail {
    padding: 3px 4px;
    gap: 4px;
  }

  .rail-meta small {
    display: none;
  }

  /* 保留最多 3 个头像，超出隐藏 */
  .rail-avatars {
    gap: 2px;
  }

  .preview-avatar,
  .rail-empty {
    width: 26px;
    height: 24px;
  }

  .avatar-text {
    font-size: 11px;
  }

  .rail-summary {
    display: none;
  }

  /* 小屏角色面板卡片更大，便于触摸 */
  .card {
    padding: 8px;
    min-height: 48px;
  }

  .rank-card {
    padding: 8px;
    min-height: 48px;
  }

  .avatar {
    width: 36px;
    height: 34px;
  }

  .avatar-text {
    font-size: 14px;
  }

  .name {
    font-size: 13px;
  }

  .desc {
    font-size: 11px;
  }
}
</style>
