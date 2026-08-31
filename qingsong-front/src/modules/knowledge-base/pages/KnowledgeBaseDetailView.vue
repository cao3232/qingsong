<template>
  <div class="kb-detail-view">
    <!-- 头部区域：标题 + 上传 -->
    <div class="header-card">
      <div class="header-top">
        <div class="header-left">
          <n-button quaternary @click="goBack" class="back-btn">
            <template #icon>
              <n-icon size="18">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M19 12H5M12 19l-7-7 7-7"/>
                </svg>
              </n-icon>
            </template>
            返回
          </n-button>
          <div class="title-section">
            <h1 class="page-title">{{ knowledgeBase?.name || '知识库详情' }}</h1>
            <p class="page-subtitle">管理知识库文档，支持上传 PDF、Word、TXT、Markdown 等格式文件</p>
          </div>
        </div>
        <div class="header-actions">
          <n-button quaternary :disabled="loading" v-if="embeddedCount < total" @click="handleReEmbedPending">
            <template #icon>
              <n-icon size="16">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                  <polyline points="22 4 12 14.01 9 11.01"/>
                </svg>
              </n-icon>
            </template>
            重试待处理
          </n-button>
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
        </div>
      </div>
      
      <!-- 上传区域整合到头部 -->
      <div class="upload-area">
        <n-upload
          v-model:file-list="uploadFileList"
          multiple
          :max="50"
          :custom-request="handleUpload"
          :show-file-list="false"
        >
          <n-upload-dragger class="upload-dragger">
            <div class="upload-content">
              <div class="upload-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                  <polyline points="17 8 12 3 7 8"/>
                  <line x1="12" y1="3" x2="12" y2="15"/>
                </svg>
              </div>
              <div class="upload-text">
                <span class="upload-title">点击或拖拽文件上传</span>
                <span class="upload-hint">支持 PDF、Word、TXT、Markdown，单个文件不超过 50MB</span>
              </div>
            </div>
          </n-upload-dragger>
        </n-upload>
      </div>
      
      <!-- 统计信息 -->
      <div class="stats-row">
        <div class="stat-item">
          <span class="stat-icon files">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14 2 14 8 20 8"/>
            </svg>
          </span>
          <div class="stat-content">
            <span class="stat-value">{{ total }}</span>
            <span class="stat-label">文件总数</span>
          </div>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-item">
          <span class="stat-icon size">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
            </svg>
          </span>
          <div class="stat-content">
            <span class="stat-value">{{ formatSize(totalSize) }}</span>
            <span class="stat-label">总大小</span>
          </div>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-item">
          <span class="stat-icon processed">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
              <polyline points="22 4 12 14.01 9 11.01"/>
            </svg>
          </span>
          <div class="stat-content">
            <span class="stat-value">{{ embeddedCount }}</span>
            <span class="stat-label">已处理</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 搜索和筛选 -->
    <div class="filter-bar">
      <n-input
        v-model:value="searchKeyword"
        placeholder="搜索文件名..."
        clearable
        class="search-input"
      >
        <template #prefix>
          <n-icon size="16">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="11" cy="11" r="8"/>
              <path d="M21 21l-4.35-4.35"/>
            </svg>
          </n-icon>
        </template>
      </n-input>
      <n-select
        v-model:value="statusFilter"
        :options="statusOptions"
        class="filter-select"
        clearable
        placeholder="状态"
      />
      <n-select
        v-model:value="typeFilter"
        :options="typeOptions"
        class="filter-select"
        clearable
        placeholder="类型"
      />
    </div>

    <!-- 文件列表 -->
    <div class="file-list" v-if="filteredFiles.length > 0">
      <div v-for="file in filteredFiles" :key="file.id" class="file-card">
        <div class="file-item">
          <div class="file-icon" :class="getFileTypeClass(file.type)">
            <svg v-if="file.type === 'pdf'" viewBox="0 0 24 24" fill="currentColor">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6zm-1 2l5 5h-5V4zM8.5 13c.28 0 .5.22.5.5v3c0 .28-.22.5-.5.5s-.5-.22-.5-.5v-3c0-.28.22-.5.5-.5zm3 0c.28 0 .5.22.5.5v2h.5c.28 0 .5.22.5.5s-.22.5-.5.5h-1c-.28 0-.5-.22-.5-.5v-2.5c0-.28.22-.5.5-.5zm3 0h1c.28 0 .5.22.5.5s-.22.5-.5.5H15v.5h.5c.28 0 .5.22.5.5s-.22.5-.5.5H15v.5c0 .28-.22.5-.5.5s-.5-.22-.5-.5v-3c0-.28.22-.5.5-.5z"/>
            </svg>
            <svg v-else-if="file.type === 'word'" viewBox="0 0 24 24" fill="currentColor">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6zm-1 2l5 5h-5V4zM9.5 13l1 4 1-4h1l1 4 1-4h1l-1.5 6h-1l-1-4-1 4h-1L8.5 13h1z"/>
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="currentColor">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6zm-1 2l5 5h-5V4zM8 13h8v1H8v-1zm0 2h8v1H8v-1zm0 2h5v1H8v-1z"/>
            </svg>
          </div>
          <div class="file-info">
            <div class="file-name">{{ file.name }}</div>
            <div class="file-meta">
              <span>{{ formatSize(file.size) }}</span>
              <span class="separator">·</span>
              <span>{{ file.uploadTime }}</span>
            </div>
          </div>
          <div class="file-right">
            <n-tag :type="getStatusType(file.status)" size="small" round>
              {{ getStatusText(file.status) }}
            </n-tag>
            <div class="file-actions">
              <n-button v-if="file.status === 'pending'" quaternary circle size="small" type="info" @click="handleReEmbedFile(file)">
                <template #icon>
                  <n-icon size="16">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                      <polyline points="22 4 12 14.01 9 11.01"/>
                    </svg>
                  </n-icon>
                </template>
              </n-button>
              <n-button quaternary circle size="small" @click="handleDownload(file)">
                <template #icon>
                  <n-icon size="16">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                      <polyline points="7 10 12 15 17 10"/>
                      <line x1="12" y1="15" x2="12" y2="3"/>
                    </svg>
                  </n-icon>
                </template>
              </n-button>
              <n-popconfirm @positive-click="handleDeleteFile(file)">
                <template #trigger>
                  <n-button quaternary circle size="small" type="error">
                    <template #icon>
                      <n-icon size="16">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                          <polyline points="3 6 5 6 21 6"/>
                          <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                        </svg>
                      </n-icon>
                    </template>
                  </n-button>
                </template>
                确定删除此文件吗？
              </n-popconfirm>
            </div>
          </div>
        </div>
      </div>
    </div>

    <n-empty v-else description="暂无文件，请上传文档" class="empty-state">
      <template #extra>
        <n-button type="primary" @click="triggerUpload">
          <template #icon>
            <n-icon size="16">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                <polyline points="17 8 12 3 7 8"/>
                <line x1="12" y1="3" x2="12" y2="15"/>
              </svg>
            </n-icon>
          </template>
          上传文件
        </n-button>
      </template>
    </n-empty>

    <div v-if="total > 0" class="pagination-bar">
      <span class="pagination-total">共 {{ total }} 个文件</span>
      <n-pagination
        v-model:page="pageNum"
        :page-size="pageSize"
        :item-count="total"
        @update:page="loadDocuments"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NUpload, NUploadDragger, NInput, NSelect, NTag, NEmpty, NIcon, NPopconfirm, NPagination, NSpin, useMessage } from 'naive-ui'
