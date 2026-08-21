<template>
  <div class="system-setting-content">
    <div class="panel-header">
      <h2>系统设置</h2>
      <p>配置系统行为和界面偏好</p>
    </div>

    <!-- 子标签页 -->
    <div class="sub-tabs">
      <button
        class="sub-tab-btn"
        :class="{ active: activeSubTab === 'appearance' }"
        @click="activeSubTab = 'appearance'"
      >
        页面外观
      </button>
      <button
          class="sub-tab-btn"
          :class="{ active: activeSubTab === 'nav' }"
          @click="activeSubTab = 'nav'"
      >
        头像设置
      </button>
      <button
        class="sub-tab-btn"
        :class="{ active: activeSubTab === 'font' }"
        @click="activeSubTab = 'font'"
      >
        字体设置
      </button>
      <button
        class="sub-tab-btn"
        :class="{ active: activeSubTab === 'chat' }"
        @click="activeSubTab = 'chat'"
      >
        聊天样式
      </button>
      <button
        class="sub-tab-btn"
        :class="{ active: activeSubTab === 'message' }"
        @click="activeSubTab = 'message'"
      >
        消息样式
      </button>
      <button
        class="sub-tab-btn"
        :class="{ active: activeSubTab === 'code' }"
        @click="activeSubTab = 'code'"
      >
        代码样式
      </button>
      <button
        class="sub-tab-btn"
        :class="{ active: activeSubTab === 'emoji' }"
        @click="activeSubTab = 'emoji'"
      >
        表情样式
      </button>
      <button
        class="sub-tab-btn"
        :class="{ active: activeSubTab === 'behavior' }"
        @click="activeSubTab = 'behavior'"
      >
        聊天行为
      </button>
    </div>

    <div class="config-form">
      <!-- 页面外观配置 -->
      <div v-if="activeSubTab === 'appearance'" class="form-section">
        <h3 class="section-title">页面外观</h3>

        <div class="form-group">
          <div class="label-row">
            <label>页面背景</label>
            <div class="current-theme-tag" v-if="currentThemeName">
              当前主题：<span class="theme-name">{{ currentThemeName }}</span>
            </div>
          </div>

          <div class="background-selector">
            <div
              v-for="(group, key) in groupedBackgroundPresets"
              :key="key"
              class="preset-group"
              :class="['group-' + key]"
            >
              <div class="group-title">{{ group.title }}</div>
              <div class="background-presets">
                <div
                  v-for="bg in group.items"
                  :key="bg.name"
                  class="background-preset"
                  :class="{ active: themeStore.config.pageBackground === bg.value }"
                  :style="{ background: bg.value }"
                  @click="selectBackground(bg)"
                  :title="bg.name"
                ></div>
              </div>
            </div>

            <div class="custom-background">
              <span class="custom-label">自定义颜色:</span>
              <n-color-picker
                v-model:value="themeStore.config.customBackground"
                @update:value="themeStore.config.pageBackground = $event"
                :show-alpha="true"
                size="small"
                style="width: 120px;"
              />
            </div>
          </div>
        </div>

        <div class="form-group">
          <label>面板背景</label>
          <div class="background-selector">
            <div class="background-presets">
              <div
                v-for="color in themeStore.panelBackgroundPresets"
                :key="color.name"
                class="background-preset"
                :class="{ active: themeStore.config.panelBackground === color.value }"
                :style="{ background: color.value }"
                @click="themeStore.config.panelBackground = color.value"
                :title="color.name"
              ></div>
            </div>
          </div>
        </div>
      </div>

      <!-- 头像设置配置 -->
      <div v-if="activeSubTab === 'nav'" class="form-section avatar-section">
        <h3 class="section-title">头像设置</h3>
        <p class="avatar-hint">基于 DiceBear 实时生成头像，所有修改自动保存并应用到导航栏账户头像。</p>

        <!-- 用户头像 -->
        <div class="avatar-card">
          <div class="avatar-card-head">
            <span class="avatar-card-title">用户头像</span>
            <n-switch
              v-model:value="themeStore.config.userAvatar.enabled"
              :checked-value="true"
              :unchecked-value="false"
            />
          </div>

          <div class="avatar-editor">
            <!-- 预览区 -->
            <div class="avatar-preview">
              <div class="avatar-preview-box" :class="{ 'is-transparent': themeStore.config.userAvatar.transparent }">
                <img :src="userAvatarUrl" alt="用户头像预览" />
              </div>
              <div class="avatar-preview-meta">
                <code class="avatar-url" :title="userAvatarUrl">{{ userAvatarUrl }}</code>
                <n-button size="tiny" tertiary @click="copyUrl(userAvatarUrl)">复制链接</n-button>
              </div>
            </div>

            <!-- 控制区 -->
            <div class="avatar-controls">
              <div class="avatar-field">
                <label>风格</label>
                <n-select
                  v-model:value="themeStore.config.userAvatar.style"
                  :options="userStyles"
                  placeholder="选择风格"
                />
              </div>

              <div class="avatar-field">
                <label>种子（Seed）</label>
                <div class="avatar-seed-row">
                  <n-input
                    v-model:value="themeStore.config.userAvatar.seed"
                    placeholder="输入任意文字作为种子"
                    clearable
                  />
                  <n-button @click="randomize('user')">随机</n-button>
                </div>
              </div>

              <div class="avatar-field">
                <label>背景</label>
                <div class="avatar-bg-row">
                  <n-switch
                    v-model:value="themeStore.config.userAvatar.transparent"
                    :checked-value="true"
                    :unchecked-value="false"
                  >
                    <template #checked>透明</template>
                    <template #unchecked>纯色</template>
                  </n-switch>
                  <n-color-picker
                    v-if="!themeStore.config.userAvatar.transparent"
                    v-model:value="userBgColor"
                    :show-alpha="false"
                    size="small"
                    style="width: 120px;"
                  />
                </div>
              </div>

              <div class="avatar-field">
                <label>快捷挑选</label>
                <div class="avatar-gallery">
                  <button
                    v-for="word in galleryWords"
                    :key="'u-' + word"
                    type="button"
                    class="gallery-item"
                    :class="{ active: themeStore.config.userAvatar.seed === word }"
                    @click="themeStore.config.userAvatar.seed = word"
                  >
                    <img
                      :src="buildAvatarUrl({ style: themeStore.config.userAvatar.style, seed: word, transparent: themeStore.config.userAvatar.transparent, backgroundColor: themeStore.config.userAvatar.backgroundColor })"
                      :alt="word"
                    />
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- AI 头像 -->
        <div class="avatar-card">
          <div class="avatar-card-head">
            <span class="avatar-card-title">AI 头像</span>
            <n-switch
              v-model:value="themeStore.config.aiAvatar.enabled"
              :checked-value="true"
              :unchecked-value="false"
            />
          </div>

          <div class="avatar-editor">
            <div class="avatar-preview">
              <div class="avatar-preview-box" :class="{ 'is-transparent': themeStore.config.aiAvatar.transparent }">
                <img :src="aiAvatarUrl" alt="AI 头像预览" />
              </div>
              <div class="avatar-preview-meta">
                <code class="avatar-url" :title="aiAvatarUrl">{{ aiAvatarUrl }}</code>
                <n-button size="tiny" tertiary @click="copyUrl(aiAvatarUrl)">复制链接</n-button>
              </div>
            </div>

            <div class="avatar-controls">
              <div class="avatar-field">
                <label>风格</label>
                <n-select
                  v-model:value="themeStore.config.aiAvatar.style"
                  :options="aiStyles"
                  placeholder="选择风格"
                />
              </div>

              <div class="avatar-field">
                <label>种子（Seed）</label>
                <div class="avatar-seed-row">
                  <n-input
                    v-model:value="themeStore.config.aiAvatar.seed"
                    placeholder="输入任意文字作为种子"
                    clearable
                  />
                  <n-button @click="randomize('ai')">随机</n-button>
                </div>
              </div>

              <div class="avatar-field">
                <label>背景</label>
                <div class="avatar-bg-row">
                  <n-switch
                    v-model:value="themeStore.config.aiAvatar.transparent"
                    :checked-value="true"
                    :unchecked-value="false"
                  >
                    <template #checked>透明</template>
                    <template #unchecked>纯色</template>
                  </n-switch>
                  <n-color-picker
                    v-if="!themeStore.config.aiAvatar.transparent"
                    v-model:value="aiBgColor"
                    :show-alpha="false"
                    size="small"
                    style="width: 120px;"
                  />
                </div>
              </div>

              <div class="avatar-field">
                <label>快捷挑选</label>
                <div class="avatar-gallery">
                  <button
                    v-for="word in galleryWords"
                    :key="'a-' + word"
                    type="button"
                    class="gallery-item"
                    :class="{ active: themeStore.config.aiAvatar.seed === word }"
                    @click="themeStore.config.aiAvatar.seed = word"
                  >
                    <img
                      :src="buildAvatarUrl({ style: themeStore.config.aiAvatar.style, seed: word, transparent: themeStore.config.aiAvatar.transparent, backgroundColor: themeStore.config.aiAvatar.backgroundColor })"
                      :alt="word"
                    />
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

