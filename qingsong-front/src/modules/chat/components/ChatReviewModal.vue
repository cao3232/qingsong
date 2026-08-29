<template>
  <NModal
    :show="show"
    preset="card"
    title="对话复盘"
    style="width: 860px; max-height: 86vh"
    :bordered="false"
    @update:show="handleUpdateShow"
  >
    <div class="chat-review scrollbar-md">
      <div class="review-header">
        <div class="review-date-area">
          <button class="review-nav-btn" type="button" title="前一天" :disabled="loading"
            @click="shiftDate(-1)">‹</button>
          <n-date-picker v-model:formatted-value="selectedDate" type="date" class="review-date-picker"
            :is-date-disabled="isDateDisabled" value-format="yyyy-MM-dd" />
          <button class="review-nav-btn" type="button" title="后一天" :disabled="loading"
            @click="shiftDate(1)">›</button>
        </div>
        <button
          class="review-ai-btn"
          :class="{ running: analyzing }"
          type="button"
          :disabled="loading || !data"
          @click="analyzing ? stopInsight() : generateInsight()"
        >
          {{ analyzing ? "停止" : essay ? "重新生成" : "AI 解读" }}
        </button>
      </div>

      <div v-if="loading" class="review-state">正在加载数据...</div>
      <div v-else-if="!data" class="review-state">该日期暂无对话数据</div>

      <template v-else>
        <div class="review-cards">
          <div class="review-card">
            <span class="review-card-num">{{ data.totalMessages }}</span>
            <span class="review-card-label">消息条数</span>
          </div>
          <div class="review-card">
            <span class="review-card-num">{{ data.userMessages }}</span>
            <span class="review-card-label">对话轮次</span>
          </div>
          <div class="review-card">
            <span class="review-card-num">{{ data.activeRoles }}</span>
            <span class="review-card-label">使用角色</span>
          </div>
          <div class="review-card">
            <span class="review-card-num">{{ data.avgRoundsPerRole }}</span>
            <span class="review-card-label">平均轮次/角色</span>
          </div>
        </div>

        <div class="review-columns">
          <div class="review-section">
            <div class="review-section-title">角色榜单</div>
            <div v-if="hasLeaderboard" class="review-lb">
              <div class="review-lb-col">
                <div class="review-lb-head">今日榜</div>
                <div v-for="(item, i) in lbToday" :key="'t' + i" class="review-lb-row">
                  <span class="review-lb-rank">{{ i + 1 }}</span>
                  <span class="review-lb-name" :title="item.name">{{ item.name }}</span>
                  <span class="review-lb-count">{{ item.count }}</span>
                </div>
                <div v-if="!lbToday.length" class="review-lb-empty">—</div>
              </div>
              <div class="review-lb-col">
                <div class="review-lb-head">总榜</div>
                <div v-for="(item, i) in lbTotal" :key="'g' + i" class="review-lb-row">
                  <span class="review-lb-rank">{{ i + 1 }}</span>
                  <span class="review-lb-name" :title="item.name">{{ item.name }}</span>
                  <span class="review-lb-count">{{ item.count }}</span>
                </div>
                <div v-if="!lbTotal.length" class="review-lb-empty">—</div>
              </div>
            </div>
            <div v-else class="review-lb-empty">暂无榜单</div>
          </div>

          <div class="review-section">
            <div class="review-section-title">角色排行</div>
            <div v-if="data.roles.length" class="review-roles">
              <div v-for="(role, index) in data.roles" :key="role.name || role.id" class="review-role">
                <span class="review-role-rank">{{ index + 1 }}</span>
                <span class="review-role-name" :title="role.name">{{ role.name }}</span>
                <span class="review-role-stat">{{ role.userMessages }} 轮 / {{ role.sessions }} 场</span>
              </div>
            </div>
            <div v-else class="review-lb-empty">暂无角色</div>
          </div>
        </div>

        <div class="review-section">
          <div class="review-section-title">活跃时段</div>
          <div class="review-bars">
            <div v-for="bucket in data.timeBuckets" :key="bucket.label" class="review-bar-col">
              <div class="review-bar" :style="{ height: hourHeight(bucket.messages) }"></div>
              <span class="review-bar-val">{{ bucket.messages }}</span>
              <span class="review-bar-label">{{ bucket.label }}</span>
            </div>
          </div>
        </div>

        <div v-if="roleGroups.length" class="review-section">
          <div class="review-section-title">会话总结</div>
          <div v-for="group in roleGroups" :key="group.role" class="review-role-group">
            <div class="review-role-group-head">
              <span class="review-role-group-name">{{ group.role }}</span>
            </div>
            <p v-if="group.summary" class="review-role-summary">{{ group.summary }}</p>
            <div v-for="session in group.sessions" :key="session.sessionNo" class="review-session">
              <span class="review-session-title">{{ session.title || "会话" }}</span>
              <span class="review-session-summary">{{ session.summary }}</span>
            </div>
          </div>
        </div>

        <div class="review-section">
          <div class="review-section-title">当日随笔</div>
          <div v-if="analyzing && !essay" class="review-state">正在生成随笔...</div>
          <div v-else-if="essay" class="review-essay" v-html="renderedEssay"></div>
          <div v-else class="review-state muted">点击右上角「AI 解读」生成当日记叙</div>
        </div>
      </template>
    </div>
  </NModal>