import { knowledgeAPI, documentAPI } from '@/modules/knowledge-base/services'

const route = useRoute()
const router = useRouter()
const message = useMessage()

const searchKeyword = ref('')
const statusFilter = ref(null)
const typeFilter = ref(null)
const loading = ref(false)
const uploadFileList = ref([]) // 手动控制上传组件的文件列表
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const totalSize = ref(0)
const embeddedCount = ref(0)

const knowledgeBase = ref(null)
const fileList = ref([])

const statusOptions = [
  { label: '已嵌入', value: true },
  { label: '待处理', value: false }
]

const typeOptions = [
  { label: 'PDF', value: 'pdf' },
  { label: 'Word', value: 'word' },
  { label: 'TXT', value: 'txt' },
  { label: 'Markdown', value: 'md' }
]

// 获取文件类型
const getFileType = (fileName) => {
  const ext = fileName.split('.').pop().toLowerCase()
  const typeMap = {
    pdf: 'pdf',
    doc: 'word',
    docx: 'word',
    txt: 'txt',
    md: 'md',
    markdown: 'md'
  }
  return typeMap[ext] || 'txt'
}

// 获取知识库信息
const fetchKnowledgeBase = async () => {
  const id = route.params.id
  try {
    const bases = await knowledgeAPI.getBases()
    const kb = bases.find(b => b.id == id)
    if (kb) {
      knowledgeBase.value = kb
    }
  } catch (error) {
    console.error('获取知识库信息失败', error)
  }
}

