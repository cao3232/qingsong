<template>
  <Transition name="phrase-panel">
    <div v-if="visible" ref="panelRef" class="quick-phrase-panel">
      <div class="panel-header">
        <div class="header-title">
          <h3>常用短语</h3>
          <span v-if="phrases.length > 0" class="phrase-count">{{ phrases.length }}</span>
        </div>
        <n-button text @click="closePanel">
          <template #icon>
            <XMarkIcon class="icon" />
          </template>
        </n-button>
      </div>

      <n-spin :show="loading">
        <div class="panel-content scrollbar-sm">
          <n-list hoverable clickable>
            <n-list-item v-for="phrase in phrases" :key="phrase.id">
              <div class="phrase-item" :title="phrase.phrase" @click="selectPhrase(phrase.phrase)">
                <span class="phrase-text">{{ phrase.phrase }}</span>
                <div class="actions">
                  <n-button text @click.stop="startEdit(phrase)">
                    <template #icon>
                      <PencilIcon class="icon" />
                    </template>
                  </n-button>

                  <n-popconfirm positive-text="确定" negative-text="取消" @positive-click="deletePhrase(phrase)">
                    <template #trigger>
                      <n-button text class="delete" @click.stop>
                        <template #icon>
                          <TrashIcon class="icon" />
                        </template>
                      </n-button>
                    </template>
                    确定要删除常用短语“{{ phrase.phrase }}”吗？
                  </n-popconfirm>
                </div>
              </div>
            </n-list-item>
          </n-list>

          <n-empty v-if="!loading && phrases.length === 0" description="暂无常用短语，先添加一条吧" size="small"
            class="empty-hint" />

          <div v-if="showAddInput || isEditing" class="add-phrase">
            <n-input v-model:value="newPhraseText" size="small" :placeholder="isEditing ? '更新短语...' : '添加新短语...'"
              @keyup.enter="addOrUpdatePhrase" />
            <n-button type="primary" size="small" @click="addOrUpdatePhrase">
              {{ isEditing ? '更新' : '添加' }}
            </n-button>
            <n-button v-if="isEditing" size="small" @click="cancelEdit">
              取消
            </n-button>
          </div>

          <div v-else class="add-phrase-toggle">
            <n-button text @click="showAddInput = true">
              + 添加常用短语
            </n-button>
          </div>
        </div>
      </n-spin>
    </div>
  </Transition>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { XMarkIcon, PencilIcon, TrashIcon } from '@heroicons/vue/24/outline'
import { useMessage, NButton, NEmpty, NInput, NList, NListItem, NPopconfirm, NSpin } from 'naive-ui'
import { rolePhrasesAPI } from '../services/index.js'

defineOptions({
  name: 'QuickPhrasePanel'
})

const props = defineProps({
  visible: Boolean,
  roleId: [String]
})

const emit = defineEmits(['close', 'select-phrase'])

const phrases = ref([])
const newPhraseText = ref('')
const isEditing = ref(false)
const editingPhrase = ref(null)
const loading = ref(false)
const showAddInput = ref(false)
const panelRef = ref(null)
const message = useMessage()

const isPanelVisible = computed(() => props.visible)

const fetchPhrases = async () => {
  if (!props.roleId) {
    phrases.value = []
    return
  }

  loading.value = true
  try {
    const data = await rolePhrasesAPI.getRolePhrases(props.roleId)
    phrases.value = data || []
  } catch (error) {
    message.error('加载常用短语失败')
    console.error('Failed to fetch phrases:', error)
  } finally {
    loading.value = false
  }
}

const handleClickOutside = event => {
  if (event.target?.closest?.('.phrase-toggle-btn')) {
    return
  }

  // 点击面板或其宿主（输入区）内部时不关闭，方便先定位光标再选短语
  const container = panelRef.value?.parentElement
  if (panelRef.value?.contains(event.target) || container?.contains(event.target)) {
    return
  }

  closePanel()
}

watch(isPanelVisible, visible => {
  if (visible) {
    fetchPhrases()
    nextTick(() => {
      document.addEventListener('mousedown', handleClickOutside)
    })
    return
  }

  document.removeEventListener('mousedown', handleClickOutside)
})

watch(
  () => props.roleId,
  () => {
    if (isPanelVisible.value) {
      fetchPhrases()
    }
  }
)

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', handleClickOutside)
})

const addOrUpdatePhrase = async () => {
  const phraseText = newPhraseText.value.trim()
  if (!phraseText) {
    return
  }

  try {
    if (isEditing.value && editingPhrase.value) {
      const updatedPhrase = { ...editingPhrase.value, phrase: phraseText }
      const result = await rolePhrasesAPI.updateRolePhrase(updatedPhrase)

      if (result?.ok === 1) {
        message.success('常用短语更新成功')
        await fetchPhrases()
        cancelEdit()
      } else {
        message.error(result?.msg || '更新失败')
      }
      return
    }

    const newPhrase = { roleId: props.roleId, phrase: phraseText }
    const result = await rolePhrasesAPI.addRolePhrase(newPhrase)
    if (result?.ok === 1) {
      message.success('常用短语添加成功')
      await fetchPhrases()
      newPhraseText.value = ''
    } else {
      message.error(result?.msg || '添加失败')
    }
  } catch (error) {
    message.error('操作失败')
    console.error('Failed to add/update phrase:', error)
  } finally {
    if (!isEditing.value) {
      showAddInput.value = false
    }
  }
}