<!--          <label>当前主题背景预览</label>-->
<!--          <div class="background-preview-box" :style="{ background: themeStore.config.pageBackground }">-->
<!--            <span class="preview-label">跟随页面主题背景</span>-->
<!--          </div>-->
<!--        </div>-->

<!--        <div v-if="themeStore.config.chatBackgroundType === 'color'" class="form-group">-->
<!--          <label>聊天背景颜色</label>-->
<!--          <div class="background-selector">-->
<!--            <div class="background-presets">-->
<!--              <div-->
<!--                  v-for="bg in themeStore.chatBackgroundPresets"-->
<!--                  :key="bg.name"-->
<!--                  class="background-preset"-->
<!--                  :class="{ active: themeStore.config.chatBackground === bg.value }"-->
<!--                  :style="{ background: bg.value }"-->
<!--                  @click="themeStore.config.chatBackground = bg.value"-->
<!--                  :title="bg.name"-->
<!--              ></div>-->
<!--            </div>-->
<!--          </div>-->
<!--        </div>-->

<!--        &lt;!&ndash; 聊天背景图片配置 &ndash;&gt;-->
<!--        <div v-if="themeStore.config.chatBackgroundType === 'image'" class="form-group">-->
<!--          <div class="form-group">-->
<!--            <div class="upload-group">-->
<!--              <label>上传背景图片</label>-->
<!--              <input-->
<!--                  type="file"-->
<!--                  @change="handleBackgroundImageUpload"-->
<!--                  accept="image/*"-->
<!--                  style="display: none"-->
<!--                  ref="fileInput"-->
<!--              />-->
<!--              <n-button @click="fileInput.click()">-->
<!--                选择图片-->
<!--              </n-button>-->
<!--            </div>-->
<!--            <div v-if="themeStore.config.chatBackgroundImage" class="image-preview-wrapper">-->
<!--              <img :src="themeStore.config.chatBackgroundImage" class="image-preview" alt="背景图片预览"/>-->
<!--              <button @click="themeStore.config.chatBackgroundImage = ''" class="btn-remove-image">×</button>-->
<!--            </div>-->
<!--          </div>-->