// 获取文档列表（分页）
const mapDocument = (doc) => ({
  id: doc.id,
  knowledgeId: doc.knowledgeId,
  name: doc.fileName,
  path: doc.path,
  sourceId: doc.sourceId,
  type: getFileType(doc.fileName),
  embedding: doc.embedding,
  status: doc.embedding ? 'processed' : 'pending',
  size: doc.size || 0,
  uploadTime: formatDate(doc.createDate)
})

const loadDocuments = async () => {
  const knowledgeId = route.params.id
  loading.value = true
  try {
    const keyword = searchKeyword.value.trim()
    const data = keyword
      ? await documentAPI.searchDocuments(knowledgeId, keyword, pageNum.value, pageSize.value, statusFilter.value, typeFilter.value)
      : await documentAPI.getDocuments(knowledgeId, pageNum.value, pageSize.value, statusFilter.value, typeFilter.value)
    fileList.value = (data.records || []).map(mapDocument)
    total.value = data.total || 0
    totalSize.value = data.totalSize || 0
    embeddedCount.value = data.embeddedCount || 0
  } catch (error) {
    message.error(searchKeyword.value.trim() ? '搜索失败' : '获取文档列表失败')
  } finally {
    loading.value = false
  }
}

// 监听搜索关键词变化
let searchTimer = null
watch(searchKeyword, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    pageNum.value = 1
    loadDocuments()
  }, 300)
})

// 筛选变化时回到第一页并重新加载（服务端过滤，保证统计与列表口径一致）
watch([statusFilter, typeFilter], () => {
  pageNum.value = 1
  loadDocuments()
})

// 格式化日期
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

const filteredFiles = computed(() => {
  return fileList.value.filter(file => {
    const matchStatus = statusFilter.value === null || statusFilter.value === undefined || file.embedding === statusFilter.value
    const matchType = !typeFilter.value || file.type === typeFilter.value
    return matchStatus && matchType
  })
})

const formatSize = (bytes) => {
  if (bytes === null || bytes === undefined || Number.isNaN(Number(bytes))) return '0 B'
  bytes = Number(bytes)
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(2) + ' MB'
}

const getFileTypeClass = (type) => {
  const classMap = {
    pdf: 'type-pdf',
    word: 'type-word',
    txt: 'type-txt',
    md: 'type-md'
  }
  return classMap[type] || 'type-default'
}

const getStatusType = (status) => {
  const typeMap = {
    processed: 'success',
    processing: 'warning',
    pending: 'default',
    failed: 'error'
  }
  return typeMap[status] || 'default'
}

const getStatusText = (status) => {
  const textMap = {
    processed: '已处理',
    processing: '处理中',
    pending: '待处理',
    failed: '失败'
  }
  return textMap[status] || status
}

const goBack = () => {
  // 使用 back() 返回上一页，避免历史记录堆积
  router.back()
}

const handleRefresh = async () => {
  await loadDocuments()
  message.success('已刷新')
}

const handleReEmbedFile = async (file) => {
  try {
    const ok = await documentAPI.reEmbedDocument(file.id)
    if (ok) {
      message.success(`「${file.name}」重新嵌入成功`)
    } else {
      message.error(`「${file.name}」重新嵌入失败`)
    }
  } catch (error) {
    message.error(`重新嵌入失败: ${error.message}`)
  }
  await loadDocuments()
}

