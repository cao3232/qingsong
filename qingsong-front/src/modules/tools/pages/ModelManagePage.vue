<template>
  <div class="model-manage-content">
    <div class="panel-header">
      <div class="header-content">
        <div>
          <h2>模型管理</h2>
          <p>管理可用的 AI 模型和参数设置，按来源分组展示</p>
        </div>
        <div class="header-actions">
          <button @click="testModelAvailability" class="btn-secondary" :disabled="testingModels || !selectedSourceId">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="9" />
              <path d="M12 3v4" />
              <path d="M12 21v-4" />
              <path d="M4.22 7.22l2.83 2.83" />
              <path d="M16.95 13.95l2.83 2.83" />
              <path d="M3 12h4" />
              <path d="M17 12h4" />
              <path d="M4.22 16.78l2.83-2.83" />
              <path d="M16.95 10.05l2.83-2.83" />
            </svg>
            {{ testingModels ? '检测中...' : '测试模型可用性' }}
          </button>
          <button @click="discoverModels" class="btn-secondary" :disabled="discoveringModels || !selectedSourceId">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 2v6h-6" />
              <path d="M3 12a9 9 0 0 1 15-6.7L21 8" />
              <path d="M3 22v-6h6" />
              <path d="M21 12a9 9 0 0 1-15 6.7L3 16" />
            </svg>
            {{ discoveringModels ? '拉取中...' : '拉取模型' }}
          </button>
        </div>
      </div>
    </div>

    <div class="config-form">
      <!-- 加载状态 -->
      <div v-if="sourceLoading" class="loading-state">
        <div class="loading-spinner"></div>
        <p>加载中...</p>
      </div>

      <!-- 错误状态 -->
      <div v-else-if="sourceError" class="error-state">
        <p>{{ sourceError }}</p>
        <button @click="loadSources" class="retry-btn">重试</button>
      </div>

      <!-- 空状态 -->
      <div v-else-if="sources.length === 0" class="empty-state">
        <p>暂无模型来源，请先创建模型来源</p>
      </div>

      <!-- 按来源分组的模型列表 -->
      <div v-else class="model-by-source-container">
        <!-- 来源选择器 - 横向卡片 -->
        <div class="source-selector-wrapper">
          <div class="source-selector" ref="sourceSelectorRef"
            :class="{ collapsed: sourceCollapsed }"
            :style="sourceCollapsed ? { maxHeight: collapsedMaxHeight } : {}">
            <div v-for="source in sources" :key="source.id"
              :class="['source-card', { active: selectedSourceId === source.id }]" @click="selectSource(source.id)">
              <div class="source-card-header">
                <span class="source-name">{{ source.sourceName }}</span>
                <span :class="['source-status-dot', source.isActive ? 'active' : 'inactive']"></span>
              </div>
              <div class="source-card-body">
                <span class="source-code-text">{{ source.sourceCode }}</span>
                <span class="source-model-count">{{ source.count || 0 }} 个模型</span>
              </div>
            </div>
          </div>
          <button v-if="showSourceToggle" class="source-toggle" type="button" @click="sourceCollapsed = !sourceCollapsed">
            <span>{{ sourceCollapsed ? '展开更多' : '收起' }}</span>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
              :style="{ transform: sourceCollapsed ? 'rotate(0deg)' : 'rotate(180deg)', transition: 'transform 0.2s ease' }">
              <polyline points="6 9 12 15 18 9" />
            </svg>
          </button>
        </div>

        <!-- 选中来源的模型列表 -->
        <div v-if="selectedSourceId" class="source-models-panel">
          <div class="panel-title-row">
            <h3>{{ getSelectedSourceName() }} 的模型列表</h3>
            <button @click="openModelModal(null, selectedSourceId)" class="btn-add-model">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19" />
                <line x1="5" y1="12" x2="19" y2="12" />
              </svg>
              添加模型
            </button>
          </div>

          <div v-if="modelsLoading" class="loading-state" style="padding: 2rem;">
            <div class="loading-spinner"></div>
            <p>加载模型中...</p>
          </div>

          <div v-else-if="currentSourceModels.length === 0" class="empty-models">
            <n-empty description="该来源下暂无模型" />
          </div>

          <div v-else class="model-table-container scrollbar-sm" ref="modelListRef" @scroll="handleModelListScroll">
            <div class="list-progress" aria-hidden="true">
              <span :style="{ width: modelListProgress + '%' }"></span>
            </div>
            <table class="model-table">
              <colgroup>
                <col style="width: 130px;" />
                <col style="width: 130px;" />
                <col style="width: 100px;" />
                <col style="width: 60px;" />
                <col style="width: 70px;" />
                <col style="width: 150px;" />
                <col style="width: 160px;" />
              </colgroup>
              <thead>
                <tr>
                  <th>模型名称</th>
                  <th>模型代码</th>
                  <th>类型</th>
                  <th>排序</th>
                  <th>状态</th>
                  <th>创建时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="model in currentSourceModels" :key="model.id">
                  <td>
                    <span class="model-name">{{ model.name }}</span>
                  </td>
                  <td>
                    <span class="model-code">{{ model.code }}</span>
                  </td>
                  <td>
                    <span v-if="model.type" class="table-tag tag-type">{{ model.type }}</span>
                    <span v-else class="table-placeholder">-</span>
                  </td>
                  <td style="text-align: center;">{{ model.modelOrder || 0 }}</td>
                  <td>
                    <span :class="['status-badge', model.isActive ? 'status-active' : 'status-inactive']">
                      {{ model.isActive ? '启用' : '禁用' }}
                    </span>
                  </td>
                  <td class="date-cell">{{ formatDate(model.createDate) }}</td>
                  <td class="actions-cell">
                    <button @click="copyModel(model)" class="table-btn btn-copy" title="复制">
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                        stroke-width="2">
                        <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
                        <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                      </svg>
                    </button>
                    <button @click="openModelModal(model)" class="table-btn btn-edit" :disabled="model.loading"
                      title="编辑">
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                        stroke-width="2">
                        <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                        <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
                      </svg>
                    </button>
                    <button @click="toggleModelStatus(model)"
                      :class="['table-btn', 'btn-status', model.isActive ? 'btn-disable' : 'btn-enable']"
                      :disabled="model.loading" :title="model.isActive ? '禁用' : '启用'">
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                        stroke-width="2">
                        <rect x="6" y="4" width="4" height="16" />
                        <rect x="14" y="4" width="4" height="16" />
                      </svg>
                    </button>
                    <button @click="deleteModel(model)" class="table-btn btn-delete" :disabled="model.loading"
                      title="删除">
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                        stroke-width="2">
                        <polyline points="3 6 5 6 21 6" />
                        <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                      </svg>
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- 未选择来源时的提示 -->
        <div v-else class="no-source-selected">
          <p>请选择一个模型来源查看对应的模型列表</p>
        </div>
      </div>
    </div>
  </div>

  <!-- 模型编辑弹窗 -->
  <n-modal v-model:show="showModelModal" preset="dialog" :title="editingModel ? '编辑模型' : '新建模型'"
    :positive-text="saving ? '保存中...' : '保存'" :negative-text="'取消'" :loading="saving" :disabled="saving"
    @positive-click="saveModel" @negative-click="closeModelModal" style="width: min(480px, calc(100vw - 24px));">
    <n-form label-placement="left" label-width="80">
      <n-form-item label="模型名称" required>
        <n-input v-model:value="modelForm.name" placeholder="请输入模型名称" />
      </n-form-item>
      <n-form-item label="模型代码" required>
        <n-input v-model:value="modelForm.code" placeholder="请输入模型代码" />
      </n-form-item>
      <n-form-item label="模型类型">
        <n-input v-model:value="modelForm.type" placeholder="例如：大语言模型" />
      </n-form-item>
      <n-form-item label="模型来源" v-if="!editingModel">
        <n-select v-model:value="modelForm.sourceId" :options="sourceOptions" placeholder="请选择模型来源" clearable />
      </n-form-item>
      <n-form-item label="排序">
        <n-input-number v-model:value="modelForm.modelOrder" :min="0" placeholder="0" style="width: 100%;" />
      </n-form-item>
      <n-form-item label="启用状态">
        <n-switch v-model:value="modelForm.isActive" />
      </n-form-item>
    </n-form>
  </n-modal>

  <!-- 拉取模型结果弹窗 -->
  <n-modal v-model:show="showDiscoverModal" preset="dialog" title="拉取到的模型"
    :positive-text="addingModels ? '添加中...' : '添加选中模型'" :negative-text="'关闭'" :loading="addingModels"
    :disabled="addingModels" @positive-click="batchAddSelected" @negative-click="closeDiscoverModal"
    style="width: min(760px, calc(100vw - 24px));">
    <div class="discover-summary">
      <span class="discover-summary-item">共 {{ discoverResults.length }} 个模型</span>
      <span class="discover-summary-item">已选 {{ selectedModels.length }}</span>
      <label class="discover-check-toggle">
        <input type="checkbox" v-model="checkBeforeAdd" />
        添加前检测可用性
      </label>
    </div>
    <div v-if="addingModels" class="discover-adding-tip">
      正在检测选中模型，请稍候...
    </div>
    <div class="discover-table-container">
      <table class="model-table discover-table">
        <colgroup>
          <col style="width: 40px;" />
          <col style="width: 220px;" />
          <col style="width: 90px;" />
          <col style="width: 120px;" />
          <col style="width: 110px;" />
          <col />
        </colgroup>
        <thead>
          <tr>
            <th style="text-align: center;">
              <input type="checkbox" :checked="allSelected" @change="toggleAll" />
            </th>
            <th>模型ID</th>
            <th>归属</th>
            <th>协议</th>
            <th>操作</th>
            <th>备注</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in discoverResults" :key="item.model">
            <td style="text-align: center;">
              <input type="checkbox" :value="item.model" v-model="selectedModels" />
            </td>
            <td>
              <span class="model-code">{{ item.model }}</span>
            </td>
            <td>{{ item.ownedBy || '-' }}</td>
            <td>
              <span v-if="item.supportedEndpointTypes && item.supportedEndpointTypes.length" class="endpoint-tags">
                <span v-for="t in item.supportedEndpointTypes" :key="t" class="endpoint-tag">{{ t }}</span>
              </span>
              <span v-else class="table-placeholder">-</span>
            </td>
            <td>
              <button class="discover-test-btn" :disabled="item.checking" @click="checkSingleModel(item)">
                <span v-if="item.checkResult === undefined || item.checkResult === null">测试</span>
                <span v-else :class="item.checkResult.working ? 'check-ok' : 'check-fail'">
                  {{ item.checking ? '检测中' : (item.checkResult.working ? '可用' : '不可用') }}
                </span>
              </button>
            </td>
            <td class="discover-note" :title="item.checkResult ? item.checkResult.note : item.note">
              {{ item.checkResult ? item.checkResult.note : (item.note || '-') }}
            </td>
          </tr>
          <tr v-if="discoverResults.length === 0">
            <td colspan="6" style="text-align: center; padding: 2rem; color: var(--app-text-secondary, #6b7280);">
              暂无模型数据
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </n-modal>

  <!-- 批量添加结果弹窗 -->
  <n-modal v-model:show="showBatchResultModal" preset="dialog" title="批量添加结果" :positive-text="'确定'"
    @positive-click="closeBatchResultModal" style="width: min(640px, calc(100vw - 24px));">
    <div class="discover-summary">
      <span class="discover-summary-item ok">新增 {{ batchAddedCount }}</span>
      <span class="discover-summary-item fail">未添加 {{ batchResults.length - batchAddedCount }}</span>
    </div>
    <div class="discover-table-container">
      <table class="model-table discover-table">
        <colgroup>
          <col style="width: 220px;" />
          <col style="width: 90px;" />
          <col />
        </colgroup>
        <thead>
          <tr>
            <th>模型ID</th>
            <th>结果</th>
            <th>备注</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in batchResults" :key="item.model">
            <td>
              <span class="model-code">{{ item.model }}</span>
            </td>
            <td>
              <span :class="['status-badge', item.added ? 'status-active' : 'status-inactive']">
                {{ item.added ? '已添加' : '未添加' }}
              </span>
            </td>
            <td class="discover-note" :title="item.note">{{ item.note || '-' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </n-modal>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useMessage, useDialog, NModal, NForm, NFormItem, NInput, NInputNumber, NSwitch, NSelect, NEmpty } from 'naive-ui'

const message = useMessage()
const dialog = useDialog()

// API
import { API_BASE_URL } from '@/config/env'
import http from '@/utils/http'

const SOURCE_API = API_BASE_URL + '/api/model-sources'
const MODEL_API = API_BASE_URL + '/api/model-configs'

// 来源列表状态
const sources = ref([])
const sourceLoading = ref(false)
const sourceError = ref(null)


const selectedSource = computed(() => {
  const source = sources.value.find(s => s.id === selectedSourceId.value) || null
  if (!source) {
    return { sourceName: '', sourceCode: '', id: null };
  }
  return { sourceName: source.sourceName, sourceCode: source.sourceCode, id: source.id };
})


// 按来源分组的模型数据
const sourceModels = ref({})
const sourceModelsLoaded = ref({})
const selectedSourceId = ref(null)
const modelsLoading = ref(false)

// 当前选中来源的模型列表
const currentSourceModels = computed(() => {
  return sourceModels.value[selectedSourceId.value] || []
})

// 弹窗状态
const showModelModal = ref(false)
const editingModel = ref(null)
const saving = ref(false)

// 模型检测状态
const testingModels = ref(false)

// 拉取模型状态
const discoveringModels = ref(false)
const showDiscoverModal = ref(false)
const discoverResults = ref([])
const selectedModels = ref([])
const addingModels = ref(false)
const checkBeforeAdd = ref(true)

// 批量添加结果
const showBatchResultModal = ref(false)
const batchResults = ref([])
const batchAddedCount = computed(() => batchResults.value.filter(m => m.added).length)

const allSelected = computed(() => {
  return discoverResults.value.length > 0 && discoverResults.value.every(m => selectedModels.value.includes(m.model))
})

// 模型表单
const modelForm = ref({
  name: '',
  code: '',
  type: '',
  sourceId: null,
  modelOrder: 0,
  isActive: true
})



// 模型来源下拉选项
const activeSources = ref([])
const sourceOptions = computed(() => {
  return activeSources.value.map(s => ({
    label: s.sourceName,
    value: s.id
  }))
})

// 模型来源选择器折叠
const sourceSelectorRef = ref(null)
const sourceCollapsed = ref(true)
const showSourceToggle = ref(false)
const collapsedMaxHeight = ref('')

const measureSourceSelector = () => {
  const el = sourceSelectorRef.value
  if (!el) return
  const cards = Array.from(el.children)
  if (cards.length === 0) {
    showSourceToggle.value = false
    return
  }
  const tops = [...new Set(cards.map((c) => c.offsetTop))]
  const rowCount = tops.length
  showSourceToggle.value = rowCount > 2
  const cardH = cards[0].offsetHeight
  const gap = parseFloat(getComputedStyle(el).rowGap || getComputedStyle(el).gap || '8') || 8
  // 两行高度 = 2 * 卡片高 + 1 * 行间距
  collapsedMaxHeight.value = `${2 * cardH + gap}px`
}

// 列表滚动进度
const modelListRef = ref(null)
const modelListProgress = ref(0)

const handleModelListScroll = () => {
  if (!modelListRef.value) {
    modelListProgress.value = 0
    return
  }
  const el = modelListRef.value
  const maxScroll = el.scrollHeight - el.clientHeight
  modelListProgress.value = maxScroll > 0 ? Math.round((el.scrollTop / maxScroll) * 100) : 0
}

// 加载来源列表
const loadSources = async () => {
  sourceLoading.value = true
  sourceError.value = null
  try {
    const result = await http.get(`${SOURCE_API}/info`)
    if (result.ok && result.data) {
      sources.value = result.data
    } else {
      sources.value = []
    }
  } catch (err) {
    console.error('加载来源列表失败:', err)
    sourceError.value = '加载失败'
    sources.value = []
  } finally {
    sourceLoading.value = false
  }
}

// 加载启用的来源列表（用于模型表单下拉框）
const loadActiveSources = async () => {
  try {
    const result = await http.get(`${SOURCE_API}/active`)
    if (result.ok && result.data) {
      activeSources.value = result.data
      // 避免重复
      if (!activeSources.value.some(source => source.id === selectedSource.value.id)) {
        activeSources.value.push(selectedSource.value); // 确保当前来源也在列表中（即使未启用）
      }
    }
  } catch (err) {
    console.error('加载来源列表失败:', err)
    activeSources.value = []
  }
}

// 选择来源
const selectSource = async (sourceId) => {
  selectedSourceId.value = sourceId
  if (!sourceModelsLoaded.value[sourceId]) {
    modelsLoading.value = true
    try {
      const result = await http.get(`${MODEL_API}/source/${sourceId}`)
      if (result.ok && result.data) {
        sourceModels.value[sourceId] = result.data
      } else {
        sourceModels.value[sourceId] = []
      }
      sourceModelsLoaded.value[sourceId] = true
    } catch (err) {
      console.error('加载模型列表失败:', err)
      sourceModels.value[sourceId] = []
      sourceModelsLoaded.value[sourceId] = true
    } finally {
      modelsLoading.value = false
    }
  }
}

// 获取选中来源的名称
const getSelectedSourceName = () => {
  const source = sources.value.find(s => s.id === selectedSourceId.value)
  return source ? source.sourceName : ''
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

// 切换模型状态
const toggleModelStatus = async (model) => {
  if (model.loading) return
  model.loading = true
  try {
    const result = await http.post(`${MODEL_API}/${model.id}/activate`)
    if (result.ok === 1) {
      model.isActive = !model.isActive
      message.success(model.isActive ? '模型已启用' : '模型已禁用')
    } else {
      message.error(result.msg || '操作失败')
    }
  } catch (err) {
    console.error('更新模型状态失败:', err)
    message.error('更新模型状态失败')
  } finally {
    model.loading = false
  }
}

// 测试模型可用性
const testModelAvailability = async () => {
  if (!selectedSourceId.value || testingModels.value) return
  testingModels.value = true
  try {
    const result = await http.get(`${MODEL_API}/test/${selectedSourceId.value}`, { timeout: 60000 })
    if (result.ok === 1) {
      message.success('模型可用性检测已完成')
      sourceModelsLoaded.value[selectedSourceId.value] = false
      await selectSource(selectedSourceId.value)
    } else {
      message.error(result.msg || '模型检测失败')
    }
  } catch (err) {
    console.error('测试模型可用性失败:', err)
    message.error('模型检测失败')
  } finally {
    testingModels.value = false
  }
}

// 拉取来源下的模型列表（不做检测）
const discoverModels = async () => {
  if (!selectedSourceId.value || discoveringModels.value) return
  discoveringModels.value = true
  try {
    const result = await http.get(`${MODEL_API}/discover/${selectedSourceId.value}`, { timeout: 120000 })
    if (result.ok === 1) {
      discoverResults.value = result.data || []
      selectedModels.value = discoverResults.value.map(m => m.model)
      batchResults.value = []
      showDiscoverModal.value = true
    } else {
      message.error(result.msg || '拉取模型失败')
    }
  } catch (err) {
    console.error('拉取模型失败:', err)
    message.error('拉取模型失败')
  } finally {
    discoveringModels.value = false
  }
}

// 全选/取消全选
const toggleAll = () => {
  if (allSelected.value) {
    selectedModels.value = []
  } else {
    selectedModels.value = discoverResults.value.map(m => m.model)
  }
}

// 单独测试某个模型（不写库）
const checkSingleModel = async (item) => {
  if (!selectedSourceId.value || item.checking) return
  item.checking = true
  try {
    const result = await http.post(`${MODEL_API}/check`, null, {
      params: {
        sourceId: selectedSourceId.value,
        modelId: item.model
      },
      timeout: 60000
    })
    if (result.ok === 1) {
      item.checkResult = result.data
    } else {
      item.checkResult = { working: false, httpStatus: 0, note: result.msg || '检测失败' }
    }
  } catch (err) {
    console.error('检测模型失败:', err)
    item.checkResult = { working: false, httpStatus: 0, note: '检测失败' }
  } finally {
    item.checking = false
  }
}

// 批量添加选中的模型（check 为用户可选项：true 只加检测通过的，false 直接入库）
const batchAddSelected = async () => {
  if (selectedModels.value.length === 0) {
    message.warning('请先勾选要添加的模型')
    return false
  }
  addingModels.value = true
  try {
    const result = await http.post(`${MODEL_API}/batch`, {
      sourceId: selectedSourceId.value,
      modelIds: selectedModels.value,
      check: checkBeforeAdd.value
    }, { timeout: 120000 })
    if (result.ok === 1) {
      batchResults.value = result.data || []
      showDiscoverModal.value = false
      showBatchResultModal.value = true
      loadSources() // 刷新来源列表以更新模型数量
      sourceModelsLoaded.value[selectedSourceId.value] = false
      await selectSource(selectedSourceId.value)
      return false // 不关闭当前 dialog（已切换为结果弹窗），返回 false 避免重复关闭
    } else {
      message.error(result.msg || '批量添加失败')
      return false
    }
  } catch (err) {
    console.error('批量添加失败:', err)
    message.error('批量添加失败')
    return false
  } finally {
    addingModels.value = false
  }
}

// 关闭拉取结果弹窗
const closeDiscoverModal = () => {
  showDiscoverModal.value = false
  discoverResults.value = []
  selectedModels.value = []
  batchResults.value = []
}

// 关闭批量添加结果弹窗
const closeBatchResultModal = () => {
  showBatchResultModal.value = false
  batchResults.value = []
}

// 打开模型弹窗
const openModelModal = async (model = null, defaultSourceId = null) => {
  await loadActiveSources()
  if (model) {
    editingModel.value = model
    modelForm.value = {
      name: model.name,
      code: model.code,
      type: model.type || '',
      sourceId: model.modelSource || null,      modelOrder: model.modelOrder || 0,
      isActive: model.isActive !== false
    }
  } else {
    editingModel.value = null
    modelForm.value = {
      name: '',
      code: '',
      type: '',
      sourceId: defaultSourceId || null,
      modelOrder: 0,
      isActive: true
    }
  }
  showModelModal.value = true
}

// 关闭弹窗
const closeModelModal = () => {
  showModelModal.value = false
  editingModel.value = null
  modelForm.value = {
    name: '',
    code: '',
    type: '',
    sourceId: null,
    modelOrder: 0,
    isActive: true
  }
}

// 保存模型
const saveModel = async () => {
  if (!modelForm.value.name || !modelForm.value.code) {
    message.warning('请填写模型名称和模型代码')
    return false
  }
  saving.value = true
  try {
    let result
    if (editingModel.value) {
      result = await http.put(`${MODEL_API}/${editingModel.value.id}`, {
        name: modelForm.value.name,
        code: modelForm.value.code,
        type: modelForm.value.type,
        sourceId: modelForm.value.sourceId,
        modelOrder: modelForm.value.modelOrder,
        isActive: modelForm.value.isActive
      })
    } else {
      result = await http.post(MODEL_API, {
        name: modelForm.value.name,
        code: modelForm.value.code,
        type: modelForm.value.type,
        modelSource: modelForm.value.sourceId,
        modelOrder: modelForm.value.modelOrder,
        isActive: modelForm.value.isActive
      })
    }
    if (result.ok === 1) {
      message.success(editingModel.value ? '更新成功' : '创建成功')
      const savedSourceId = modelForm.value.sourceId
      closeModelModal()
      loadSources() // 刷新来源列表以更新模型数量
      if (savedSourceId) {
        sourceModelsLoaded.value[savedSourceId] = false
        if (selectedSourceId.value === savedSourceId) {
          await selectSource(savedSourceId)
        }
      }
      return true
    } else {
      message.error(result.msg || '操作失败')
      return false
    }
  } catch (err) {
    console.error('保存模型失败:', err)
    message.error('保存模型失败')
    return false
  } finally {
    saving.value = false
  }
}

// 复制模型
const copyModel = async (model) => {
  await loadActiveSources();
  editingModel.value = null
  modelForm.value = {
    name: model.name + ' (副本)',
    code: model.code,
    type: model.type || '',
    sourceId: model.modelSource || null,
    modelOrder: model.modelOrder || 0,
    isActive: model.isActive !== false
  }
  showModelModal.value = true
}

// 删除模型
const deleteModel = async (model) => {
  dialog.warning({
    title: '删除确认',
    content: `确定要删除模型 "${model.name}" 吗？此操作不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const result = await http.delete(`${MODEL_API}/${model.id}`)
        if (result.ok === 1) {
          message.success('删除成功')
          const sourceId = model.modelSource || selectedSourceId.value
          if (sourceId) {
            sourceModelsLoaded.value[sourceId] = false
            if (selectedSourceId.value === sourceId) {
              await selectSource(sourceId)
            }
          }
        } else {
          message.error(result.msg || '删除失败')
        }
      } catch (err) {
        console.error('删除模型失败:', err)
        message.error('删除失败')
      }
    }
  })
}

onMounted(async () => {
  await loadSources()
  if (sources.value.length > 0) {
    const activeSource = sources.value.find(s => s.isActive)
    const defaultSource = activeSource || sources.value[0]
    await selectSource(defaultSource.id)
  }
  await nextTick()
  measureSourceSelector()
  window.addEventListener('resize', measureSourceSelector)
})

onUnmounted(() => {
  window.removeEventListener('resize', measureSourceSelector)
})

watch(() => sources.value.length, async () => {
  await nextTick()
  measureSourceSelector()
})
</script>

<style scoped>
.model-manage-content {
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

.header-actions {
  display: inline-flex;
  align-items: center;
  gap: 0.75rem;
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

/* 次按钮 */
.btn-secondary {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.55rem 1rem;
  border: 1px solid var(--app-border-color, #cbd5f5);
  border-radius: 6px;
  font-size: 0.875rem;
  cursor: pointer;
  transition: all 0.2s ease;
  background: rgba(59, 130, 246, 0.08);
  color: var(--app-active-bg, #3b82f6);
}

.btn-secondary:hover:not(:disabled) {
  background: rgba(59, 130, 246, 0.18);
  border-color: rgba(59, 130, 246, 0.55);
}

.btn-secondary:disabled {
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

/* 按来源分组的模型列表 */
.model-by-source-container {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  gap: 1.5rem;
}

/* 来源选择器 - 横向卡片 */
.source-selector-wrapper {
  position: relative;
}

.source-selector {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  overflow: hidden;
  transition: max-height 0.25s ease;
}

.source-toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  margin-top: 0.5rem;
  padding: 0.25rem 0.5rem;
  border: none;
  background: transparent;
  color: var(--app-active-bg, #3b82f6);
  font-size: 0.8rem;
  font-weight: 500;
  cursor: pointer;
  transition: color 0.2s ease;
}

.source-toggle:hover {
  color: #2563eb;
}

.source-card {
  padding: 0.5rem 0.75rem;
  background: var(--app-component-bg, white);
  border: 1.5px solid var(--app-border-color, #e5e7eb);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.source-card:hover {
  border-color: var(--app-active-bg, #3b82f6);
}

.source-card.active {
  border-color: var(--app-active-bg, #3b82f6);
  background: rgba(59, 130, 246, 0.06);
}

.source-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.5rem;
}

.source-card-header .source-name {
  font-weight: 500;
  font-size: 0.85rem;
  color: var(--app-text-primary, #1f2937);
  white-space: nowrap;
}

.source-status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.source-status-dot.active {
  background: #22c55e;
}

.source-status-dot.inactive {
  background: #ef4444;
}

.source-card-body {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.source-code-text {
  font-family: 'Courier New', monospace;
  font-size: 0.7rem;
  color: var(--app-text-secondary, #6b7280);
}

.source-model-count {
  font-size: 0.7rem;
  color: var(--app-active-bg, #3b82f6);
}

/* 选中来源的模型列表面板 */
.source-models-panel {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  background: var(--app-component-bg, rgba(255, 255, 255, 0.8));
  border: 1px solid var(--app-border-color, #e5e7eb);
  border-radius: 18px;
  padding: 1.25rem;
}

.panel-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid var(--app-border-color, #e5e7eb);
}

.panel-title-row h3 {
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
  color: var(--app-text-primary, #1f2937);
}

.btn-add-model {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.4rem 0.8rem;
  border: 1px solid var(--app-active-bg, #3b82f6);
  border-radius: 6px;
  background: transparent;
  color: var(--app-active-bg, #3b82f6);
  cursor: pointer;
  font-size: 0.85rem;
  transition: all 0.2s ease;
}

.btn-add-model:hover {
  background: var(--app-active-bg, #3b82f6);
  color: white;
}

.empty-models {
  padding: 2rem;
  text-align: center;
}

.no-source-selected {
  padding: 3rem;
  text-align: center;
  color: var(--app-text-secondary, #6b7280);
  background: var(--app-bg-secondary, rgba(249, 250, 251, 0.5));
  border-radius: 10px;
}

/* 滚动进度条 */
.list-progress {
  position: sticky;
  top: 0;
  left: 0;
  height: 2px;
  background: rgba(0, 0, 0, 0.04);
  pointer-events: none;
  z-index: 11;
}

.list-progress span {
  display: block;
  height: 100%;
  width: 0;
  background: var(--app-active-bg, #3b82f6);
  transition: width 0.1s;
}

/* 表格容器 */
.model-table-container {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: auto;
  border-radius: 18px;
  border: 1px solid var(--app-border-color, #e5e7eb);
  background: var(--app-component-bg, rgba(255, 255, 255, 0.8));
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
  font-family: 'Courier New', monospace;
  font-size: 0.8rem;
  padding: 0.2rem 0.5rem;
  background: var(--app-bg-secondary, #f3f4f6);
  color: var(--app-text-secondary, #6b7280);
  border-radius: 4px;
  font-weight: 500;
}

.table-tag {
  display: inline-flex;
  align-items: center;
  padding: 0.25rem 0.6rem;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 500;
}

.tag-type {
  background: rgba(59, 130, 246, 0.1);
  color: #3b82f6;
}

.table-placeholder {
  color: var(--app-text-secondary, #9ca3af);
}

.date-cell {
  font-size: 0.8rem;
  color: var(--app-text-secondary, #6b7280);
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

/* 拉取模型结果弹窗 */
.discover-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-bottom: 0.75rem;
}

.discover-summary-item {
  padding: 0.25rem 0.75rem;
  border-radius: 12px;
  font-size: 0.8rem;
  font-weight: 500;
  background: var(--app-bg-secondary, #f3f4f6);
  color: var(--app-text-secondary, #6b7280);
}

.discover-summary-item.ok {
  background: rgba(34, 197, 94, 0.1);
  color: #22c55e;
}

.discover-summary-item.fail {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}

.discover-table-container {
  max-height: 50vh;
  overflow-y: auto;
  overflow-x: auto;
  border: 1px solid var(--app-border-color, #e5e7eb);
  border-radius: 10px;
}

.discover-table {
  table-layout: auto;
}

.discover-table input[type="checkbox"] {
  width: 15px;
  height: 15px;
  cursor: pointer;
  accent-color: var(--app-active-bg, #3b82f6);
}

.discover-table input[type="checkbox"]:disabled {
  cursor: not-allowed;
  opacity: 0.4;
}

.discover-note {
  font-size: 0.78rem;
  color: var(--app-text-secondary, #6b7280);
}

.discover-check-toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.25rem 0.75rem;
  border-radius: 12px;
  font-size: 0.8rem;
  font-weight: 500;
  background: var(--app-bg-secondary, #f3f4f6);
  color: var(--app-text-primary, #374151);
  cursor: pointer;
  user-select: none;
}

.discover-check-toggle input[type="checkbox"] {
  width: 14px;
  height: 14px;
  cursor: pointer;
  accent-color: var(--app-active-bg, #3b82f6);
}

.discover-adding-tip {
  margin-bottom: 0.75rem;
  padding: 0.5rem 0.75rem;
  border-radius: 8px;
  font-size: 0.85rem;
  background: rgba(59, 130, 246, 0.1);
  color: var(--app-active-bg, #3b82f6);
}

.endpoint-tags {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 0.25rem;
}

.endpoint-tag {
  padding: 0.15rem 0.5rem;
  border-radius: 10px;
  font-size: 0.72rem;
  font-weight: 500;
  background: rgba(139, 92, 246, 0.1);
  color: #8b5cf6;
}

:global(.dark) .endpoint-tag {
  background: rgba(139, 92, 246, 0.2);
  color: #c4b5fd;
}

.discover-test-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0.2rem 0.6rem;
  border: 1px solid var(--app-active-bg, #3b82f6);
  border-radius: 6px;
  background: transparent;
  color: var(--app-active-bg, #3b82f6);
  font-size: 0.78rem;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s ease;
}

.discover-test-btn:hover:not(:disabled) {
  background: var(--app-active-bg, #3b82f6);
  color: white;
}

.discover-test-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.check-ok {
  color: #22c55e;
}

.check-fail {
  color: #ef4444;
}

/* 小屏适配 */
@media (max-width: 640px) {
  .model-manage-content {
    height: auto;
    padding: 0 4px 12px;
  }
  .header-content {
    flex-direction: column;
    align-items: flex-start;
  }
  .header-actions {
    flex-wrap: wrap;
  }
  .panel-title-row {
    flex-wrap: wrap;
    gap: 0.5rem;
  }
  .model-table-container {
    max-height: calc(100vh - 320px);
  }
}
</style>
