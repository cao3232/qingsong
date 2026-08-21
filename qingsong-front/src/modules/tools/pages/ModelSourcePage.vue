<template>
  <div class="source-panel-content">
    <div class="panel-header">
      <div class="header-content">
        <div>
          <h2>模型来源</h2>
          <p>管理 AI 模型的来源分类，新建模型时可选择对应来源</p>
        </div>
        <button @click="openModal()" class="btn-primary">
          <svg
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
          >
            <line x1="12" y1="5" x2="12" y2="19" />
            <line x1="5" y1="12" x2="19" y2="12" />
          </svg>
          新建来源
        </button>
      </div>
    </div>

    <div class="config-form">
      <!-- 加载状态 -->
      <div v-if="loading" class="loading-state">
        <div class="loading-spinner"></div>
        <p>加载中...</p>
      </div>

      <!-- 错误状态 -->
      <div v-else-if="error" class="error-state">
        <p>{{ error }}</p>
        <button @click="loadSources" class="retry-btn">重试</button>
      </div>

      <!-- 空状态 -->
      <div v-else-if="sources.length === 0" class="empty-state">
        <p>暂无模型来源</p>
        <button @click="openModal()" class="btn-primary">立即创建</button>
      </div>

      <!-- 来源列表 -->
      <div v-else class="model-table-container">
        <table class="model-table">
          <colgroup>
            <col style="width: 130px" />
            <col style="width: 110px" />
            <col style="width: 220px" />
            <col style="width: 120px" />
            <col style="width: 60px" />
            <col style="width: 70px" />
            <col style="width: 150px" />
            <col style="width: 160px" />
          </colgroup>
          <thead>
            <tr>
              <th>来源名称</th>
              <th>来源编码</th>
              <th>API地址</th>
              <th>描述</th>
              <th>排序</th>
              <th>状态</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="source in sources" :key="source.id">
              <td>
                <div class="source-name-cell">
                  <span class="source-avatar">{{ initialOf(source.sourceName) }}</span>
                  <span class="model-name">{{ source.sourceName }}</span>
                </div>
              </td>
              <td>
                <span class="model-code">{{ source.sourceCode }}</span>
              </td>
              <td>
                <a
                  v-if="source.apiBaseUrl"
                  :href="source.apiBaseUrl"
                  target="_blank"
                  class="url-link"
                  :title="source.apiBaseUrl"
                  >{{ source.apiBaseUrl }}</a
                >
                <span v-else class="table-placeholder">-</span>
              </td>
              <td>
                <span v-if="source.description">{{ source.description }}</span>
                <span v-else class="table-placeholder">-</span>
              </td>
              <td style="text-align: center">
                <span class="sort-chip">{{ source.sortOrder || 0 }}</span>
              </td>
              <td>
                <span
                  :class="[
                    'status-badge',
                    source.isActive ? 'status-active' : 'status-inactive',
                  ]"
                >
                  {{ source.isActive ? "启用" : "禁用" }}
                </span>
              </td>
              <td class="date-cell">{{ formatDate(source.createDate) }}</td>
              <td class="actions-cell">
                <button
                  @click="openModal(source)"
                  class="table-btn btn-edit"
                  title="编辑"
                >
                  <svg
                    width="16"
                    height="16"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                  >
                    <path
                      d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"
                    />
                    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
                  </svg>
                </button>
                <button
                  @click="copySource(source)"
                  class="table-btn btn-copy"
                  title="复制"
                >
                  <svg
                    width="16"
                    height="16"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                  >
                    <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
                    <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                  </svg>
                </button>
                <button
                  @click="toggleStatus(source)"
                  :class="[
                    'table-btn',
                    'btn-status',
                    source.isActive ? 'btn-disable' : 'btn-enable',
                  ]"
                  :title="source.isActive ? '禁用' : '启用'"
                >
                  <svg
                    width="16"
                    height="16"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                  >
                    <rect x="6" y="4" width="4" height="16" />
                    <rect x="14" y="4" width="4" height="16" />
                  </svg>
                </button>
                <button
                  @click="deleteSource(source)"
                  class="table-btn btn-delete"
                  title="删除"
                >
                  <svg
                    width="16"
                    height="16"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                  >
                    <path d="M3 6h18" />
                    <path
                      d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"
                    />
                    <line x1="10" y1="11" x2="10" y2="17" />
                    <line x1="14" y1="11" x2="14" y2="17" />
                  </svg>
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>

  <!-- 编辑弹窗 -->
  <n-modal
    v-model:show="showModal"
    preset="dialog"
    :title="editingSource ? '编辑来源' : '新建来源'"
    :positive-text="saving ? '保存中...' : '保存'"
    :negative-text="'取消'"
    :loading="saving"
    :disabled="saving"
    @positive-click="saveSource"
    @negative-click="closeModal"
    style="width: min(560px, calc(100vw - 24px))"
  >
    <div class="source-form">
      <div class="source-form-grid">
        <div class="source-form-field">
          <label class="source-form-label">来源名称 <span class="required">*</span></label>
          <n-input v-model:value="form.sourceName" placeholder="请输入来源名称，如：OpenAI" />
        </div>
        <div class="source-form-field">
          <label class="source-form-label">来源编码 <span class="required">*</span></label>
          <n-input v-model:value="form.sourceCode" placeholder="如：openai" autocomplete="off"
            :disabled="editingSource" />
        </div>
      </div>
      <div class="source-form-field">
        <label class="source-form-label">API 密钥 <span class="required">*</span></label>
        <n-input v-model:value="form.apiKey" type="password" show-password-on="click" autocomplete="new-password"
          :input-props="{ 'data-lpignore': 'true', 'data-1p-ignore': '', 'data-bwignore': '' }"
          placeholder="请输入API密钥" />
      </div>
      <div class="source-form-field">
        <label class="source-form-label">API 地址</label>
        <n-input v-model:value="form.apiBaseUrl" placeholder="请输入API基础地址，如：https://api.openai.com" />
      </div>
      <div class="source-form-field">
        <label class="source-form-label">描述</label>
        <n-input v-model:value="form.description" type="textarea" placeholder="请输入描述信息" :rows="3" />
      </div>
      <div class="source-form-grid">
        <div class="source-form-field">
          <label class="source-form-label">排序</label>
          <n-input-number v-model:value="form.sortOrder" :min="0" placeholder="0" style="width: 100%" />
        </div>
        <div class="source-form-field">
          <label class="source-form-label">启用状态</label>
          <n-switch v-model:value="form.isActive" />
        </div>
      </div>
    </div>
  </n-modal>
