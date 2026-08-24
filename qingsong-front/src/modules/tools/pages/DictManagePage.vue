<template>
  <div class="dict-manage-content">
    <div class="panel-header">
      <div class="header-content">
        <div>
          <h2>字典管理</h2>
          <p>维护业务字典（下拉选项、标签等），修改后缓存自动刷新即时生效</p>
        </div>
        <div class="header-actions">
          <button @click="handleReload" class="btn-secondary" :disabled="reloading">
            {{ reloading ? '刷新中...' : '刷新缓存' }}
          </button>
          <button @click="openModal(null)" class="btn-primary">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19" />
              <line x1="5" y1="12" x2="19" y2="12" />
            </svg>
            新增字典项
          </button>
        </div>
      </div>
    </div>

    <div class="config-form">
      <!-- 筛选栏 -->
      <div class="filter-bar">
        <input
          v-model="searchCode"
          class="filter-input"
          type="text"
          placeholder="按字典编码筛选，如 chat_model"
          @keyup.enter="handleSearch"
        />
        <button @click="handleSearch" class="btn-secondary">查询</button>
        <button @click="handleReset" class="btn-secondary">重置</button>
      </div>

      <!-- 加载状态 -->
      <div v-if="loading" class="loading-state">
        <div class="loading-spinner"></div>
        <p>加载中...</p>
      </div>

      <!-- 错误状态 -->
      <div v-else-if="error" class="error-state">
        <p>{{ error }}</p>
        <button @click="loadList" class="retry-btn">重试</button>
      </div>

      <!-- 空状态 -->
      <div v-else-if="records.length === 0" class="empty-state">
        <p>暂无字典项，点击右上角"新增字典项"创建</p>
      </div>

      <!-- 数据表格 -->
      <div v-else class="model-table-container">
        <table class="model-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>字典编码</th>
              <th>文案</th>
              <th>值</th>
              <th>附加信息</th>
              <th>排序</th>
              <th>状态</th>
              <th>备注</th>
              <th>更新时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in records" :key="item.id">
              <td class="table-placeholder">{{ item.id }}</td>
              <td><span class="model-code">{{ item.dictCode }}</span></td>
              <td class="model-name-cell">
                <span class="model-name" :title="item.itemLabel">{{ item.itemLabel }}</span>
              </td>
              <td class="key-cell">
                <span class="model-code" :title="item.itemKey">{{ item.itemKey }}</span>
              </td>
              <td class="table-placeholder">{{ item.itemExtra || '-' }}</td>
              <td>{{ item.sort }}</td>
              <td>
                <span :class="['status-badge', item.status === 1 ? 'status-active' : 'status-inactive']">
                  {{ item.status === 1 ? '启用' : '停用' }}
                </span>
              </td>
              <td class="table-placeholder">{{ item.remark || '-' }}</td>
              <td class="date-cell">{{ formatTime(item.updatedAt) }}</td>
              <td class="actions-cell">
                <button class="table-btn btn-copy" title="复制新建" @click="copyItem(item)">
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
                    <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                  </svg>
                </button>
                <button class="table-btn btn-edit" title="编辑" @click="openModal(item)">
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M17 3a2.828 2.828 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5L17 3z" />
                  </svg>
                </button>
                <button class="table-btn btn-status" :class="item.status === 1 ? 'btn-disable' : 'btn-enable'"
                  :title="item.status === 1 ? '停用' : '启用'" @click="toggleStatus(item)">
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="4" />
                    <path v-if="item.status === 1" d="M12 2v3m0 14v3M2 12h3m14 0h3M4.9 4.9l2.1 2.1m10 10l2.1 2.1M19.1 4.9l-2.1 2.1m-10 10l-2.1 2.1" />
                    <path v-else d="M12 2v3m0 14v3M2 12h3m14 0h3" />
                  </svg>
                </button>
                <button class="table-btn btn-delete" title="删除" @click="deleteItem(item)">
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="3 6 5 6 21 6" />
                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                  </svg>
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 分页 -->
      <div v-if="total > 0" class="pagination-bar">
        <span class="pagination-total">共 {{ total }} 条</span>
        <n-pagination
          v-model:page="pageNum"
          :page-size="pageSize"
          :item-count="total"
          @update:page="loadList"
        />
      </div>
    </div>

    <!-- 编辑弹窗 -->
    <n-modal v-model:show="showModal" preset="dialog" :title="editing ? '编辑字典项' : '新增字典项'"
      :positive-text="saving ? '保存中...' : '保存'" :negative-text="'取消'" :loading="saving" :disabled="saving"
      @positive-click="saveItem" @negative-click="closeModal" style="width: min(520px, calc(100vw - 24px));">
      <n-form label-placement="left" label-width="80">
        <n-form-item label="字典编码" required>
          <n-input v-model:value="form.dictCode" placeholder="如 chat_model" :disabled="!!editing" />
        </n-form-item>
        <n-form-item label="值" required>
          <n-input v-model:value="form.itemKey" placeholder="前端表单存储的值，如 gpt-4o" />
        </n-form-item>
        <n-form-item label="文案" required>
          <n-input v-model:value="form.itemLabel" placeholder="展示文案，如 GPT-4o" />
        </n-form-item>
        <n-form-item label="附加信息">
          <n-input v-model:value="form.itemExtra" placeholder="可选，JSON 字符串，如价格/图标" />
        </n-form-item>
        <n-form-item label="排序">
          <n-input-number v-model:value="form.sort" :min="0" placeholder="0" style="width: 100%;" />
        </n-form-item>
        <n-form-item label="启用状态">
          <n-switch v-model:value="form.status" :checked-value="1" :unchecked-value="0" />
        </n-form-item>
        <n-form-item label="备注">
          <n-input v-model:value="form.remark" placeholder="可选" />
        </n-form-item>
      </n-form>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useMessage, useDialog, NPagination, NModal, NForm, NFormItem, NInput, NInputNumber, NSwitch } from 'naive-ui'
