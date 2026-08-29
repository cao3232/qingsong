<template>
  <div class="role-manage-content">
    <div class="panel-header">
      <div class="header-content">
        <div>
          <h2>角色管理</h2>
          <p>管理 AI 对话角色的配置</p>
        </div>
        <button @click="openRoleModal()" class="btn-primary">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"/>
            <line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
          新建角色
        </button>
      </div>
    </div>

    <div class="config-form">
      <!-- 加载状态 -->
      <div v-if="roleLoading" class="loading-state">
        <div class="loading-spinner"></div>
        <p>加载中...</p>
      </div>

      <!-- 错误状态 -->
      <div v-else-if="roleError" class="error-state">
        <p>{{ roleError }}</p>
        <button @click="loadRoles" class="retry-btn">重试</button>
      </div>

      <!-- 空状态 -->
      <div v-else-if="roles.length === 0" class="empty-state">
        <p>暂无角色配置</p>
        <button @click="openRoleModal()" class="btn-primary">立即创建</button>
      </div>

      <!-- 角色列表 -->
      <div v-else class="role-table-wrapper">
        <!-- 批量操作栏 -->
        <div v-if="selectedRoleIds.length > 0" class="batch-actions-bar">
          <div class="batch-actions-info">
            <span class="batch-count">{{ selectedRoleIds.length }}</span>
            <span class="batch-text">已选择 {{ selectedRoleIds.length }} 项</span>
          </div>
          <div class="batch-actions-buttons">
            <button class="batch-btn batch-cancel" type="button" @click="clearRoleSelection">取消选择</button>
            <button class="batch-btn batch-delete" type="button" :disabled="roleBatchDeleting"
              @click="batchDeleteRoles">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                stroke-linecap="round" stroke-linejoin="round">
                <polyline points="3 6 5 6 21 6" />
                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
              </svg>
              {{ roleBatchDeleting ? '删除中...' : '批量删除' }}
            </button>
          </div>
        </div>

        <div
          class="role-table-scroll scrollbar-sm"
          ref="roleListRef"
          @scroll="handleRoleListScroll"
        >
          <div class="list-progress" aria-hidden="true">
            <span :style="{ width: roleListProgress + '%' }"></span>
          </div>
          <table class="model-table">
            <colgroup>
              <col style="width: 40px;" />
              <col style="width: 190px;" />
              <col />
              <col style="width: 90px;" />
              <col style="width: 70px;" />
              <col style="width: 80px;" />
              <col style="width: 150px;" />
              <col style="width: 130px;" />
            </colgroup>
            <thead>
              <tr>
                <th class="checkbox-col">
                  <n-checkbox
                    :checked="isAllRolesSelected"
                    :indeterminate="isSomeRolesSelected && !isAllRolesSelected"
                    @update:checked="toggleAllRoles"
                  />
                </th>
                <th>角色名称</th>
                <th>描述</th>
                <th>排序</th>
                <th>收藏</th>
                <th>会话数</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="role in roles" :key="role.id" class="model-row" :class="{ 'row-selected': selectedRoleIds.includes(role.id) }">
                <td class="checkbox-col">
                  <n-checkbox
                    :checked="selectedRoleIds.includes(role.id)"
                    @update:checked="(checked) => toggleRoleSelection(role.id, checked)"
                  />
                </td>
                <td>
                  <div class="role-name-cell">
                    <img class="role-avatar" :src="roleAvatarUrl(role)" alt="" loading="lazy" />
                    <span class="model-name">{{ role.name }}</span>
                  </div>
                </td>
                <td class="role-desc-cell">
                  <span class="role-desc" :title="role.description">{{ role.description || '-' }}</span>
                </td>
                <td style="text-align: center;">
                  <span class="sort-chip">{{ role.sort || 0 }}</span>
                </td>
                <td>
                  <span class="favor-badge" :class="{ favorited: !!role.favor }"
                    :title="role.favor ? '已收藏' : '未收藏'">★</span>
                </td>
                <td>
                  <span class="session-badge">{{ role.sessionCount || 0 }}</span>
                </td>
                <td class="date-cell">{{ formatDate(role.updateDate) }}</td>
                <td class="actions-cell">
                  <button
                    @click="openRoleModal(role)"
                    class="table-btn btn-edit"
                    title="编辑"
                  >
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                      <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                    </svg>
                  </button>
                  <button
                    @click="copyRole(role)"
                    class="table-btn btn-copy"
                    title="复制"
                  >
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
                      <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                    </svg>
                  </button>
                  <button
                    @click="deleteRole(role)"
                    class="table-btn btn-delete"
                    title="删除"
                  >
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polyline points="3 6 5 6 21 6"/>
                      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                    </svg>
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>

  <!-- 角色编辑弹窗 -->
  <n-modal
    v-model:show="showRoleModal"
    preset="dialog"
    :title="editingRole ? '编辑角色' : '新建角色'"
    :positive-text="roleSaving ? '保存中...' : '保存'"
    :negative-text="'取消'"
    :loading="roleSaving"
    :disabled="roleSaving"
    @positive-click="saveRole"
    @negative-click="closeRoleModal"
    style="width: min(680px, calc(100vw - 24px));"
  >
    <div class="role-form">
      <div class="role-form-grid">
        <div class="role-form-field">
          <label class="role-form-label">角色名称 <span class="required">*</span></label>
          <n-input v-model:value="roleForm.name" placeholder="请输入角色名称" />
        </div>
        <div class="role-form-field">
          <label class="role-form-label">排序</label>
          <n-input-number v-model:value="roleForm.sort" :min="0" placeholder="0" style="width: 100%;" />
        </div>
      </div>
      <div class="role-form-field">
        <label class="role-form-label">角色描述</label>
        <n-input v-model:value="roleForm.description" placeholder="请输入角色描述" />
      </div>
      <div class="role-form-field">
        <label class="role-form-label">角色提示词（System Prompt）</label>
        <n-input v-model:value="roleForm.value" type="textarea" class="prompt-textarea"
          placeholder="请输入角色提示词" :rows="8" />
      </div>
      <div class="role-form-field">
        <label class="role-form-label">英文提示词（可选）</label>
        <n-input v-model:value="roleForm.valueEn" type="textarea" class="prompt-textarea"
          placeholder="请输入英文角色提示词" :rows="4" />
      </div>
    </div>
  </n-modal>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { useMessage, useDialog, NModal, NInput, NInputNumber, NCheckbox } from 'naive-ui'