</template>

<script setup>
import { ref, onMounted } from "vue";
import {
  useMessage,
  useDialog,
  NModal,
  NInput,
  NInputNumber,
  NSwitch,
} from "naive-ui";

const message = useMessage();
const dialog = useDialog();

// 来源名称首字符（用于列表初始头像）
const initialOf = (name) => String(name || "?").trim().charAt(0).toUpperCase();

// 数据
const sources = ref([]);
const loading = ref(false);
const error = ref(null);

// 弹窗
const showModal = ref(false);
const editingSource = ref(null);
const saving = ref(false);

// 表单
const form = ref({
  sourceName: "",
  sourceCode: "",
  apiBaseUrl: "",
  apiKey: "",
  description: "",
  sortOrder: 0,
  isActive: true,
});

// API 基础路径
import { API_BASE_URL } from "@/config/env";
import http from "@/utils/http";

const API_BASE = API_BASE_URL + "/api/model-sources";

// 加载来源列表
const loadSources = async () => {
  loading.value = true;
  error.value = null;
  try {
    const result = await http.get(`${API_BASE}/info`);
    if (result.ok && result.data) {
      sources.value = result.data;
    } else {
      sources.value = [];
    }
  } catch (err) {
    console.error("加载来源列表失败:", err);
    error.value = "加载失败，请重试";
    sources.value = [];
  } finally {
    loading.value = false;
  }
};

