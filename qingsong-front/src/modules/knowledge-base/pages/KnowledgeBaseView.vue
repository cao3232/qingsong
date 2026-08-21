<template>
  <div class="knowledge-base-view">
    <div class="page-header">
      <div class="title-section">
        <h1 class="page-title">知识库</h1>
        <p class="page-subtitle">管理你的知识库、成员与内容状态</p>
      </div>
      <div class="header-actions">
        <n-button quaternary @click="handleRefresh">
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
        <n-button type="primary" @click="handleCreate">
          <template #icon>
            <n-icon size="16">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19" />
                <line x1="5" y1="12" x2="19" y2="12" />
              </svg>
            </n-icon>
          </template>
          新增
        </n-button>
      </div>
    </div>

    <div class="filter-bar">
      <n-input
        v-model:value="keyword"
        placeholder="搜索知识库名称"
        clearable
        class="filter-input"
      />
      <n-select
        v-model:value="statusFilter"
        :options="statusOptions"
        class="filter-select"
        clearable
        placeholder="状态"
      />
    </div>

    <n-grid x-gap="16" y-gap="16" :cols="3" responsive="screen">
      <n-grid-item v-for="item in filteredBases" :key="item.id">
        <n-card class="kb-card" :bordered="false">
          <div class="kb-card-header">
            <div class="kb-icon">
              <svg v-if="item.icon === 'book-2'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M12 6.042A8.967 8.967 0 0 0 6 3.75c-1.052 0-2.062.18-3 .512v14.25A8.987 8.987 0 0 1 6 18c2.305 0 4.408.867 6 2.292m0-14.25a8.966 8.966 0 0 1 6-2.292c1.052 0 2.062.18 3 .512v14.25A8.987 8.987 0 0 0 18 18a8.967 8.967 0 0 0-6 2.292m0-14.25v14.25"/>
              </svg>
              <svg v-else-if="item.icon === 'book'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M12 6.042A8.967 8.967 0 0 0 6 3.75c-1.052 0-2.062.18-3 .512v14.25A8.987 8.987 0 0 1 6 18c2.305 0 4.408.867 6 2.292m0-14.25a8.966 8.966 0 0 1 6-2.292c1.052 0 2.062.18 3 .512v14.25A8.987 8.987 0 0 0 18 18a8.967 8.967 0 0 0-6 2.292m0-14.25v14.25"/>
              </svg>
              <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="m20.25 7.5-.625 10.632a2.25 2.25 0 0 1-2.247 2.118H6.622a2.25 2.25 0 0 1-2.247-2.118L3.75 7.5M10 11.25h4M3.375 7.5h17.25c.621 0 1.125-.504 1.125-1.125v-1.5c0-.621-.504-1.125-1.125-1.125H3.375c-.621 0-1.125.504-1.125 1.125v1.5c0 .621.504 1.125 1.125 1.125Z"/>
              </svg>
            </div>
            <div class="kb-actions">
              <n-button size="small" type="info" @click="handleEdit(item)">编辑</n-button>
              <n-button size="small" type="primary" @click="handleView(item)">详情</n-button>
              <n-popconfirm @positive-click="handleDelete(item)">
                <template #trigger>
                  <n-button size="small" type="error" secondary>删除</n-button>
                </template>
                确定要删除该知识库吗？
              </n-popconfirm>
            </div>
          </div>

          <div class="kb-title">知识库名称：{{ item.name }}</div>
          <div class="kb-meta">
            <div class="meta-row">
              <span class="meta-label">创建人：</span>
              <span>{{ item.owner }}</span>
            </div>
            <div class="meta-row">
              <span class="meta-label">创建时间：</span>
              <span>{{ formatDate(item.createDate) }}</span>
            </div>
          </div>

          <div class="kb-footer">
            <n-switch v-model:value="item.active" :loading="loading" @update:value="(value) => handleToggleActive(item, value)">
              <template #checked>启用</template>
              <template #unchecked>禁用</template>
            </n-switch>
          </div>
        </n-card>
      </n-grid-item>
    </n-grid>

    <n-empty v-if="filteredBases.length === 0" description="暂无知识库" class="empty-state" />

    <n-modal v-model:show="showCreateModal" preset="dialog" :title="isEditMode ? '编辑知识库' : '新建知识库'" :positive-text="isEditMode ? '保存' : '创建'" negative-text="取消" @positive-click="handleConfirmCreate">
      <n-form>
        <n-form-item label="知识库名称" required>
          <n-input v-model:value="newKbName" placeholder="请输入知识库名称" />
        </n-form-item>
        <n-form-item label="描述">
          <n-input v-model:value="newKbDesc" type="textarea" placeholder="请输入知识库描述（可选）" :rows="3" />
        </n-form-item>
      </n-form>
    </n-modal>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NCard, NGrid, NGridItem, NTag, NInput, NSelect, NEmpty, NPopconfirm, NIcon, NModal, NForm, NFormItem, NSwitch, useMessage } from 'naive-ui'
import { knowledgeAPI } from '@/modules/knowledge-base/services'

const router = useRouter()
const message = useMessage()

const keyword = ref('')
const statusFilter = ref(null)
const loading = ref(false)
const showCreateModal = ref(false)
const isEditMode = ref(false)
const editingKbId = ref(null)
const newKbName = ref('')
const newKbDesc = ref('')