</template>

<script setup>
import { NModal, NDatePicker, useMessage } from "naive-ui";
import { computed, ref, watch } from "vue";
import DOMPurify from "dompurify";
import { marked } from "marked";
import { chatAPI } from "../services/index.js";
import { agentApi } from "@/modules/agent-lab/services/agentApi";

const props = defineProps({
  show: { type: Boolean, default: false },
});
const emit = defineEmits(["update:show"]);

const message = useMessage();
const loading = ref(false);
const data = ref(null);
const analyzing = ref(false);
const essay = ref("");
const sessionSummaries = ref([]);
const roleSummaries = ref([]);
let streamHandle = null;

// —— 按天解读：日期选择（yyyy-MM-dd）——
const formatDate = (d) => {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
};
const selectedDate = ref(formatDate(new Date()));
const isDateDisabled = (ts) => ts > Date.now();
const todayStr = () => formatDate(new Date());
const shiftDate = (offset) => {
  const d = new Date(`${selectedDate.value}T00:00:00`);
  d.setDate(d.getDate() + offset);
  const next = formatDate(d);
  if (next > todayStr()) return;
  selectedDate.value = next;
};
const resetInsight = () => {
  essay.value = "";
  sessionSummaries.value = [];
  roleSummaries.value = [];
};

const maxHour = computed(() =>
  Math.max(1, ...(data.value?.timeBuckets || []).map((b) => b.messages || 0))
);
const hourHeight = (value) =>
  `${Math.max(6, Math.round(((value || 0) / maxHour.value) * 56))}px`;

const lbToday = computed(() =>
  (data.value?.leaderboard?.today || []).slice(0, 5)
);
const lbTotal = computed(() =>
  (data.value?.leaderboard?.total || []).slice(0, 5)
);
const hasLeaderboard = computed(() => lbToday.value.length > 0 || lbTotal.value.length > 0);

// 会话总结按角色归组 + 角色小结
const roleGroups = computed(() => {
  const groups = new Map();
  for (const s of sessionSummaries.value || []) {
    if (!groups.has(s.role)) groups.set(s.role, { role: s.role, summary: "", sessions: [] });
    groups.get(s.role).sessions.push(s);
  }
  const summaryMap = new Map((roleSummaries.value || []).map((r) => [r.role, r.summary]));
  for (const group of groups.values()) {
    group.summary = summaryMap.get(group.role) || "";
  }
  return Array.from(groups.values());
});

const renderedEssay = computed(() =>
  DOMPurify.sanitize(marked.parse(essay.value || "") || "")
);

const load = async () => {
  if (!props.show) return;
  loading.value = true;
  resetInsight();
  try {
    const [review, insight] = await Promise.all([
      chatAPI.getReview(selectedDate.value),
      chatAPI.getInsight(selectedDate.value),
    ]);
    data.value = review;
    if (insight) {
      essay.value = insight.essay || "";
      const parsedSessions = parseJsonArray(insight.sessionSummariesJson);
      if (parsedSessions) sessionSummaries.value = parsedSessions;
      const parsedRoles = parseJsonArray(insight.roleSummariesJson);
      if (parsedRoles) roleSummaries.value = parsedRoles;
    }
  } catch (error) {
    data.value = null;
    message.error(error?.message || "获取对话复盘失败，请稍后重试");
  } finally {
    loading.value = false;
  }
};

const parseJsonArray = (text) => {
  if (!text) return null;
  try {
    const parsed = JSON.parse(text);
    return Array.isArray(parsed) ? parsed : null;
  } catch {
    return null;
  }
};

