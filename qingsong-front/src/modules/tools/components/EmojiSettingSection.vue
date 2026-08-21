<template>
  <div class="emoji-setting">
    <div class="panel-header">
      <h2>表情样式</h2>
      <p>选择 AI 聊天消息中 emoji 的渲染风格，未覆盖的表情会回退为系统原生渲染</p>
    </div>

    <div class="provider-grid">
      <div
        v-for="provider in providers"
        :key="provider.key"
        class="provider-card"
        :class="{ active: emojiStore.provider === provider.key }"
        @click="select(provider.key)"
      >
        <div class="provider-preview" v-html="renderPreview(provider.key)"></div>
        <div class="provider-label">{{ provider.label }}</div>
        <div class="provider-desc">{{ provider.description }}</div>
      </div>
    </div>

    <div class="live-preview">
      <div class="preview-title">实时预览</div>
      <div class="preview-content" v-html="livePreview"></div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useEmojiStore } from '@/stores/emojiStore'
import { EMOJI_PROVIDERS, replaceEmojis } from '@/modules/chat/utils/emoji.js'

const emojiStore = useEmojiStore()

const SAMPLE = '哈哈😄 赞👍 爱你😍 笑哭😂 大哭😭 比心❤️ 加油💪 震惊😱 思考🤔'

const providers = EMOJI_PROVIDERS

const select = key => {
  emojiStore.provider = key
}

const renderPreview = key => replaceEmojis(SAMPLE, key)

const livePreview = computed(() => replaceEmojis(SAMPLE, emojiStore.provider))
</script>

<style scoped lang="scss">
.emoji-setting {
  .provider-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 0.75rem;
  }

  .provider-card {
    padding: 1rem;
    border: 1px solid var(--config-line, rgba(0, 0, 0, 0.08));
    border-radius: 14px;
    background: var(--config-panel-muted, rgba(255, 255, 255, 0.6));
    cursor: pointer;
    transition: all 0.18s ease;
    text-align: center;

    &:hover {
      border-color: color-mix(in srgb, var(--config-accent, #3b82f6) 40%, transparent);
    }

    &.active {
      border-color: var(--config-accent, #3b82f6);
      background: color-mix(in srgb, var(--config-accent, #3b82f6) 8%, rgba(255, 255, 255, 0.92));
      box-shadow: 0 4px 14px color-mix(in srgb, var(--config-accent, #3b82f6) 18%, transparent);
    }
  }

  .provider-preview {
    min-height: 2.2em;
    font-size: 1.6rem;
    line-height: 2;
    word-break: break-all;
  }

  .provider-label {
    margin-top: 0.5rem;
    font-weight: 600;
    font-size: 0.9rem;
    color: var(--config-ink, #374151);
  }

  .provider-desc {
    margin-top: 0.25rem;
    font-size: 0.75rem;
    line-height: 1.5;
    color: var(--config-muted, #6b7280);
  }

  .live-preview {
    margin-top: 1rem;
    padding: 1rem;
    border: 1px solid var(--config-line, rgba(0, 0, 0, 0.08));
    border-radius: 14px;
    background: var(--config-panel-muted, rgba(255, 255, 255, 0.6));
  }

  .preview-title {
    margin-bottom: 0.5rem;
    font-size: 0.8rem;
    font-weight: 600;
    color: var(--config-muted, #6b7280);
  }

  .preview-content {
    font-size: 1.25rem;
    line-height: 2;
    color: var(--config-ink, #374151);
  }

  :deep(.emoji-img) {
    display: inline-block;
    width: 1.2em;
    height: 1.2em;
    vertical-align: -0.2em;
    margin: 0;
  }
}
</style>