const message = useMessage()
const dialog = useDialog()

// API
import { API_BASE_URL } from '@/config/env'
import http from '@/utils/http'
import { generateAIAvatar } from '../../../shared/utils/index.js'

const ROLE_API = API_BASE_URL + '/admin/roles'

// 角色头像：按角色 ID/名称确定性生成（与聊天页角色头像风格一致）
const roleAvatarUrl = (role) => generateAIAvatar(role?.id || role?.name || 'role')

// 角色管理状态
const roles = ref([])
const roleLoading = ref(false)
const roleError = ref(null)
const showRoleModal = ref(false)
const editingRole = ref(null)
const roleSaving = ref(false)
const roleForm = ref({
  name: '',
  description: '',
  value: '',
  valueEn: '',
  sort: 0
})

// 角色多选状态
const selectedRoleIds = ref([])
const roleBatchDeleting = ref(false)

// 列表滚动进度
const roleListRef = ref(null)
const roleListProgress = ref(0)

const handleRoleListScroll = () => {
  if (!roleListRef.value) {
    roleListProgress.value = 0
    return
  }
  const el = roleListRef.value
  const maxScroll = el.scrollHeight - el.clientHeight
  roleListProgress.value = maxScroll > 0 ? Math.round((el.scrollTop / maxScroll) * 100) : 0
}

// 格式化日期
const formatDate = (dateString) => {
  if (!dateString) return '-'
  try {
    const date = new Date(dateString)
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    })
  } catch (e) {
    return dateString
  }
}

// 加载角色列表
const loadRoles = async () => {
  roleLoading.value = true
  roleError.value = null
  try {
    const result = await http.get(`${ROLE_API}/all`)
    if (result.ok === 1 && result.data) {
      roles.value = result.data
    } else {
      roles.value = []
    }
  } catch (err) {
    console.error('加载角色列表失败:', err)
    roleError.value = '加载失败'
    roles.value = []
  } finally {
    roleLoading.value = false
  }
}