const statusOptions = [
  { label: '启用', value: true },
  { label: '禁用', value: false }
]

const knowledgeBases = ref([])

const fetchKnowledgeBases = async () => {
  loading.value = true
  try {
    const data = await knowledgeAPI.getBases(statusFilter.value)
    knowledgeBases.value = data.map(item => ({
      ...item,
      owner: 'root',
      status: item.active ? 'active' : 'archived',
      icon: 'book-2',
      members: []
    }))
  } catch (error) {
    message.error('获取知识库列表失败')
  } finally {
    loading.value = false
  }
}

const searchKnowledgeBases = async () => {
  if (!keyword.value.trim()) {
    fetchKnowledgeBases()
    return
  }
  loading.value = true
  try {
    const data = await knowledgeAPI.searchBases(keyword.value.trim())
    knowledgeBases.value = data.map(item => ({
      ...item,
      owner: 'root',
      status: item.active ? 'active' : 'archived',
      icon: 'book-2',
      members: []
    }))
  } catch (error) {
    message.error('搜索失败')
  } finally {
    loading.value = false
  }
}

let searchTimer = null
watch(keyword, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(searchKnowledgeBases, 300)
})

watch(statusFilter, () => {
  fetchKnowledgeBases()
})

const filteredBases = computed(() => {
  return knowledgeBases.value
})

const handleRefresh = () => {
  keyword.value = ''
  statusFilter.value = null
  fetchKnowledgeBases()
  message.success('已刷新')
}

const handleCreate = () => {
  isEditMode.value = false
  editingKbId.value = null
  newKbName.value = ''
  newKbDesc.value = ''
  showCreateModal.value = true
}

const handleConfirmCreate = async () => {
  if (!newKbName.value.trim()) {
    message.warning('请输入知识库名称')
    return
  }
  try {
    if (isEditMode.value && editingKbId.value !== null) {
      await knowledgeAPI.updateBase({
        id: editingKbId.value,
        name: newKbName.value.trim(),
        description: newKbDesc.value.trim()
      })
      message.success('更新成功')
    } else {
      await knowledgeAPI.createBase(newKbName.value.trim(), newKbDesc.value.trim())
      message.success('创建成功')
    }
    showCreateModal.value = false
    isEditMode.value = false
    editingKbId.value = null
    newKbName.value = ''
    newKbDesc.value = ''
    fetchKnowledgeBases()
  } catch (error) {
    message.error(isEditMode.value ? '更新失败' : '创建失败')
  }
}

const handleView = (item) => {
  if (loading.value) return
  router.push(`/knowledge-base/${item.id}`)
}

const handleEdit = (item) => {
  if (loading.value) return

  isEditMode.value = true
  editingKbId.value = item.id
  newKbName.value = item.name || ''
  newKbDesc.value = item.description || ''
  showCreateModal.value = true
}

const handleDelete = async (item) => {
  try {
    await knowledgeAPI.deleteBase(item.id)
    message.success('已删除')
    fetchKnowledgeBases()
  } catch (error) {
    message.error('删除失败')
  }
}

const handleToggleActive = async (item, value) => {
  const previousValue = !value
  try {
    await knowledgeAPI.updateStatus([item.id], value)
    message.success(value ? '已启用' : '已禁用')
  } catch (error) {
    item.active = previousValue
    message.error('状态更新失败')
  }
}

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).replace(/\//g, '-')
}

onMounted(() => {
  fetchKnowledgeBases()
})
</script>

<style scoped>
.knowledge-base-view {
  min-height: 100vh;
  padding: 28px 28px 40px;
  background: var(--app-background, #f7f8fa);
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.title-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.page-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: var(--app-text-primary, #1f2937);
}

.page-subtitle {
  margin: 0;
  color: var(--app-text-secondary, #6b7280);
  font-size: 13px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.filter-bar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
}

.filter-input {
  max-width: 280px;
}

.filter-select {
  width: 140px;
}

.kb-card {
  border-radius: 14px;
  background: var(--app-component-bg, #ffffff);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
  border: 1px solid rgba(226, 232, 240, 0.8);
}

.kb-card :deep(.n-card__content) {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.kb-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.kb-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: linear-gradient(135deg, #dbeafe 0%, #bfdbfe 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #2563eb;
}

.kb-icon svg {
  width: 26px;
  height: 26px;
}

.kb-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.kb-title {
  font-weight: 600;
  color: var(--app-text-primary, #1f2937);
}

.kb-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: var(--app-text-secondary, #6b7280);
  font-size: 13px;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.meta-label {
  color: var(--app-text-muted, #94a3b8);
}

.kb-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.member-group {
  display: flex;
  align-items: center;
  gap: -4px;
}

.member-avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  border: 2px solid white;
  margin-left: -6px;
  cursor: pointer;
  transition: transform 0.2s ease;
}

.member-avatar:first-child {
  margin-left: 0;
}

.member-avatar:hover {
  transform: scale(1.15);
  z-index: 1;
}

.empty-state {
  margin-top: 32px;
}

@media (max-width: 1200px) {
  .knowledge-base-view :deep(.n-grid) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .knowledge-base-view {
    padding: 20px 16px 28px;
  }

  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .filter-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .knowledge-base-view :deep(.n-grid) {
    grid-template-columns: 1fr;
  }
}
</style>