const handleReEmbedPending = async () => {
  loading.value = true
  try {
    const count = await documentAPI.reEmbedPending(route.params.id)
    message.success(`已重新嵌入 ${count} 个文件`)
  } catch (error) {
    message.error(`重试失败: ${error.message}`)
  } finally {
    loading.value = false
  }
  await loadDocuments()
}

// 上传中的文件ID集合，防止重复上传
const uploadingFiles = ref(new Set())

const handleUpload = async ({ file }) => {
  // 防止重复上传（按文件唯一 id 去重，避免同名文件互吞）
  if (uploadingFiles.value.has(file.id)) {
    return
  }
  uploadingFiles.value.add(file.id)
  
  const knowledgeId = route.params.id
  const fileType = file.name.split('.').pop().toLowerCase()
  const typeMap = {
    pdf: 'pdf',
    doc: 'word',
    docx: 'word',
    txt: 'txt',
    md: 'md',
    markdown: 'md'
  }
  
  // 先添加到列表显示上传中状态
  const tempId = `temp_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
  const newFile = {
    id: tempId,
    name: file.name,
    type: typeMap[fileType] || 'txt',
    size: file.file?.size || 0,
    status: 'uploading',
    uploadTime: new Date().toLocaleString('zh-CN', { 
      year: 'numeric', 
      month: '2-digit', 
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    }).replace(/\//g, '-')
  }
  
  fileList.value.unshift(newFile)
  
  try {
    const result = await documentAPI.uploadFile(knowledgeId, file.file)

    if (result) {
      // 上传成功，回到第一页刷新列表获取最新数据
      pageNum.value = 1
      await loadDocuments()
      message.success(`文件 "${file.name}" 上传成功`)
    } else {
      // 移除失败的文件
      fileList.value = fileList.value.filter(f => f.id !== tempId)
      message.error('上传失败')
    }
  } catch (error) {
    fileList.value = fileList.value.filter(f => f.id !== tempId)
    message.error(`上传失败: ${error.message}`)
  } finally {
    uploadingFiles.value.delete(file.id)
    // 清空上传组件的文件列表，防止累加
    uploadFileList.value = []
  }
}

const handleDownload = async (file) => {
  try {
    const blob = await documentAPI.downloadDocument(file.id)
    // 创建下载链接
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = file.name
    document.body.appendChild(a)
    a.click()
    window.URL.revokeObjectURL(url)
    document.body.removeChild(a)
    message.success('下载成功')
  } catch (error) {
    message.error(`下载失败: ${error.message}`)
  }
}

const handleDeleteFile = async (file) => {
  const result = await documentAPI.deleteDocument(file.knowledgeId, file.id)
  if (result) {
    // 删除后刷新，同步总大小/已处理等聚合统计
    await loadDocuments()
    message.success('已删除')
  } else {
    message.error('删除失败')
  }
}

const triggerUpload = () => {
  const uploadBtn = document.querySelector('.upload-dragger')
  if (uploadBtn) {
    uploadBtn.click()
  }
}

// 监听路由参数变化，重新加载数据
watch(() => route.params.id, (newId, oldId) => {
  if (newId && newId !== oldId) {
    pageNum.value = 1
    fetchKnowledgeBase()
    loadDocuments()
  }
})

onMounted(() => {
  fetchKnowledgeBase()
  loadDocuments()
})
</script>

<style scoped>
.kb-detail-view {
  min-height: 100vh;
  padding: 20px 24px 40px;
  background: var(--app-background, #f7f8fa);
}

/* 头部卡片 */
.header-card {
  background: var(--app-component-bg, #ffffff);
  border-radius: 16px;
  border: 1px solid rgba(226, 232, 240, 0.8);
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.06);
  overflow: hidden;
  margin-bottom: 16px;
}

.header-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.6);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-btn {
  padding: 6px 12px;
}

.title-section {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--app-text-primary, #1f2937);
}

.page-subtitle {
  margin: 0;
  color: var(--app-text-secondary, #6b7280);
  font-size: 12px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 上传区域 */
.upload-area {
  padding: 16px 20px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.6);
}

.upload-dragger {
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%) !important;
  border: 2px dashed #cbd5e1 !important;
  border-radius: 12px !important;
  transition: all 0.25s ease;
}

.upload-dragger:hover {
  border-color: #3b82f6 !important;
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%) !important;
}

.upload-content {
  padding: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
}

.upload-icon {
  width: 40px;
  height: 40px;
  color: #3b82f6;
  flex-shrink: 0;
}

.upload-icon svg {
  width: 100%;
  height: 100%;
}

.upload-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.upload-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--app-text-primary, #1f2937);
}

.upload-hint {
  font-size: 12px;
  color: var(--app-text-secondary, #6b7280);
}

/* 统计行 */
.stats-row {
  display: flex;
  align-items: center;
  padding: 14px 20px;
  gap: 24px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.stat-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-icon svg {
  width: 18px;
  height: 18px;
}

.stat-icon.files {
  background: rgba(59, 130, 246, 0.1);
  color: #3b82f6;
}

.stat-icon.size {
  background: rgba(168, 85, 247, 0.1);
  color: #a855f7;
}

.stat-icon.processed {
  background: rgba(34, 197, 94, 0.1);
  color: #22c55e;
}

.stat-content {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.stat-value {
  font-size: 16px;
  font-weight: 700;
  color: var(--app-text-primary, #1f2937);
  line-height: 1.2;
}

.stat-label {
  font-size: 11px;
  color: var(--app-text-secondary, #6b7280);
}

.stat-divider {
  width: 1px;
  height: 32px;
  background: rgba(226, 232, 240, 0.8);
}

/* 筛选栏 */
.filter-bar {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 16px;
}

.search-input {
  flex: 1;
  max-width: 320px;
}

.filter-select {
  width: 110px;
}

/* 文件列表 */
.file-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.file-card {
  background: var(--app-component-bg, #ffffff);
  border-radius: 12px;
  border: 1px solid rgba(226, 232, 240, 0.8);
  transition: all 0.2s ease;
}

.file-card:hover {
  border-color: rgba(59, 130, 246, 0.3);
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.06);
}

.file-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
}

.file-icon {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.file-icon svg {
  width: 22px;
  height: 22px;
}

.file-icon.type-pdf {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}

.file-icon.type-word {
  background: rgba(59, 130, 246, 0.1);
  color: #3b82f6;
}

.file-icon.type-txt {
  background: rgba(107, 114, 128, 0.1);
  color: #6b7280;
}

.file-icon.type-md {
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
}

.file-info {
  flex: 1;
  min-width: 0;
}

.file-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--app-text-primary, #1f2937);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-meta {
  font-size: 12px;
  color: var(--app-text-secondary, #6b7280);
  margin-top: 2px;
}

.separator {
  margin: 0 4px;
}

.file-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.file-actions {
  display: flex;
  gap: 2px;
  opacity: 0.6;
  transition: opacity 0.2s;
}

.file-card:hover .file-actions {
  opacity: 1;
}

.empty-state {
  margin-top: 60px;
  padding: 40px 0;
}

/* 分页栏 */
.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 16px;
}

.pagination-total {
  font-size: 13px;
  color: var(--app-text-secondary, #6b7280);
}

/* 响应式 */
@media (max-width: 768px) {
  .kb-detail-view {
    padding: 12px;
  }

  .header-top {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .header-left {
    width: 100%;
  }

  .upload-content {
    flex-direction: column;
    text-align: center;
  }

  .stats-row {
    flex-wrap: wrap;
    gap: 16px;
  }

  .stat-divider {
    display: none;
  }

  .filter-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .search-input {
    max-width: none;
  }

  .filter-select {
    width: 100%;
  }

  .file-item {
    flex-wrap: wrap;
    padding: 12px;
  }

  .file-right {
    width: 100%;
    justify-content: space-between;
    margin-top: 10px;
    padding-top: 10px;
    border-top: 1px solid rgba(226, 232, 240, 0.6);
  }
}
</style>