const generateInsight = () => {
  if (analyzing.value) return;
  analyzing.value = true;
  essay.value = "";
  sessionSummaries.value = [];
  roleSummaries.value = [];
  streamHandle = agentApi.runGraphStream(
    "daily-review",
    "日报",
    {
      date: selectedDate.value,
      onEvent: (event) => {
        if (!event) return;
        // 会话总结 / 角色小结 / 随笔都从 state 快照读取（不依赖节点名，避免流式内部命名差异）
        if (event.state && typeof event.state === "object") {
          if (event.state.session_summaries) {
            try {
              const parsed = JSON.parse(event.state.session_summaries);
              if (Array.isArray(parsed)) sessionSummaries.value = parsed;
            } catch {
              /* 忽略解析失败 */
            }
          }
          if (event.state.role_summaries) {
            try {
              const parsed = JSON.parse(event.state.role_summaries);
              if (Array.isArray(parsed)) roleSummaries.value = parsed;
            } catch {
              /* 忽略解析失败 */
            }
          }
          if (typeof event.state.essay === "string" && event.state.essay) {
            essay.value = event.state.essay;
          }
        }
      },
      onError: (error) => {
        analyzing.value = false;
        streamHandle = null;
        message.error(error?.message || "AI 解读生成失败，请稍后重试");
      },
      onDone: () => {
        analyzing.value = false;
        streamHandle = null;
      },
    }
  );
};

const stopInsight = () => {
  if (streamHandle) {
    streamHandle.abort();
    streamHandle = null;
  }
  analyzing.value = false;
};

watch(
  () => props.show,
  (visible) => {
    if (visible) load();
    else stopInsight();
  }
);

watch(selectedDate, (val) => {
  if (!props.show || !val) return;
  if (val > todayStr()) {
    selectedDate.value = todayStr();
    return;
  }
  stopInsight();
  load();
});

const handleUpdateShow = (visible) => {
  if (!visible) stopInsight();
  emit("update:show", visible);
};
</script>

<style lang="scss" scoped>
.chat-review {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 74vh;
  overflow-y: auto;
  font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);
}

