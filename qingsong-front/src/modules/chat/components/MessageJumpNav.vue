<template>
  <nav v-if="userMessages.length > 0" class="message-jump-nav" :class="{ expanded }"
    aria-label="消息快捷跳转" @mouseenter="expanded = true" @mouseleave="expanded = false">
    <div class="jump-nav-track">
      <button v-for="(item, index) in userMessages" :key="item.id || index" type="button"
        class="jump-dot" :aria-label="`跳转到用户消息：${item.preview}`"
        @click="emit('jump', item)">
        <span class="dot-core"></span>
        <span v-if="expanded" class="jump-dot-label">{{ item.preview }}</span>
      </button>
    </div>
  </nav>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  messages: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['jump'])

const expanded = ref(false)

const normalizePreview = value => String(value || '').replace(/\s+/g, ' ').trim()

const userMessages = computed(() => {
  const anchors = []
  const messages = Array.isArray(props.messages) ? props.messages : []

  for (let index = 0; index < messages.length; index += 1) {
    const message = messages[index]
    if (!message || message.role !== 'user') {
      continue
    }

    const content = normalizePreview(message.content)
    if (!content) {
      continue
    }

    anchors.push({
      id: message.messageNo || message.id || null,
      index,
      preview: content.length > 28 ? `${content.slice(0, 28)}...` : content
    })
  }

  return anchors
})
</script>

<style lang="scss" scoped>
.message-jump-nav {
  position: absolute;
  top: 12px;
  right: 10px;
  bottom: 70px;
  z-index: 5;
  width: 24px;
  padding: 6px 4px;
  box-sizing: border-box;
  background: var(--chat-panel, #c0c0c0);
  border: 2px solid;
  border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
  border-radius: var(--chat-radius, 0);
  box-shadow: var(--chat-shadow, none);
  opacity: 0.65;
  transition: width 0.12s ease, opacity 0.12s ease;

  &:hover,
  &.expanded {
    opacity: 1;
  }

  &.expanded {
    width: 230px;
  }
}

.jump-nav-track {
  height: 100%;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
  scrollbar-width: none;

  &::-webkit-scrollbar {
    display: none;
  }
}

.jump-dot {
  position: relative;
  flex-shrink: 0;
  height: 14px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: none;
  min-width: 0;

  &:hover {
    .dot-core {
      background: var(--chat-accent, #000080);
      border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
    }

    .jump-dot-label {
      color: var(--chat-accent, #000080);
    }
  }

  &:active {
    .dot-core {
      padding: 1px 0 0 1px;
    }
  }
}

.dot-core {
  flex-shrink: 0;
  width: 10px;
  height: 10px;
  box-sizing: border-box;
  border-radius: 50%;
  background: var(--chat-text-muted, #808080);
  border: 2px solid;
  border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
  transition: none;
}

.jump-dot-label {
  flex: 1;
  min-width: 0;
  padding-right: 12px;
  font-size: 11px;
  line-height: 1.3;
  color: var(--chat-text, #000000);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-family: var(--chat-font-family, 'MS Sans Serif', 'Segoe UI', Tahoma, sans-serif);
  pointer-events: none;
}

@media (max-width: 768px) {
  .message-jump-nav {
    display: none;
  }
}
</style>