// 打开角色弹窗
const openRoleModal = async (role = null) => {
  if (role) {
    editingRole.value = role
    // 查找角色的详细信息
    const result = await http.get(`${ROLE_API}/${editingRole.value.id}`)
    if (result.ok === 1) {
      role = result.data
    }
    roleForm.value = {
      name: role.name,
      description: role.description || '',
      value: role.value || '',
      valueEn: role.valueEn || '',
      sort: role.sort || 0
    }
  } else {
    editingRole.value = null
    roleForm.value = {
      name: '',
      description: '',
      value: '',
      valueEn: '',
      sort: 0
    }
  }
  showRoleModal.value = true
}

// 关闭角色弹窗
const closeRoleModal = () => {
  showRoleModal.value = false
  editingRole.value = null
  roleForm.value = {
    name: '',
    description: '',
    value: '',
    valueEn: '',
    sort: 0
  }
}

// 保存角色
const saveRole = async () => {
  if (!roleForm.value.name) {
    message.warning('请填写角色名称')
    return false
  }

  roleSaving.value = true
  try {
    const body = {
      id: editingRole.value ? editingRole.value.id : null,
      name: roleForm.value.name,
      description: roleForm.value.description,
      value: roleForm.value.value,
      valueEn: roleForm.value.valueEn,
      sort: roleForm.value.sort
    }

    let result
    if (editingRole.value) {
      result = await http.put(`${ROLE_API}`, body)
    } else {
      result = await http.post(ROLE_API, body)
    }

    if (result.ok === 1) {
      message.success(editingRole.value ? '更新成功' : '创建成功')
      closeRoleModal()
      await loadRoles()
      return true
    } else {
      message.error(result.msg || '操作失败')
      return false
    }
  } catch (err) {
    console.error('保存角色失败:', err)
    message.error('保存失败')
    return false
  } finally {
    roleSaving.value = false
  }
}

// 复制角色
const copyRole = (role) => {
  editingRole.value = null
  roleForm.value = {
    name: role.name + ' (副本)',
    description: role.description || '',
    value: role.value || '',
    valueEn: role.valueEn || '',
    sort: role.sort || 0
  }
  showRoleModal.value = true
}

// 删除角色
const deleteRole = (role) => {
  dialog.warning({
    title: '删除确认',
    content: `确定要删除角色 "${role.name}" 吗？此操作不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const result = await http.delete(ROLE_API, { data: [role.id] })
        if (result.ok === 1) {
          message.success('删除成功')
          await loadRoles()
        } else {
          message.error(result.msg || '删除失败')
        }
      } catch (err) {
        console.error('删除角色失败:', err)
        message.error('删除失败')
      }
    }
  })
}

// 角色多选相关
const isAllRolesSelected = computed(() => {
  return roles.value.length > 0 && selectedRoleIds.value.length === roles.value.length
})

const isSomeRolesSelected = computed(() => {
  return selectedRoleIds.value.length > 0
})

const toggleRoleSelection = (roleId, checked) => {
  if (checked) {
    selectedRoleIds.value.push(roleId)
  } else {
    const index = selectedRoleIds.value.indexOf(roleId)
    if (index !== -1) {
      selectedRoleIds.value.splice(index, 1)
    }
  }
}

const toggleAllRoles = (checked) => {
  if (checked) {
    selectedRoleIds.value = roles.value.map(r => r.id)
  } else {
    selectedRoleIds.value = []
  }
}

const clearRoleSelection = () => {
  selectedRoleIds.value = []
}

// 批量删除角色
const batchDeleteRoles = () => {
  if (selectedRoleIds.value.length === 0) return

  dialog.warning({
    title: '批量删除确认',
    content: `确定要删除选中的 ${selectedRoleIds.value.length} 个角色吗？此操作不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      roleBatchDeleting.value = true
      try {
        const result = await http.delete(ROLE_API, { data: selectedRoleIds.value })
        if (result.ok === 1) {
          message.success(`成功删除 ${selectedRoleIds.value.length} 个角色`)
          selectedRoleIds.value = []
          await loadRoles()
        } else {
          message.error(result.msg || '批量删除失败')
        }
      } catch (err) {
        console.error('批量删除角色失败:', err)
        message.error('批量删除失败')
      } finally {
        roleBatchDeleting.value = false
      }
    }
  })
}