.review-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.review-date-area {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.review-nav-btn {
  flex-shrink: 0;
  width: 22px;
  height: 26px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 2px solid;
  border-color: var(--chat-bevel-light, #fff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #fff);
  background: var(--chat-panel, #c0c0c0);
  color: var(--chat-text, #000);
  font-size: 14px;
  font-weight: bold;
  cursor: pointer;
  font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);

  &:active {
    border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #fff) var(--chat-bevel-light, #fff) var(--chat-bevel-shadow, #808080);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.review-date-picker {
  width: 134px;

  :deep(.n-input) {
    height: 26px;
    box-sizing: border-box;
    padding: 0;
    border-radius: var(--chat-radius, 0);
    background: var(--chat-surface, #fff);
    border: 2px solid;
    border-color: var(--chat-bevel-light, #fff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #fff);
  }

  :deep(.n-input-wrapper) {
    padding: 0;
  }

  :deep(.n-input__input-el) {
    height: 22px;
    padding: 0;
    font-size: 11px;
    text-align: center;
    font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);
  }

  :deep(.n-input__suffix) {
    flex-shrink: 0;
    display: inline-flex;
    align-items: center;
  }
}

.review-date {
  font-size: 12px;
  font-weight: bold;
  color: var(--chat-accent, #000080);
}

.review-ai-btn {
  padding: 3px 14px;
  border: 2px solid;
  border-color: var(--chat-bevel-light, #fff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #fff);
  background: var(--chat-accent, #000080);
  color: var(--chat-text-on-accent, #fff);
  font-size: 12px;
  font-weight: bold;
  cursor: pointer;
  font-family: var(--chat-font-family, "MS Sans Serif", "Segoe UI", Tahoma, sans-serif);

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  &:active {
    border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #fff) var(--chat-bevel-light, #fff) var(--chat-bevel-shadow, #808080);
  }

  &.running {
    background: #800000;
  }
}

.review-state {
  padding: 20px 0;
  text-align: center;
  font-size: 12px;
  color: var(--chat-text-muted, #808080);
  border: 2px dashed #808080;

  &.muted {
    border-style: solid;
    border-color: var(--chat-bevel-shadow, #808080);
  }
}

.review-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
}

.review-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 8px 4px;
  border: 2px solid;
  border-color: var(--chat-bevel-light, #fff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #fff);
  background: var(--chat-panel, #c0c0c0);
}

.review-card-num {
  font-size: 18px;
  font-weight: bold;
  color: var(--chat-accent, #000080);
}

.review-card-label {
  font-size: 10px;
  color: var(--chat-text-muted, #808080);
}

.review-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.review-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.review-section-title {
  padding: 2px 4px;
  font-size: 11px;
  font-weight: bold;
  color: var(--chat-text-on-accent, #fff);
  background: #808080;
  border: 1px solid;
  border-color: var(--chat-bevel-frame-dark, #404040) var(--chat-bevel-frame-light, #dfdfdf) var(--chat-bevel-frame-light, #dfdfdf) var(--chat-bevel-frame-dark, #404040);
}

.review-lb {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}

.review-lb-col {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.review-lb-head {
  font-size: 10px;
  font-weight: bold;
  color: var(--chat-accent, #000080);
}

.review-lb-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 2px 4px;
  border: 2px solid;
  border-color: var(--chat-bevel-light, #fff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #fff);
}

.review-lb-rank {
  flex: 0 0 16px;
  text-align: center;
  font-size: 10px;
  font-weight: bold;
  color: var(--chat-text-muted, #808080);
}

.review-lb-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
  font-weight: bold;
}

.review-lb-count {
  flex-shrink: 0;
  font-size: 10px;
  font-weight: bold;
  color: var(--chat-accent, #000080);
}

.review-lb-empty {
  padding: 8px;
  text-align: center;
  font-size: 11px;
  color: var(--chat-text-muted, #808080);
}

.review-roles {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.review-role {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 6px;
  border: 2px solid;
  border-color: var(--chat-bevel-light, #fff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #fff);
}

.review-role-rank {
  flex: 0 0 18px;
  width: 18px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: bold;
  color: var(--chat-text, #000);
  border: 2px solid;
  border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #fff) var(--chat-bevel-light, #fff) var(--chat-bevel-shadow, #808080);
  background: var(--chat-panel, #c0c0c0);
}

.review-role-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  font-weight: bold;
}

.review-role-stat {
  flex-shrink: 0;
  font-size: 10px;
  color: var(--chat-text-muted, #808080);
}

.review-bars {
  display: flex;
  align-items: flex-end;
  gap: 6px;
  padding: 4px;
  min-height: 96px;
  border: 2px solid;
  border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #fff) var(--chat-bevel-light, #fff) var(--chat-bevel-shadow, #808080);
  background: var(--chat-surface, #ffffff);
}

.review-bar-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.review-bar {
  width: 100%;
  max-width: 34px;
  background: var(--chat-accent, #000080);
  border: 1px solid #000;
}

.review-bar-val {
  font-size: 9px;
  font-weight: bold;
  color: var(--chat-accent, #000080);
}

.review-bar-label {
  font-size: 9px;
  color: var(--chat-text-muted, #808080);
}

.review-role-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 6px;
  border: 2px solid;
  border-color: var(--chat-bevel-light, #fff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #fff);
}

.review-role-group-head {
  font-size: 12px;
  font-weight: bold;
  color: var(--chat-accent, #000080);
}

.review-role-summary {
  margin: 0;
  font-size: 11px;
  line-height: 1.5;
  color: var(--chat-text, #000);
}

.review-session {
  display: flex;
  gap: 8px;
  padding: 3px 6px;
  border: 2px solid;
  border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #fff) var(--chat-bevel-light, #fff) var(--chat-bevel-shadow, #808080);
  background: var(--chat-surface, #ffffff);
}

.review-session-title {
  flex: 0 0 96px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 10px;
  font-weight: bold;
  color: var(--chat-text-muted, #808080);
}

.review-session-summary {
  flex: 1;
  min-width: 0;
  font-size: 11px;
  line-height: 1.5;
  color: var(--chat-text, #000);
}

.review-essay {
  padding: 10px;
  border: 2px solid;
  border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #fff) var(--chat-bevel-light, #fff) var(--chat-bevel-shadow, #808080);
  background: var(--chat-tooltip, #ffffe1);
  font-size: 13px;
  line-height: 1.8;
  color: var(--chat-text, #000);

  :deep(p) {
    margin: 0 0 8px;
  }

  :deep(p:last-child) {
    margin-bottom: 0;
  }
}
</style>
