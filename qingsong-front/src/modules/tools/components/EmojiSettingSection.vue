<template>
  <div class="emoji-setting">
    <h3 class="emoji-title">表情样式</h3>
    <p class="emoji-hint">选择 AI 聊天消息中 emoji 的渲染风格，未覆盖的表情会回退为系统原生渲染</p>

    <div class="provider-grid">
      <div
        v-for="provider in providers"
        :key="provider.key"
        class="provider-card"
        :class="{ active: emojiStore.provider === provider.key }"
        @click="select(provider.key)"
      >
        <span v-if="emojiStore.provider === provider.key" class="provider-check">✓</span>
        <div class="provider-preview" v-html="renderPreview(provider.key)"></div>
        <div class="provider-label">{{ provider.label }}</div>
        <div class="provider-desc">{{ provider.description }}</div>
      </div>
    </div>

    <div class="live-preview">
      <div class="preview-title">实时预览</div>
      <div class="preview-bubble" v-html="livePreview"></div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useEmojiStore } from '@/stores/emojiStore'
import { EMOJI_PROVIDERS, replaceEmojis } from '@/modules/chat/utils/emoji.js'

const emojiStore = useEmojiStore()

// 卡片内只放少量表情保持紧凑；实时预览用完整例句模拟真实聊天
const CARD_SAMPLE = '😄 👍 😍 😂 🤔'
const SAMPLE = '哈哈😄 赞👍 爱你😍 笑哭😂 大哭😭 比心❤️ 加油💪 震惊😱 思考🤔'

const providers = EMOJI_PROVIDERS

const select = key => {
  emojiStore.provider = key
}

const renderPreview = key => replaceEmojis(CARD_SAMPLE, key)

const livePreview = computed(() => replaceEmojis(SAMPLE, emojiStore.provider))
</script>

<style scoped lang="scss">
.emoji-setting {
  display: flex;
  flex-direction: column;
}

/* 与系统设置页其他区块标题一致：左侧主色竖条 */
.emoji-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 6px;
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--app-text-primary, #374151);

  &::before {
    content: '';
    flex-shrink: 0;
    width: 3px;
    height: 14px;
    border-radius: 2px;
    background: var(--app-active-bg, #3b82f6);
  }
}

.emoji-hint {
  margin: 0 0 16px;
  font-size: 0.82rem;
  color: var(--app-text-secondary, #6b7280);
}

/* 6 个 provider 固定 3×2 排布，窄屏依次降列 */
.provider-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.provider-card {
  position: relative;
  padding: 14px 12px 12px;
  border: 1px solid var(--app-border-color, rgba(0, 0, 0, 0.08));
  border-radius: 12px;
  background: var(--app-component-bg, rgba(255, 255, 255, 0.6));
  cursor: pointer;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
  text-align: center;

  &:hover {
    transform: translateY(-2px);
    border-color: color-mix(in srgb, var(--app-active-bg, #3b82f6) 45%, transparent);
    box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
  }

  &.active {
    border-color: var(--app-active-bg, #3b82f6);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--app-active-bg, #3b82f6) 20%, transparent);
  }
}

/* 选中角标 */
.provider-check {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--app-active-bg, #3b82f6);
  color: var(--app-active-text, #ffffff);
  font-size: 11px;
  line-height: 18px;
  text-align: center;
  pointer-events: none;
}

.provider-preview {
  min-height: 2.8rem;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 2px;
  font-size: 1.7rem;
  line-height: 1.6;

  :deep(.emoji-img) {
    width: 1.7rem;
    height: 1.7rem;
    vertical-align: middle;
  }
}

.provider-label {
  margin-top: 8px;
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--app-text-primary, #374151);
}

.provider-desc {
  margin-top: 2px;
  font-size: 0.75rem;
  line-height: 1.5;
  color: var(--app-text-secondary, #6b7280);
}

/* 实时预览：模拟 AI 消息气泡，贴近实际聊天效果 */
.live-preview {
  margin-top: 14px;
  padding: 12px 14px 14px;
  border: 1px solid var(--app-border-color, rgba(0, 0, 0, 0.08));
  border-radius: 12px;
  background: var(--app-bg-secondary, #f9fafb);
}

.preview-title {
  margin-bottom: 8px;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--app-text-secondary, #6b7280);
}

.preview-bubble {
  display: inline-block;
  max-width: 100%;
  padding: 10px 14px;
  border-radius: var(--chat-radius, 12px);
  background: var(--chat-ai-bubble-bg, #ffffff);
  border: 1px solid var(--chat-ai-bubble-border, var(--app-border-color, #e5e7eb));
  color: var(--app-text-primary, #1f2937);
  font-size: 1.1rem;
  line-height: 2;
  word-break: break-word;

  :deep(.emoji-img) {
    display: inline-block;
    width: 1.3em;
    height: 1.3em;
    vertical-align: -0.2em;
    margin: 0;
  }
}

@media (max-width: 900px) {
  .provider-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 560px) {
  .provider-grid {
    grid-template-columns: 1fr;
  }
}
</style>