<!--          <div class="form-group">-->
<!--            <label>背景图片透明度</label>-->
<!--            <div class="slider-row">-->
<!--              <n-slider-->
<!--                  v-model:value="themeStore.config.chatBackgroundImageOpacity"-->
<!--                  :min="0.1"-->
<!--                  :max="1"-->
<!--                  :step="0.05"-->
<!--                  :tooltip="true"-->
<!--                  style="flex: 1;"-->
<!--              />-->
<!--              <span class="slider-value-text">{{ themeStore.config.chatBackgroundImageOpacity.toFixed(2) }}</span>-->
<!--            </div>-->
<!--          </div>-->
<!--        </div>-->

      <!-- 字体设置 -->
      <div v-if="activeSubTab === 'font'" class="form-section">
        <h3 class="section-title">字体设置</h3>
        <div class="form-group">
          <label>页面字体</label>
          <n-select
            v-model:value="themeStore.config.fontFamily"
            :options="fontOptions"
            placeholder="请选择字体"
          />
        </div>

        <div class="form-group">
          <label>字体大小</label>
          <div class="slider-row">
            <n-slider
              v-model:value="themeStore.config.fontSize"
              :min="12"
              :max="20"
              :step="1"
              :tooltip="true"
              style="flex: 1;"
            />
            <span class="slider-value-text">{{ themeStore.config.fontSize }}px</span>
          </div>
          <div class="font-preview" :style="fontPreviewStyle">
            这是字体预览文本 - Font Preview Text
          </div>
        </div>
      </div>

      <!-- 聊天样式配置 -->
      <div v-if="activeSubTab === 'chat'" class="form-section">
        <h3 class="section-title">聊天样式配置</h3>

        <div class="form-group">
          <div class="label-row">
            <label>聊天皮肤</label>
            <div class="current-theme-tag" v-if="currentSkinName">
              当前皮肤：<span class="theme-name">{{ currentSkinName }}</span>
            </div>
          </div>
          <div class="background-selector">
            <div class="skin-cards">
              <div
                v-for="skin in themeStore.skinPresets"
                :key="skin.name"
                class="skin-card"
                :class="{ active: themeStore.config.chatSkin === skin.name }"
                @click="themeStore.setChatSkin(skin.name)"
                :title="skin.label"
              >
                <div class="skin-card-preview" :style="skinPreviewStyle(skin)">
                  <div class="sc-titlebar" :style="skinTitlebarStyle(skin)">
                    <span class="sc-titlebar-dot" :style="{ background: skinVar(skin, '--chat-titlebar-text', '#ffffff') }"></span>
                  </div>
                  <div class="sc-body">
                    <div class="sc-bubble sc-ai" :style="skinAiBubbleStyle(skin)"></div>
                    <div class="sc-bubble sc-user" :style="skinUserBubbleStyle(skin)"></div>
                  </div>
                </div>
                <div class="skin-card-name">
                  <span>{{ skin.label }}</span>
                  <span v-if="themeStore.config.chatSkin === skin.name" class="skin-card-current">当前</span>
                </div>
              </div>
            </div>
            <span class="skin-hint">切换 /chat 聊天页面的整体皮肤，选择后立即生效并自动保存</span>
          </div>
        </div>

        <div class="form-group">
          <label>聊天背景类型</label>
          <n-radio-group v-model:value="themeStore.config.chatBackgroundType" orientation="horizontal">
            <n-radio value="theme">跟随主题</n-radio>
            <n-radio value="color">纯色背景</n-radio>
            <n-radio value="image">图片背景</n-radio>
          </n-radio-group>
        </div>

        <div v-if="themeStore.config.chatBackgroundType === 'theme'" class="form-group">
          <label>当前主题背景预览</label>
          <div class="background-preview-box" :style="{ background: themeStore.config.pageBackground }">
            <span class="preview-label">跟随页面主题背景</span>
          </div>
        </div>

        <div v-if="themeStore.config.chatBackgroundType === 'color'" class="form-group">
          <label>聊天背景颜色</label>
          <div class="background-selector">
            <div class="background-presets">
              <div
                v-for="bg in themeStore.chatBackgroundPresets"
                :key="bg.name"
                class="background-preset"
                :class="{ active: themeStore.config.chatBackground === bg.value }"
                :style="{ background: bg.value }"
                @click="themeStore.config.chatBackground = bg.value"
                :title="bg.name"
              ></div>
            </div>
          </div>
        </div>

        <!-- 聊天背景图片配置 -->
        <div v-if="themeStore.config.chatBackgroundType === 'image'" class="form-group">
          <div class="form-group">
            <div class="upload-group">
              <label>上传背景图片</label>
              <input
                type="file"
                @change="handleBackgroundImageUpload"
                accept="image/*"
                style="display: none"
                ref="fileInput"
              />
              <n-button @click="fileInput.click()">
                选择图片
              </n-button>
            </div>
            <div v-if="themeStore.config.chatBackgroundImage" class="image-preview-wrapper">
              <img :src="themeStore.config.chatBackgroundImage" class="image-preview" alt="背景图片预览"/>
              <button @click="themeStore.config.chatBackgroundImage = ''" class="btn-remove-image">×</button>
            </div>
          </div>

          <div class="form-group">
            <label>背景图片透明度</label>
            <div class="slider-row">
              <n-slider
                v-model:value="themeStore.config.chatBackgroundImageOpacity"
                :min="0.1"
                :max="1"
                :step="0.05"
                :tooltip="true"
                style="flex: 1;"
              />
              <span class="slider-value-text">{{ themeStore.config.chatBackgroundImageOpacity.toFixed(2) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 消息样式配置 -->
      <div v-if="activeSubTab === 'message'" class="form-section">
        <h3 class="section-title">消息样式配置</h3>

        <div class="form-group">
          <label>消息间距</label>
          <div class="slider-row">
            <n-slider
              v-model:value="themeStore.config.messageSpacing"
              :min="8"
              :max="24"
              :step="2"
              :tooltip="true"
              style="flex: 1;"
            />
            <span class="slider-value-text">{{ themeStore.config.messageSpacing }}px</span>
          </div>
        </div>

        <div class="form-group">
          <label>消息内边距</label>
          <div class="slider-row">
            <n-slider
              v-model:value="themeStore.config.messagePadding"
              :min="8"
              :max="20"
              :step="2"
              :tooltip="true"
              style="flex: 1;"
            />
            <span class="slider-value-text">{{ themeStore.config.messagePadding }}px</span>
          </div>
        </div>
      </div>

      <!-- 代码样式配置 -->
      <div v-if="activeSubTab === 'code'" class="form-section">
        <h3 class="section-title">代码样式配置</h3>

        <div class="form-group">
          <label>行内代码背景</label>
          <div class="background-selector">
            <div class="background-presets">
              <div
                v-for="color in themeStore.inlineCodeBackgroundPresets"
                :key="color.name"
                class="background-preset"
                :class="{ active: themeStore.config.inlineCodeBackground === color.value }"
                :style="{ background: color.value }"
                @click="themeStore.config.inlineCodeBackground = color.value"
                :title="color.name"
              ></div>
            </div>
          </div>
        </div>

        <div class="form-group">
          <label>代码字体大小</label>
          <div class="slider-row">
            <n-slider
              v-model:value="themeStore.config.codeFontSize"
              :min="10"
              :max="18"
              :step="1"
              :tooltip="true"
              style="flex: 1;"
            />
            <span class="slider-value-text">{{ themeStore.config.codeFontSize }}px</span>
          </div>
        </div>
      </div>

      <!-- 表情样式配置 -->
      <div v-if="activeSubTab === 'emoji'" class="form-section">
        <EmojiSettingSection />
      </div>

      <!-- 聊天行为配置 -->
      <div v-if="activeSubTab === 'behavior'" class="form-section">
        <h3 class="section-title">聊天行为配置</h3>

        <div class="form-group setting-item">
          <div class="setting-label">
            <span class="setting-title">自动滚动到底部</span>
            <span class="setting-desc">新消息到达时自动滚动到页面底部</span>
          </div>
          <n-switch v-model:value="themeStore.config.autoScroll" />
        </div>

        <div class="form-group setting-item">
          <div class="setting-label">
            <span class="setting-title">显示消息时间戳</span>
            <span class="setting-desc">在消息旁边显示发送时间</span>
          </div>
          <n-switch v-model:value="themeStore.config.showTimestamp" />
        </div>

        <div class="form-group setting-item">
          <div class="setting-label">
            <span class="setting-title">流式结束后解析 Markdown</span>
            <span class="setting-desc">流式回复期间仅显示纯文本，结束后再完整解析 Markdown，减少长回复的解析卡顿</span>
          </div>
          <n-switch v-model:value="themeStore.config.parseMdAfterStream" />
        </div>

      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useThemeStore } from '@/stores/theme'
import { getAvailableStyles, buildAvatarUrl } from '@/shared/utils/avatarUtils'
import { EmojiSettingSection } from '@/modules/tools/components/index.js'
import {
  NSwitch,
  NButton,
  NSlider,
  NColorPicker,
  NRadioGroup,
  NRadio,
  NSelect,
  useMessage
} from 'naive-ui'

const themeStore = useThemeStore()
const message = useMessage()

const activeSubTab = ref('appearance')
const fileInput = ref(null)

// 字体选项
const fontOptions = [
  { label: '系统默认字体', value: 'system' },
  { label: '微软雅黑', value: "'Microsoft YaHei', sans-serif" },
  { label: '苹方', value: "'PingFang SC', sans-serif" },
  { label: '思源黑体', value: "'Noto Sans SC', sans-serif" }
]

const currentThemeName = computed(() => {
  const currentBg = themeStore.config.pageBackground
  const found = themeStore.backgroundPresets.find(p => p.value === currentBg)
  return found ? found.name : '自定义背景'
})

const currentSkinName = computed(() => {
  const found = themeStore.skinPresets.find(s => s.name === themeStore.config.chatSkin)
  return found ? found.label : ''
})

// —— 皮肤迷你预览：用皮肤真实 token 渲染 ——
const skinVar = (skin, name, fallback = '') => skin.vars?.[name] || fallback

const skinPreviewStyle = (skin) => ({
  background: skinVar(skin, '--chat-wallpaper', '#eef2f7'),
  borderRadius: skinVar(skin, '--chat-radius', '8px')
})

const skinTitlebarStyle = (skin) => {
  const start = skinVar(skin, '--chat-titlebar-start', '#000080')
  const end = skinVar(skin, '--chat-titlebar-end', '#1084d0')
  return { background: `linear-gradient(90deg, ${start} 0%, ${end} 100%)` }
}

const skinAiBubbleStyle = (skin) => ({
  background: skinVar(skin, '--chat-ai-bubble-bg', '#ffffff'),
  borderColor: skinVar(skin, '--chat-ai-bubble-border', 'transparent')
})

const skinUserBubbleStyle = (skin) => ({
  background: skinVar(skin, '--chat-user-bubble-bg', '#3b82f6')
})

const groupedBackgroundPresets = computed(() => {
  const groups = {
    basic: '基础主题',
    nature: '自然风光',
    colorful: '缤纷色彩',
    game: '游戏主题',
    chinese: '国风雅韵',
    other: '其他风格'
  }

  const result = {}

  // Initialize order
  Object.keys(groups).forEach(key => {
    result[key] = {
      title: groups[key],
      items: []
    }
  })

  themeStore.backgroundPresets.forEach(preset => {
    const category = preset.category || 'other'
    if (!result[category]) {
      result[category] = {
        title: groups[category] || '其他风格',
        items: []
      }
    }
    result[category].items.push(preset)
  })

  // Filter out empty groups
  const finalResult = {}
  Object.keys(result).forEach(key => {
    if (result[key].items.length > 0) {
      finalResult[key] = result[key]
    }
  })

  return finalResult
})

// 方法
const selectBackground = (bg) => {
  themeStore.config.pageBackground = bg.value
  themeStore.config.isThemeDark = bg.isDark
  // 智能匹配面板背景色
  if (bg.isDark) {
    themeStore.config.panelBackground = 'rgba(0, 0, 0, 0.3)'
  } else {
    themeStore.config.panelBackground = 'rgba(255, 255, 255, 0.5)'
  }
}

// 字体预览样式
const fontPreviewStyle = computed(() => ({
  fontFamily: themeStore.config.fontFamily === 'system' ? 'system-ui, -apple-system, sans-serif' : themeStore.config.fontFamily,
  fontSize: themeStore.config.fontSize + 'px'
}))

const handleBackgroundImageUpload = (event) => {
  const file = event.target.files[0]
  if (file) {
    const reader = new FileReader()
    reader.onload = (e) => {
      themeStore.config.chatBackgroundImage = e.target.result
    }
    reader.readAsDataURL(file)
  }
}

// ---- 头像设置（DiceBear） ----
const userStyles = getAvailableStyles('user').map(s => ({ label: `${s.label} · ${s.description}`, value: s.name }))
const aiStyles = getAvailableStyles('ai').map(s => ({ label: `${s.label} · ${s.description}`, value: s.name }))

// 快捷挑选候选词
const galleryWords = ['小江子', '青松', '星河', '晨曦', '墨白', '清风', '云朵', '柠檬', 'panda', 'tiger', 'rocket', 'coffee']

const userAvatarUrl = computed(() => buildAvatarUrl(themeStore.config.userAvatar))
const aiAvatarUrl = computed(() => buildAvatarUrl(themeStore.config.aiAvatar))

// n-color-picker 返回带 # 的 hex，这里做双向转换
const userBgColor = computed({
  get: () => '#' + (themeStore.config.userAvatar.backgroundColor || 'b6e3f4').replace('#', ''),
  set: (v) => { themeStore.config.userAvatar.backgroundColor = (v || '').replace('#', '') }
})
const aiBgColor = computed({
  get: () => '#' + (themeStore.config.aiAvatar.backgroundColor || 'b6e3f4').replace('#', ''),
  set: (v) => { themeStore.config.aiAvatar.backgroundColor = (v || '').replace('#', '') }
})

// 随机种子
function randomize(type) {
  const cfg = type === 'user' ? themeStore.config.userAvatar : themeStore.config.aiAvatar
  cfg.seed = Math.random().toString(36).slice(2, 10)
}

// 复制头像链接
async function copyUrl(url) {
  try {
    await navigator.clipboard.writeText(url)
    message.success('已复制头像链接')
  } catch (e) {
    message.error('复制失败，请手动复制')
  }
}
</script>

<style scoped>
.system-setting-content {
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

/* 子标签页 */
.sub-tabs {
  display: flex;
  gap: 0.25rem;
  margin-bottom: 1.25rem;
  padding: 0.25rem;
  background: var(--app-bg-secondary, #f3f4f6);
  border-radius: 10px;
  overflow-x: auto;
}
.sub-tab-btn {
  padding: 0.6rem 1.2rem;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--app-text-secondary, #6b7280);
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
  font-size: 0.875rem;
}
.sub-tab-btn:hover {
  background: var(--app-hover-bg, rgba(255, 255, 255, 0.8));
  color: var(--app-text-primary, #1f2937);
}
.sub-tab-btn.active {
  background: var(--app-component-bg, white);
  color: var(--app-text-primary, #1f2937);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

/* 表单容器 */
.config-form {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

/* 表单区域 */
.form-section {
  padding: 1.25rem;
  background: var(--app-component-bg, rgba(255, 255, 255, 0.6));
  border-radius: 12px;
  border: 1px solid var(--app-border-color, #e5e7eb);
  margin-bottom: 1.25rem;
}
.form-section:last-child {
  margin-bottom: 0;
}

.section-title {
  margin: 0 0 1.25rem 0;
  padding-bottom: 0.75rem;
  font-size: 1rem;
  font-weight: 600;
  color: var(--app-text-primary, #374151);
  border-bottom: 1px solid var(--app-border-color, #e5e7eb);
}

/* 表单组 */
.form-group {
  margin-bottom: 1.25rem;
  padding-bottom: 1.25rem;
  border-bottom: 1px solid var(--app-border-color, #e5e7eb);
}
.form-group:last-child {
  margin-bottom: 0;
  padding-bottom: 0;
  border-bottom: none;
}
.form-group>label {
  display: flex;
  margin-bottom: 0.75rem;
  font-weight: 600;
  color: var(--app-text-primary, #374151);
  font-size: 0.95rem;
}

/* 标签行 */
.label-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

.current-theme-tag {
  font-size: 0.8rem;
  color: var(--app-text-secondary, #6b7280);
  background: var(--app-bg-secondary, #f3f4f6);
  padding: 3px 10px;
  border-radius: 12px;
}
.theme-name {
  color: var(--app-active-bg, #3b82f6);
  font-weight: 600;
  margin-left: 4px;
}

/* 背景选择器 */
.background-selector {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  padding: 1rem;
  background: var(--app-bg-secondary, #f9fafb);
  border-radius: 10px;
}
.background-presets {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}
.background-preset {
  width: 48px;
  height: 32px;
  border-radius: 6px;
  cursor: pointer;
  border: 2px solid transparent;
  transition: all 0.2s ease;
  position: relative;
  overflow: hidden;
  flex-shrink: 0;
}
.background-preset:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}
.background-preset.active {
  border-color: var(--app-active-bg, #3b82f6);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.25);
}
.background-preset.active::after {
  content: '✓';
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: var(--app-active-text, white);
  font-weight: bold;
  font-size: 16px;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.5);
}

.skin-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 0.75rem;
}

.skin-card {
  border: 2px solid rgba(0, 0, 0, 0.12);
  border-radius: 8px;
  cursor: pointer;
  overflow: hidden;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
  box-sizing: border-box;

  &:hover {
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
  }

  &.active {
    border-color: var(--app-active-bg, #3b82f6);
    box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.25);
  }
}

.skin-card-preview {
  position: relative;
  aspect-ratio: 16 / 10;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sc-titlebar {
  height: 16px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding: 0 6px;
}

.sc-titlebar-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.sc-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  padding: 8px;
}

.sc-bubble {
  height: 12px;
  border-radius: 6px;
}

.sc-ai {
  align-self: flex-start;
  width: 60%;
  border: 1px solid transparent;
}

.sc-user {
  align-self: flex-end;
  width: 40%;
}

.skin-card-name {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
  padding: 6px 8px;
  font-size: 12px;
  color: var(--app-text-primary, #1f2937);
  border-top: 1px solid var(--app-border-color, rgba(0, 0, 0, 0.06));
}

.skin-card-current {
  flex-shrink: 0;
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 8px;
  background: var(--app-active-bg, #3b82f6);
  color: #fff;
}

.skin-hint {
  font-size: 0.8rem;
  color: var(--app-text-secondary, #6b7280);
}

/* 预设分组 */
.preset-group {
  margin-bottom: 1rem;
}
.preset-group:last-child {
  margin-bottom: 0;
}
.group-title {
  font-size: 0.85rem;
  color: var(--app-text-secondary, #6b7280);
  margin-bottom: 0.5rem;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

/* 自定义背景 */
.custom-background {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  background: var(--app-component-bg, white);
  border-radius: 8px;
  border: 1px solid var(--app-border-color, #e5e7eb);
}
.custom-label {
  font-size: 0.85rem;
  color: var(--app-text-secondary, #6b7280);
  white-space: nowrap;
}

/* 滑块行 */
.slider-row {
  display: flex;
  align-items: center;
  gap: 1rem;
}
.slider-value-text {
  min-width: 50px;
  text-align: center;
  font-weight: 500;
  color: var(--app-text-primary, #374151);
  font-size: 0.9rem;
  padding: 0.25rem 0.5rem;
  background: var(--app-bg-secondary, #f3f4f6);
  border-radius: 4px;
}

/* 字体预览 */
.font-preview {
  margin-top: 0.5rem;
  padding: 0.75rem;
  background: var(--app-component-bg, rgba(255, 255, 255, 0.95));
  border-radius: 6px;
  border: 1px solid var(--app-border-color, rgba(0, 0, 0, 0.1));
  color: var(--app-text-primary, #374151);
}

/* 设置项样式 */
.setting-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem;
  background: var(--app-bg-secondary, #f9fafb);
  border-radius: 10px;
  margin-bottom: 0.75rem;
  transition: all 0.2s ease;
}
.setting-item:hover {
  background: var(--app-hover-bg, #f3f4f6);
}
.setting-label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}
.setting-title {
  font-weight: 500;
  color: var(--app-text-primary, #1f2937);
  font-size: 0.95rem;
}
.setting-desc {
  font-size: 0.8rem;
  color: var(--app-text-secondary, #6b7280);
}

/* 上传组 */
.upload-group {
  display: flex;
  align-items: center;
  gap: 1rem;
}
.upload-group label {
  display: inline;
  margin-bottom: 0;
  white-space: nowrap;
}

/* 背景预览框 */
.background-preview-box {
  width: 100%;
  height: 60px;
  border-radius: 8px;
  border: 1px solid var(--app-border-color, #e5e7eb);
  display: flex;
  align-items: center;
  justify-content: center;
}
.preview-label {
  font-size: 12px;
  color: var(--app-text-secondary, #6b7280);
  background: rgba(255, 255, 255, 0.6);
  padding: 2px 8px;
  border-radius: 4px;
}

/* 图片预览 */
.image-preview-wrapper {
  position: relative;
}
.image-preview {
  width: 120px;
  height: 70px;
  border-radius: 8px;
  object-fit: cover;
  border: 1px solid var(--app-border-color, #e5e7eb);
}
.btn-remove-image {
  position: absolute;
  top: -5px;
  right: -5px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.6);
  color: white;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  line-height: 1;
}

/* 暗色模式 */
:global(.dark) .sub-tab-btn.active {
  background: rgba(59, 130, 246, 0.2);
}
:global(.dark) .form-section {
  background: rgba(255, 255, 255, 0.03);
  border-color: rgba(255, 255, 255, 0.08);
}

/* ===== 头像设置 ===== */
.avatar-hint {
  margin: -8px 0 16px;
  font-size: 0.82rem;
  color: var(--app-text-secondary, #6b7280);
}

.avatar-card {
  padding: 16px;
  margin-bottom: 18px;
  background: var(--app-panel-background, rgba(255, 255, 255, 0.6));
  border: 1px solid var(--app-border-color, #e5e7eb);
  border-radius: 14px;
}
.avatar-card:last-child {
  margin-bottom: 0;
}

.avatar-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.avatar-card-title {
  font-weight: 600;
  font-size: 1rem;
  color: var(--app-text-primary, #374151);
}

.avatar-editor {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

.avatar-preview {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.avatar-preview-box {
  width: 200px;
  height: 200px;
  border-radius: 18px;
  border: 1px solid var(--app-border-color, #e5e7eb);
  background:
    linear-gradient(45deg, #f1f5f9 25%, transparent 25%) -10px 0 / 20px 20px,
    linear-gradient(-45deg, #f1f5f9 25%, transparent 25%) -10px 0 / 20px 20px,
    linear-gradient(45deg, transparent 75%, #f1f5f9 75%) -10px 0 / 20px 20px,
    linear-gradient(-45deg, transparent 75%, #f1f5f9 75%) -10px 0 / 20px 20px,
    #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.avatar-preview-box.is-transparent {
  background:
    linear-gradient(45deg, #eef2f7 25%, transparent 25%) -10px 0 / 20px 20px,
    linear-gradient(-45deg, #eef2f7 25%, transparent 25%) -10px 0 / 20px 20px,
    linear-gradient(45deg, transparent 75%, #eef2f7 75%) -10px 0 / 20px 20px,
    linear-gradient(-45deg, transparent 75%, #eef2f7 75%) -10px 0 / 20px 20px,
    #ffffff;
}
.avatar-preview-box img {
  width: 88%;
  height: 88%;
  object-fit: contain;
}
.avatar-preview-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
}
.avatar-url {
  display: block;
  max-width: 200px;
  font-size: 0.7rem;
  color: var(--app-text-secondary, #6b7280);
  background: var(--app-bg-secondary, #f3f4f6);
  padding: 4px 8px;
  border-radius: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.avatar-controls {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.avatar-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.avatar-field > label {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--app-text-primary, #374151);
  margin: 0;
}
.avatar-seed-row,
.avatar-bg-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.avatar-seed-row :deep(.n-input) {
  flex: 1;
}

.avatar-gallery {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 8px;
}
.gallery-item {
  aspect-ratio: 1 / 1;
  padding: 6px;
  border-radius: 12px;
  border: 2px solid transparent;
  background: var(--app-bg-secondary, #f3f4f6);
  cursor: pointer;
  transition: all 0.18s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}
.gallery-item:hover {
  transform: translateY(-2px);
  border-color: var(--app-active-bg, #3b82f6);
}
.gallery-item.active {
  border-color: var(--app-active-bg, #3b82f6);
  background: color-mix(in srgb, var(--app-active-bg, #3b82f6) 10%, #ffffff 90%);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.18);
}
.gallery-item img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  border-radius: 8px;
}

@media (max-width: 640px) {
  .avatar-editor {
    grid-template-columns: 1fr;
  }
  .avatar-preview-box {
    width: 100%;
    height: 160px;
  }
  .avatar-url {
    max-width: 100%;
  }
  .avatar-gallery {
    grid-template-columns: repeat(4, 1fr);
  }
}
</style>