const startEdit = phrase => {
  isEditing.value = true
  editingPhrase.value = phrase
  newPhraseText.value = phrase.phrase
}

const cancelEdit = () => {
  isEditing.value = false
  editingPhrase.value = null
  newPhraseText.value = ''
  showAddInput.value = false
}

const deletePhrase = async phrase => {
  try {
    const result = await rolePhrasesAPI.deleteRolePhrase(phrase.id, props.roleId)
    if (result?.ok === 1) {
      message.success('常用短语删除成功')
      await fetchPhrases()
    } else {
      message.error(result?.msg || '删除失败')
    }
  } catch (error) {
    message.error('删除失败')
    console.error('Failed to delete phrase:', error)
  }
}

const selectPhrase = phraseText => {
  emit('select-phrase', phraseText)
  closePanel()
}

const closePanel = () => {
  emit('close')
}
</script>

<style lang="scss" scoped>
.quick-phrase-panel {
  position: absolute;
  bottom: 100%;
  left: 0;
  width: 100%;
  max-height: 320px;
  background: var(--chat-panel, var(--card-bg));
  border: 1px solid var(--chat-bevel-shadow, var(--border-color));
  border-radius: 12px 12px 0 0;
  box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.1);
  box-shadow: 0 -4px 20px color-mix(in srgb, var(--chat-shadow-color, #000000) 10%, transparent);
  display: flex;
  flex-direction: column;
  z-index: 20;
  overflow: hidden;

  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 12px;
    border-bottom: 1px solid var(--chat-bevel-shadow, var(--border-color));

    .header-title {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    h3 {
      margin: 0;
      font-size: 14px;
      font-weight: 600;
      color: var(--chat-text, var(--text-color));
    }

    .phrase-count {
      font-size: 11px;
      line-height: 1;
      padding: 3px 7px;
      border-radius: 999px;
      background: color-mix(in srgb, var(--chat-accent, #000080) 12%, transparent);
      color: var(--chat-accent, #000080);
      font-weight: 600;
    }

    .icon {
      width: 18px;
      height: 18px;
      color: var(--chat-text-muted, var(--text-color-light));
    }
  }

  :deep(.n-spin-container) {
    display: flex;
    flex-direction: column;
    flex-grow: 1;
    min-height: 0;
    overflow: hidden;
  }

  .panel-content {
    overflow-y: auto;
    padding: 6px;
    flex-grow: 1;
    min-height: 0;
    color: var(--chat-text, var(--text-color));
  }

  :deep(.n-list) {
    padding: 0;
  }

  :deep(.n-list-item) {
    color: var(--chat-text, var(--text-color));
    padding: 0;
  }

  .phrase-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 8px;
    width: 100%;
    min-height: 34px;
    padding: 5px 8px;
    border-radius: var(--chat-radius, 8px);
    cursor: pointer;
    transition: background-color 0.15s ease;

    &:hover {
      background: var(--chat-panel-hover, rgba(0, 0, 0, 0.04));
    }

    .phrase-text {
      flex-grow: 1;
      font-size: 13px;
      line-height: 1.5;
      color: inherit;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
      word-break: break-word;
    }

    .actions {
      display: flex;
      align-items: center;
      gap: 2px;
      opacity: 0.45;
      transition: opacity 0.15s ease;
      flex-shrink: 0;

      &:hover,
      .phrase-item:hover & {
        opacity: 1;
      }

      .icon {
        width: 15px;
        height: 15px;
        color: var(--chat-text-muted, var(--text-color-light));
      }

      .delete:hover {
        color: var(--chat-danger, #ef4444);
      }
    }
  }

  .empty-hint {
    padding: 14px 0;
  }

  .add-phrase {
    display: flex;
    gap: 8px;
    padding: 8px 6px;
    margin-top: 4px;
    border-top: 1px solid var(--chat-bevel-shadow, var(--border-color));
  }

  .add-phrase-toggle {
    padding: 8px 6px;
    margin-top: 4px;
    border-top: 1px solid var(--chat-bevel-shadow, var(--border-color));
    text-align: center;

    :deep(.n-button .n-button__content) {
      color: var(--chat-accent, var(--primary-color)) !important;
      font-weight: 500;
    }
  }
}

.phrase-panel-enter-active,
.phrase-panel-leave-active {
  transition: opacity 0.16s ease, transform 0.16s ease;
}

.phrase-panel-enter-from,
.phrase-panel-leave-to {
  opacity: 0;
  transform: translateY(10px);
}
</style>