onMounted(() => {
  loadRoles()
  nextTick(() => {
    handleRoleListScroll()
  })
})
</script>

<style scoped>
.role-manage-content {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 160px);
  overflow: hidden;
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
  flex: 1;
  min-height: 0;
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
  to { transform: rotate(360deg); }
}

.retry-btn {
  margin-top: 1rem;
  padding: 0.5rem 1.5rem;
  border: 1px solid var(--app-active-bg, #3b82f6);
  border-radius: 6px;
  background: transparent;
  color: var(--app-active-bg, #3b82f6);
  cursor: pointer;
  transition: all 0.2s ease;
}

.retry-btn:hover {
  background: var(--app-active-bg, #3b82f6);
  color: white;
}

/* 角色表格容器 */
.role-table-wrapper {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  gap: 0.75rem;
}

.role-table-scroll {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: auto;
  border-radius: 18px;
  border: 1px solid var(--app-border-color, #e5e7eb);
  background: var(--app-component-bg, rgba(255, 255, 255, 0.8));
}

/* 列表滚动进度条 */
.list-progress {
  position: sticky;
  top: 0;
  left: 0;
  height: 3px;
  z-index: 20;
  background: transparent;
  pointer-events: none;
}
.list-progress span {
  display: block;
  height: 100%;
  width: 0;
  background: var(--app-active-bg, #3b82f6);
  transition: width 0.1s;
}

/* 数据表格 */
.model-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  font-size: 0.875rem;
}
.model-table thead {
  background: var(--app-bg-secondary, #f9fafb);
  position: sticky;
  top: 0;
  z-index: 10;
}
.model-table th {
  padding: 0.875rem 1.25rem;
  text-align: left;
  font-weight: 600;
  color: var(--app-text-primary, #1f2937);
  font-size: 0.8rem;
  border-bottom: 2px solid var(--app-border-color, #e5e7eb);
  white-space: nowrap;
}
.model-table tbody tr {
  border-bottom: 1px solid var(--app-border-color, #e5e7eb);
  transition: background-color 0.15s ease;
}
.model-table tbody tr:hover {
  background: var(--app-hover-bg, rgba(59, 130, 246, 0.05));
}
.model-table tbody tr:last-child {
  border-bottom: none;
}
.model-table tbody tr:nth-child(even) {
  background: rgba(249, 250, 251, 0.5);
}
.model-table td {
  padding: 0.75rem 1.25rem;
  color: var(--app-text-primary, #374151);
  vertical-align: middle;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 角色名称 */
.model-name {
  font-weight: 600;
  color: var(--app-text-primary, #1f2937);
}

/* 角色描述 */
.role-desc-cell {
  overflow: hidden;
}
.role-desc {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 0.85rem;
  color: var(--app-text-secondary, #6b7280);
}

/* 角色名称（含头像） */
.role-name-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.role-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  flex-shrink: 0;
  object-fit: cover;
  background: var(--app-bg-secondary, #f3f4f6);
  border: 1px solid var(--app-border-color, #e5e7eb);
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

/* 收藏徽标 */
.favor-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  color: #cbd5e1;
  background: var(--app-bg-secondary, #f3f4f6);
  font-size: 13px;
  line-height: 1;
  transition: all 0.2s ease;
}
.favor-badge.favorited {
  color: #f59e0b;
  background: rgba(245, 158, 11, 0.14);
  box-shadow: 0 0 0 1px rgba(245, 158, 11, 0.22);
}

/* 会话数徽标 */
.session-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 9px;
  border-radius: 999px;
  font-size: 0.8rem;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: #b45309;
  background: rgba(251, 146, 60, 0.12);
}

/* 日期 */
.date-cell {
  font-size: 0.8rem;
  color: var(--app-text-secondary, #6b7280);
}

/* 操作按钮 */
.actions-cell {
  white-space: nowrap;
  overflow: visible !important;
}

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
.table-btn.btn-delete {
  color: #ef4444;
}
.table-btn.btn-delete:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.1);
  border-color: #ef4444;
  color: #dc2626;
}
.table-btn:not(:last-child) {
  margin-right: 0.5rem;
}

/* 多选框样式 */
.checkbox-col {
  width: 40px;
  text-align: center;
}

/* 行选中样式 */
.row-selected {
  background: var(--app-active-bg, rgba(59, 130, 246, 0.05)) !important;
}

/* 批量操作栏 */
.batch-actions-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  background: linear-gradient(135deg, rgba(255, 184, 108, 0.16), rgba(251, 113, 133, 0.12));
  border: 1px solid rgba(251, 191, 36, 0.28);
  border-radius: 18px;
  font-size: 0.875rem;
  color: var(--app-text-primary, #1f2937);
  flex-shrink: 0;
  flex-wrap: wrap;
}

.batch-actions-info {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.batch-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 22px;
  padding: 0 6px;
  border-radius: 999px;
  background: linear-gradient(135deg, #ffb86c 0%, #fb7185 100%);
  color: #ffffff;
  font-size: 0.8rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.batch-text {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--app-text-primary, #374151);
}

.batch-actions-buttons {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.batch-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border-radius: 999px;
  border: 1px solid transparent;
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}
.batch-btn svg {
  width: 14px;
  height: 14px;
}

.batch-cancel {
  background: rgba(255, 255, 255, 0.7);
  border-color: var(--app-border-color, #e5e7eb);
  color: var(--app-text-secondary, #6b7280);
}
.batch-cancel:hover {
  color: var(--app-text-primary, #374151);
  border-color: var(--app-border-color, #d1d5db);
  background: rgba(255, 255, 255, 0.92);
}

.batch-delete {
  background: linear-gradient(135deg, #fb7185 0%, #f43f5e 100%);
  color: #ffffff;
  box-shadow: 0 8px 16px rgba(244, 63, 94, 0.24);
}
.batch-delete:hover:not(:disabled) {
  filter: brightness(1.08);
  box-shadow: 0 10px 20px rgba(244, 63, 94, 0.3);
}
.batch-delete:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 角色编辑弹窗表单 */
.role-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-top: 2px;
}
.role-form-grid {
  display: grid;
  grid-template-columns: 1fr 160px;
  gap: 14px;
}
.role-form-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.role-form-label {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--app-text-primary, #374151);
}
.role-form-label .required {
  color: #ef4444;
}
:deep(.prompt-textarea textarea) {
  font-family: var(--code-font-family, ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace);
  font-size: 13px;
  line-height: 1.6;
}
@media (max-width: 520px) {
  .role-form-grid {
    grid-template-columns: 1fr;
  }
}

/* 暗色模式 */
:global(.dark) .role-table-scroll {
  background: var(--app-component-bg, rgba(30, 30, 30, 0.8));
}
:global(.dark) .role-avatar {
  background: var(--app-bg-secondary, rgba(30, 30, 30, 0.8));
}
:global(.dark) .favor-badge {
  background: rgba(255, 255, 255, 0.06);
}
:global(.dark) .session-badge {
  background: rgba(251, 146, 60, 0.18);
}
:global(.dark) .role-form-label {
  color: var(--app-text-primary, #e5e7eb);
}
:global(.dark) .role-desc {
  color: var(--app-text-secondary, #9ca3af);
}
:global(.dark) .batch-actions-bar {
  background: linear-gradient(135deg, rgba(255, 184, 108, 0.14), rgba(251, 113, 133, 0.12));
  border-color: rgba(251, 191, 36, 0.2);
}
:global(.dark) .batch-cancel {
  background: rgba(255, 255, 255, 0.06);
  border-color: rgba(255, 255, 255, 0.12);
  color: var(--app-text-secondary, #9ca3af);
}
:global(.dark) .batch-cancel:hover {
  color: var(--app-text-primary, #e5e7eb);
  background: rgba(255, 255, 255, 0.1);
}
:global(.dark) .model-table tbody tr:nth-child(even) {
  background: rgba(255, 255, 255, 0.03);
}

/* 小屏适配 */
@media (max-width: 640px) {
  .role-manage-content {
    height: auto;
    padding: 0 4px 12px;
  }
  .header-content {
    flex-direction: column;
    align-items: flex-start;
  }
  .batch-actions-bar {
    flex-wrap: wrap;
  }
  .role-table-scroll {
    max-height: calc(100vh - 320px);
  }
}
</style>