// 格式化日期
const formatDate = (dateString) => {
  if (!dateString) return "-";
  try {
    const date = new Date(dateString);
    return date.toLocaleString("zh-CN", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch (e) {
    return dateString;
  }
};

// 打开弹窗
const openModal = (source = null) => {
  if (source) {
    editingSource.value = source;
    form.value = {
      sourceName: source.sourceName,
      sourceCode: source.sourceCode,
      apiBaseUrl: source.apiBaseUrl || "",
      apiKey: source.apiKey || "",
      description: source.description || "",
      sortOrder: source.sortOrder || 0,
      isActive: source.isActive !== false,
    };
  } else {
    editingSource.value = null;
    form.value = {
      sourceName: "",
      sourceCode: "",
      apiBaseUrl: "",
      apiKey: "",
      description: "",
      sortOrder: 0,
      isActive: true,
    };
  }
  showModal.value = true;
};

// 关闭弹窗
const closeModal = () => {
  showModal.value = false;
  editingSource.value = null;
  form.value = {
    sourceName: "",
    sourceCode: "",
    apiBaseUrl: "",
    apiKey: "",
    description: "",
    sortOrder: 0,
    isActive: true,
  };
};

// 保存
const saveSource = async () => {
  if (!form.value.sourceName || !form.value.sourceCode || !form.value.apiKey) {
    message.warning("请填写来源名称、来源编码和API密钥");
    return false;
  }

  saving.value = true;
  try {
    const body = {
      sourceName: form.value.sourceName,
      sourceCode: form.value.sourceCode,
      apiBaseUrl: form.value.apiBaseUrl,
      apiKey: form.value.apiKey,
      description: form.value.description,
      sortOrder: form.value.sortOrder,
      isActive: form.value.isActive,
    };

    let result;
    if (editingSource.value) {
      result = await http.put(`${API_BASE}/${editingSource.value.id}`, body);
    } else {
      result = await http.post(API_BASE, body);
    }

    if (result.ok === 1) {
      message.success(editingSource.value ? "更新成功" : "创建成功");
      closeModal();
      await loadSources();
      return true;
    } else {
      message.error(result.msg || "操作失败");
      return false;
    }
  } catch (err) {
    console.error("保存失败:", err);
    message.error("保存失败");
    return false;
  } finally {
    saving.value = false;
  }
};

// 复制
const copySource = (source) => {
  editingSource.value = null;
  form.value = {
    sourceName: source.sourceName + " (副本)",
    sourceCode: source.sourceCode,
    apiBaseUrl: source.apiBaseUrl || "",
    apiKey: source.apiKey || "",
    description: source.description || "",
    sortOrder: source.sortOrder || 0,
    isActive: source.isActive !== false,
  };
  showModal.value = true;
};

// 切换状态
const toggleStatus = async (source) => {
  if (source.isActive) {
    const activeCount = sources.value.filter((s) => s.isActive).length;
    if (activeCount <= 1) {
      message.warning("至少需要保留一个启用的来源");
      return;
    }
  }

  try {
    const result = await http.post(`${API_BASE}/${source.id}/toggle-active`);
    if (result.ok === 1) {
      await loadSources();
      message.success("状态切换成功");
    } else {
      message.error(result.msg || "操作失败");
    }
  } catch (err) {
    console.error("切换状态失败:", err);
    message.error("操作失败");
  }
};

// 删除
const deleteSource = (source) => {
  dialog.warning({
    title: "删除确认",
    content: `确定要删除来源编码「${source.sourceName}」所有信息吗？此操作不可恢复。`,
    positiveText: "删除",
    negativeText: "取消",
    onPositiveClick: async () => {
      try {
        const result = await http.delete(`${API_BASE}/${source.id}`);
        if (result.ok === 1) {
          message.success("删除成功");
          await loadSources();
        } else {
          message.error(result.msg || "删除失败");
        }
      } catch (err) {
        console.error("删除失败:", err);
        message.error("删除失败");
      }
    },
  });
};

onMounted(() => {
  loadSources();
});
</script>

<style scoped>
.source-panel-content {
  overflow: visible;
  padding: 0 16px 16px 16px;
}

/* 面板头部 */
.panel-header {
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--app-border-color, rgba(0, 0, 0, 0.08));
}
.panel-header h2 {
  margin: 0 0 4px 0;
  font-size: 24px;
  font-weight: 600;
  color: var(--app-text-primary, #1f2937);
  letter-spacing: -0.02em;
}
.panel-header p {
  margin: 0;
  color: var(--app-text-secondary, #6b7280);
  font-size: 0.9rem;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
}

/* 主按钮 */
.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.625rem 1.25rem;
  border: none;
  border-radius: 8px;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.15s ease, box-shadow 0.15s ease;
  background: linear-gradient(135deg, #ffb86c 0%, #fb7185 100%);
  color: white;
  box-shadow: 0 16px 24px rgba(244, 114, 182, 0.22);
}
.btn-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, #ffae52 0%, #f43f5e 100%);
  box-shadow: 0 3px 6px rgba(244, 114, 182, 0.25);
}
.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 表单容器 */
.config-form {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

/* 加载/错误/空状态 */
.loading-state,
.error-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem 2rem;
  color: var(--app-text-secondary, #6b7280);
}
.loading-state p,
.error-state p,
.empty-state p {
  margin: 0 0 0.5rem 0;
  font-size: 0.95rem;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid var(--app-border-color, #e5e7eb);
  border-top-color: var(--app-active-bg, #3b82f6);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 1rem;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.retry-btn {
  margin-top: 1rem;
  padding: 0.5rem 1.5rem;
  border: 1px solid var(--app-active-bg, #3b82f6);
  background: transparent;
  color: var(--app-active-bg, #3b82f6);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 0.875rem;
}
.retry-btn:hover {
  background: var(--app-active-bg, #3b82f6);
  color: white;
}

/* 表格容器 */
.model-table-container {
  position: relative;
  max-height: calc(100vh - 280px);
  overflow-y: auto;
  overflow-x: auto;
  border-radius: 18px;
  border: 1px solid var(--app-border-color, #e5e7eb);
  background: var(--app-component-bg, rgba(255, 255, 255, 0.8));
}
.model-table-container::-webkit-scrollbar {
  width: 8px;
}
.model-table-container::-webkit-scrollbar-track {
  background: transparent;
  border-radius: 4px;
}
.model-table-container::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.18);
  border-radius: 4px;
  min-height: 40px;
}
.model-table-container::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 0, 0, 0.3);
}

/* 数据表格 */
.model-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.875rem;
  table-layout: fixed;
}
.model-table thead {
  background: var(--app-bg-secondary, #f9fafb);
  position: sticky;
  top: 0;
  z-index: 10;
}
.model-table th {
  padding: 0.875rem 12px;
  text-align: left;
  font-weight: 600;
  color: var(--app-text-primary, #1f2937);
  font-size: 0.8rem;
  border-bottom: 2px solid var(--app-border-color, #e5e7eb);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.model-table tbody tr {
  border-bottom: 1px solid var(--app-border-color, #e5e7eb);
  transition: background-color 0.15s ease;
}
.model-table tbody tr:hover {
  background: var(--app-hover-bg, rgba(59, 130, 246, 0.05));
}
.model-table tbody tr:nth-child(even) {
  background: rgba(249, 250, 251, 0.5);
}
.model-table td {
  padding: 0.875rem 12px;
  color: var(--app-text-primary, #374151);
  vertical-align: middle;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 单元格样式 */
.actions-cell {
  overflow: visible !important;
  white-space: nowrap;
}
.model-name {
  font-weight: 600;
  color: var(--app-text-primary, #1f2937);
}
.model-code {
  font-family: "Courier New", monospace;
  font-size: 0.8rem;
  padding: 0.2rem 0.5rem;
  background: var(--app-bg-secondary, #f3f4f6);
  color: var(--app-text-secondary, #6b7280);
  border-radius: 4px;
  font-weight: 500;
}
.url-link {
  font-size: 0.8rem;
  color: var(--app-active-bg, #3b82f6);
  text-decoration: none;
  cursor: pointer;
  transition: color 0.2s ease;
}
.url-link:hover {
  color: #2563eb;
  text-decoration: underline;
}
.table-placeholder {
  color: var(--app-text-secondary, #9ca3af);
}
.date-cell {
  font-size: 0.8rem;
  color: var(--app-text-secondary, #6b7280);
}

/* 来源名称（含初始头像） */
.source-name-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.source-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  flex-shrink: 0;
  font-size: 0.9rem;
  font-weight: 700;
  color: #ffffff;
  background: linear-gradient(135deg, #ffb86c 0%, #fb7185 100%);
  box-shadow: 0 4px 10px rgba(244, 114, 182, 0.24);
}

/* 排序 chip */
.sort-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--app-text-secondary, #6b7280);
  background: var(--app-bg-secondary, #f3f4f6);
}

/* 来源编辑弹窗表单 */
.source-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-top: 2px;
}
.source-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.source-form-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.source-form-label {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--app-text-primary, #374151);
}
.source-form-label .required {
  color: #ef4444;
}
@media (max-width: 520px) {
  .source-form-grid {
    grid-template-columns: 1fr;
  }
}

/* 状态徽章 */
.status-badge {
  display: inline-flex;
  align-items: center;
  padding: 0.25rem 0.75rem;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 500;
}
.status-active {
  background: rgba(34, 197, 94, 0.1);
  color: #22c55e;
}
.status-inactive {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}

/* 表格操作按钮 */
.table-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 1px solid var(--app-border-color, #d1d5db);
  border-radius: 6px;
  background: var(--app-component-bg, white);
  cursor: pointer;
  transition: all 0.2s ease;
  color: var(--app-text-primary, #374151);
}
.table-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.table-btn.btn-edit:hover:not(:disabled) {
  background: rgba(59, 130, 246, 0.1);
  border-color: #3b82f6;
  color: #3b82f6;
}
.table-btn.btn-copy:hover:not(:disabled) {
  background: rgba(16, 185, 129, 0.1);
  border-color: #10b981;
  color: #10b981;
}
.table-btn.btn-status.btn-enable:hover:not(:disabled) {
  background: rgba(34, 197, 94, 0.1);
  border-color: #22c55e;
  color: #22c55e;
}
.table-btn.btn-status.btn-disable:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.1);
  border-color: #ef4444;
  color: #ef4444;
}
.table-btn.btn-delete:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.1);
  border-color: #ef4444;
  color: #ef4444;
}
.table-btn:not(:last-child) {
  margin-right: 0.5rem;
}

/* 暗色模式适配 */
:global(.dark) .model-table tbody tr:nth-child(even) {
  background: rgba(255, 255, 255, 0.03);
}
:global(.dark) .model-code {
  background: rgba(255, 255, 255, 0.08);
}
:global(.dark) .model-table-container {
  background: var(--app-component-bg, rgba(30, 30, 30, 0.8));
}
:global(.dark) .sort-chip {
  background: rgba(255, 255, 255, 0.06);
}
:global(.dark) .source-form-label {
  color: var(--app-text-primary, #e5e7eb);
}

/* 小屏适配 */
@media (max-width: 640px) {
  .source-panel-content {
    padding: 0 4px 12px;
  }
  .header-content {
    flex-direction: column;
    align-items: flex-start;
  }
  .header-content .btn-primary {
    align-self: flex-start;
  }
}
</style>