import { dictService } from '@/services/dictService'
import { useDictStore } from '@/stores/dictStore'

const message = useMessage()
const dialog = useDialog()
const dictStore = useDictStore()

// 列表状态
const records = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const error = ref(null)
const searchCode = ref('')

// 弹窗状态
const showModal = ref(false)
const editing = ref(null)
const saving = ref(false)
const reloading = ref(false)

const emptyForm = () => ({
  dictCode: '',
  itemKey: '',
  itemLabel: '',
  itemExtra: '',
  sort: 0,
  status: 1,
  remark: ''
})

const form = ref(emptyForm())

const formatTime = (value) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const loadList = async () => {
  loading.value = true
  error.value = null
  try {
    const result = await dictService.page({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      dictCode: searchCode.value.trim() || undefined
    })
    records.value = result?.records || []
    total.value = result?.total || 0
  } catch (err) {
    console.error('加载字典列表失败:', err)
    error.value = '加载失败'
    records.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pageNum.value = 1
  loadList()
}

const handleReset = () => {
  searchCode.value = ''
  pageNum.value = 1
  loadList()
}

const openModal = (item) => {
  if (item) {
    editing.value = item
    form.value = {
      dictCode: item.dictCode,
      itemKey: item.itemKey,
      itemLabel: item.itemLabel,
      itemExtra: item.itemExtra || '',
      sort: item.sort || 0,
      status: item.status === 1 ? 1 : 0,
      remark: item.remark || ''
    }
  } else {
    editing.value = null
    form.value = emptyForm()
  }
  showModal.value = true
}

const closeModal = () => {
  showModal.value = false
  editing.value = null
  form.value = emptyForm()
}

const saveItem = async () => {
  if (!form.value.dictCode.trim()) {
    message.warning('请填写字典编码')
    return false
  }
  if (!form.value.itemKey.trim()) {
    message.warning('请填写字典项值')
    return false
  }
  if (!form.value.itemLabel.trim()) {
    message.warning('请填写展示文案')
    return false
  }
  saving.value = true
  try {
    if (editing.value) {
      await dictService.update(editing.value.id, form.value)
      message.success('更新成功')
    } else {
      await dictService.create({ ...form.value })
      message.success('创建成功')
    }
    closeModal()
    loadList()
    dictStore.refresh()
    return true
  } catch (err) {
    console.error('保存字典项失败:', err)
    message.error(err.message || '保存失败')
    return false
  } finally {
    saving.value = false
  }
}

const toggleStatus = async (item) => {
  try {
    await dictService.update(item.id, {
      ...item,
      status: item.status === 1 ? 0 : 1
    })
    message.success(item.status === 1 ? '已停用' : '已启用')
    loadList()
    dictStore.refresh()
  } catch (err) {
    console.error('切换状态失败:', err)
    message.error(err.message || '操作失败')
  }
}

// 复制新建：预填表单打开弹窗，清空值（itemKey 唯一，需重新填），保存后生成新记录
const copyItem = (item) => {
  editing.value = null
  form.value = {
    dictCode: item.dictCode,
    itemKey: '',
    itemLabel: item.itemLabel,
    itemExtra: item.itemExtra || '',
    sort: item.sort || 0,
    status: item.status === 1 ? 1 : 0,
    remark: item.remark || ''
  }
  showModal.value = true
}

const deleteItem = (item) => {
  dialog.warning({
    title: '删除确认',
    content: `确定要删除字典项 "${item.itemLabel}"（${item.dictCode} / ${item.itemKey}）吗？此操作不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await dictService.remove(item.id)
        message.success('删除成功')
        if (records.value.length === 1 && pageNum.value > 1) {
          pageNum.value -= 1
        }
        loadList()
        dictStore.refresh()
      } catch (err) {
        console.error('删除字典项失败:', err)
        message.error(err.message || '删除失败')
      }
    }
  })
}

const handleReload = async () => {
  reloading.value = true
  try {
    await dictService.reload()
    await dictStore.refresh()
    message.success('缓存已刷新')
  } catch (err) {
    console.error('刷新缓存失败:', err)
    message.error(err.message || '刷新失败')
  } finally {
    reloading.value = false
  }
}

onMounted(loadList)
</script>

<style scoped>
.dict-manage-content {
  width: 100%;
}

.panel-header {
  margin-bottom: 1.5rem;
  padding-bottom: 0.9rem;
  border-bottom: 1px solid rgba(245, 158, 11, 0.16);
}

.panel-header h2 {
  margin: 0 0 0.5rem 0;
  font-size: 1.5rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: #3f2f58;
}

.panel-header p {
  margin: 0;
  color: #8b6f61;
  font-size: 0.9rem;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
}

.header-actions {
  display: inline-flex;
  align-items: center;
  gap: 0.75rem;
}

.config-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.filter-input {
  width: 280px;
  padding: 0.6rem 1rem;
  border: 1px solid rgba(255, 230, 214, 0.9);
  border-radius: 999px;
  font-size: 0.9rem;
  transition: all 0.2s ease;
  background: rgba(255, 255, 255, 0.74);
  color: var(--app-text-primary, #1f2937);
}

.filter-input::placeholder {
  color: #b39a8d;
}

.filter-input:focus {
  outline: none;
  border-color: rgba(251, 191, 36, 0.6);
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.12);
}

.btn-primary,
.btn-secondary {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.55rem 1.1rem;
  border: none;
  border-radius: 999px;
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.18s ease;
  white-space: nowrap;
}

.btn-primary {
  background: linear-gradient(135deg, #ffb86c 0%, #fb7185 100%);
  color: #ffffff;
  box-shadow: 0 16px 24px rgba(244, 114, 182, 0.22);
}

.btn-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, #ffae52 0%, #f43f5e 100%);
  transform: translateY(-1px);
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-secondary {
  border: 1px solid rgba(255, 223, 201, 0.92);
  background: rgba(255, 255, 255, 0.72);
  color: #8a5d46;
}

.btn-secondary:hover:not(:disabled) {
  background: rgba(255, 241, 226, 0.96);
  color: #b45309;
}

.btn-secondary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.model-table-container {
  position: relative;
  overflow-x: auto;
  scrollbar-gutter: stable both-edges;
  border-radius: 18px;
  border: 1px solid rgba(255, 230, 214, 0.95);
  background: rgba(255, 255, 255, 0.8);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.72);
}

.model-table-container::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

.model-table-container::-webkit-scrollbar-track {
  background: var(--app-bg-secondary, #f1f1f1);
  border-radius: 3px;
}

.model-table-container::-webkit-scrollbar-thumb {
  background: var(--app-border-color, #c1c1c1);
  border-radius: 3px;
}

.model-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.875rem;
}

.model-table thead {
  background: rgba(255, 247, 237, 0.68);
  position: sticky;
  top: 0;
  z-index: 10;
}

.model-table th {
  padding: 0.875rem 1rem;
  text-align: left;
  font-weight: 600;
  color: #4b3d66;
  font-size: 0.8rem;
  border-bottom: 2px solid rgba(251, 191, 36, 0.18);
  white-space: nowrap;
}

.model-table tbody tr {
  border-bottom: 1px solid rgba(255, 230, 214, 0.7);
  transition: background-color 0.15s ease;
}

.model-table tbody tr:hover {
  background: rgba(255, 241, 226, 0.5);
}

.model-table tbody tr:last-child {
  border-bottom: none;
}

.model-table td {
  padding: 0.6rem 1rem;
  color: var(--app-text-primary, #374151);
  vertical-align: middle;
  white-space: nowrap;
}

.model-name-cell {
  min-width: 120px;
}

.model-name {
  font-weight: 600;
  color: var(--app-text-primary, #1f2937);
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: inline-block;
  vertical-align: bottom;
}

.key-cell {
  min-width: 120px;
}

.model-code {
  font-family: "Courier New", monospace;
  font-size: 0.78rem;
  padding: 0.2rem 0.55rem;
  background: rgba(255, 237, 213, 0.6);
  color: #9a6b45;
  border-radius: 6px;
  font-weight: 500;
  display: inline-block;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
  border: 1px solid rgba(255, 223, 201, 0.6);
}

.table-placeholder {
  color: var(--app-text-secondary, #9ca3af);
}

.status-badge {
  display: inline-flex;
  align-items: center;
  padding: 0.25rem 0.75rem;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 500;
}

.status-active {
  background: rgba(34, 197, 94, 0.1);
  color: #16a34a;
}

.status-inactive {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}

.date-cell {
  font-size: 0.8rem;
  color: var(--app-text-secondary, #6b7280);
  min-width: 130px;
}

.actions-cell {
  white-space: nowrap;
  min-width: 90px;
}

.table-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 1px solid rgba(255, 223, 201, 0.92);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.72);
  cursor: pointer;
  transition: all 0.18s ease;
  color: var(--app-text-primary, #374151);
}

.table-btn:not(:last-child) {
  margin-right: 0.4rem;
}

.table-btn:hover {
  transform: translateY(-1px);
}

.table-btn.btn-copy:hover {
  background: rgba(251, 191, 36, 0.14);
  border-color: #fbbf24;
  color: #d97706;
}

.table-btn.btn-edit:hover {
  background: rgba(59, 130, 246, 0.1);
  border-color: #3b82f6;
  color: #3b82f6;
}

.table-btn.btn-delete:hover {
  background: rgba(239, 68, 68, 0.1);
  border-color: #ef4444;
  color: #ef4444;
}

.table-btn.btn-status.btn-enable:hover {
  background: rgba(34, 197, 94, 0.1);
  border-color: #22c55e;
  color: #22c55e;
}

.table-btn.btn-status.btn-disable:hover {
  background: rgba(239, 68, 68, 0.1);
  border-color: #ef4444;
  color: #ef4444;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 1rem;
}

.pagination-total {
  font-size: 0.85rem;
  color: var(--app-text-secondary, #6b7280);
}

.loading-state,
.error-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 3rem 2rem;
  color: var(--app-text-secondary, #6b7280);
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid rgba(255, 223, 201, 0.9);
  border-top-color: #fb7185;
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
  border: 1px solid #fb7185;
  background: transparent;
  color: #fb7185;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.retry-btn:hover {
  background: #fb7185;
  color: white;
}
</style>
