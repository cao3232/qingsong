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
        :class="{ active: activeSubTab === 'mermaid' }"
        @click="activeSubTab = 'mermaid'"
      >
        图表样式
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
                @update:value="selectCustomBackground"
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
              <div class="avatar-preview-box">
                <img :src="userAvatarUrl" alt="用户头像预览" />
              </div>
              <div class="avatar-preview-meta">
                <code class="avatar-url" :title="userAvatarUrl">{{ userAvatarUrl }}</code>
                <n-button size="tiny" tertiary @click="copyUrl(userAvatarUrl)">复制链接</n-button>
              </div>
            </div>

            <!-- 控制区：风格/背景 一行两列，种子与快捷挑选通栏 -->
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
                    style="width: 110px;"
                  />
                </div>
              </div>

              <div class="avatar-field avatar-field-full">
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

              <div class="avatar-field avatar-field-full">
                <label>快捷挑选</label>
                <div class="avatar-gallery">
                  <button
                    v-for="word in galleryWords"
                    :key="'u-' + word"
                    type="button"
                    class="gallery-item"
                    :class="{ active: themeStore.config.userAvatar.seed === word }"
                    :title="word"
                    @click="themeStore.config.userAvatar.seed = word"
                  >
                    <img
                      :src="buildAvatarUrl({ style: themeStore.config.userAvatar.style, seed: word, transparent: themeStore.config.userAvatar.transparent, backgroundColor: themeStore.config.userAvatar.backgroundColor })"
                      :alt="word"
                      loading="lazy"
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
              <div class="avatar-preview-box">
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
                    style="width: 110px;"
                  />
                </div>
              </div>

              <div class="avatar-field avatar-field-full">
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

              <div class="avatar-field avatar-field-full">
                <label>快捷挑选</label>
                <div class="avatar-gallery">
                  <button
                    v-for="word in galleryWords"
                    :key="'a-' + word"
                    type="button"
                    class="gallery-item"
                    :class="{ active: themeStore.config.aiAvatar.seed === word }"
                    :title="word"
                    @click="themeStore.config.aiAvatar.seed = word"
                  >
                    <img
                      :src="buildAvatarUrl({ style: themeStore.config.aiAvatar.style, seed: word, transparent: themeStore.config.aiAvatar.transparent, backgroundColor: themeStore.config.aiAvatar.backgroundColor })"
                      :alt="word"
                      loading="lazy"
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
        <div class="form-grid">
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
          <div class="background-preview-box" :style="{ background: themeStore.effectivePageBackground }">
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

        <div class="form-grid">
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

        <div class="form-group">
          <label>效果预览</label>
          <div class="message-preview" :style="{ gap: themeStore.config.messageSpacing + 'px' }">
            <div
              class="mp-bubble mp-ai"
              :style="{ padding: themeStore.config.messagePadding + 'px' }"
            >你好，我是 AI 助手，有什么可以帮你？</div>
            <div
              class="mp-bubble mp-user"
              :style="{ padding: themeStore.config.messagePadding + 'px' }"
            >帮我看下这段配置</div>
            <div
              class="mp-bubble mp-ai"
              :style="{ padding: themeStore.config.messagePadding + 'px' }"
            >没问题，这是调整后的效果。</div>
          </div>
        </div>
      </div>

      <!-- 代码样式配置 -->
      <div v-if="activeSubTab === 'code'" class="form-section">
        <h3 class="section-title">代码样式配置</h3>

        <div class="form-grid">
          <div class="form-group">
            <label>代码主题</label>
            <n-select
              v-model:value="themeStore.config.codeTheme"
              :options="codeThemeOptions"
              placeholder="请选择代码主题"
            />
            <p class="mermaid-hint">「跟随皮肤」按页面/皮肤明暗自动切换浅色或深色高亮；浅色主题下代码块背景自动变浅</p>
          </div>

          <div class="form-group">
            <label>代码字体</label>
            <n-select
              v-model:value="themeStore.config.codeFont"
              :options="codeFontOptions"
              placeholder="请选择代码字体"
            />
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

          <div class="form-group">
            <label>代码块背景</label>
            <div class="background-selector">
              <div class="background-presets">
                <div
                  v-for="color in codeBlockBackgroundPresets"
                  :key="color.name"
                  class="background-preset"
                  :class="{ active: themeStore.config.codeBlockBackground === color.value }"
                  :style="{ background: color.value }"
                  @click="themeStore.config.codeBlockBackground = color.value"
                  :title="color.name"
                ></div>
              </div>
            </div>
            <p class="mermaid-hint">仅深色主题生效</p>
          </div>

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
        </div>

        <div class="form-group">
          <label>实时预览</label>
          <div class="code-preview-box">
            <div class="code-preview-render" v-html="codePreviewHtml"></div>
          </div>
        </div>
      </div>

      <!-- 图表样式（Mermaid）配置 -->
      <div v-if="activeSubTab === 'mermaid'" class="form-section">
        <h3 class="section-title">图表样式（Mermaid）</h3>

        <div class="form-grid">
          <div class="form-group">
            <label>图表主题</label>
            <n-select
              v-model:value="themeStore.config.mermaidTheme"
              :options="mermaidThemeOptions"
              placeholder="请选择图表主题"
            />
            <p class="mermaid-hint">「跟随皮肤」会按页面/皮肤明暗自动切换深色或中性主题，作用于聊天中的流程图等 mermaid 图表</p>
          </div>

          <div class="form-group">
            <label>手绘风格</label>
            <div class="inline-switch-row">
              <n-switch v-model:value="mermaidHandDrawn" />
              <span class="switch-hint">开启后以手绘笔触（handDrawn）渲染图表，关闭为经典样式</span>
            </div>
          </div>
        </div>

        <div class="form-group">
          <label>实时预览</label>
          <div class="mermaid-preview-box">
            <div v-if="mermaidPreviewError" class="mermaid-preview-error">
              <span>预览渲染失败，示例源码：</span>
              <pre>{{ MERMAID_SAMPLE }}</pre>
            </div>
            <div v-else ref="mermaidPreviewRef" class="mermaid-preview-render"></div>
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

        <div class="settings-grid">
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
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { useThemeStore } from '@/stores/theme'
import { getAvailableStyles, buildAvatarUrl } from '@/shared/utils/avatarUtils'
import { getMermaid, applyMermaidConfig } from '@/services/mermaid.js'
import hljs from 'highlight.js'
import { EmojiSettingSection } from '@/modules/tools/components/index.js'
import {
  NSwitch,
  NButton,
  NSlider,
  NColorPicker,
  NRadioGroup,
  NRadio,
  NSelect,
  NInput,
  useMessage
} from 'naive-ui'

const themeStore = useThemeStore()
const message = useMessage()

const activeSubTab = ref('appearance')
const fileInput = ref(null)

// ---- 图表样式（Mermaid）----
const mermaidThemeOptions = [
  { label: '跟随皮肤（自动明暗）', value: 'auto' },
  { label: '中性 neutral', value: 'neutral' },
  { label: '默认 default', value: 'default' },
  { label: '深色 dark', value: 'dark' },
  { label: '森林 forest', value: 'forest' },
  { label: '基础 base', value: 'base' }
]

// 手绘开关 ↔ mermaidLook 字符串值的双向转换
const mermaidHandDrawn = computed({
  get: () => themeStore.config.mermaidLook === 'handDrawn',
  set: (v) => { themeStore.config.mermaidLook = v ? 'handDrawn' : 'classic' }
})

// 预览示例：含决策分支，能体现主题差异
const MERMAID_SAMPLE = `flowchart LR
  A[用户提问] --> B{理解意图}
  B -->|清晰| C[生成回复]
  B -->|模糊| D[追问澄清]
  D --> B
  C --> E[展示结果]`

const mermaidPreviewRef = ref(null)
const mermaidPreviewError = ref(false)

// 渲染/重渲染预览图（仅在图表样式标签页激活时执行；mermaid 按需懒加载）
const renderMermaidPreview = async () => {
  if (activeSubTab.value !== 'mermaid') return
  await nextTick()
  if (!mermaidPreviewRef.value) return

  const id = `mermaid-preview-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`
  try {
    const mermaid = await getMermaid()
    applyMermaidConfig(mermaid)
    const { svg } = await mermaid.render(id, MERMAID_SAMPLE)
    // 防御：渲染期间用户可能已切走标签
    if (activeSubTab.value === 'mermaid' && mermaidPreviewRef.value) {
      mermaidPreviewRef.value.innerHTML = svg
      mermaidPreviewError.value = false
    }
  } catch (error) {
    console.warn('mermaid 预览渲染失败:', error)
    if (activeSubTab.value === 'mermaid') mermaidPreviewError.value = true
  } finally {
    document.getElementById(`d-${id}`)?.remove()
  }
}

// 打开标签或配置/生效明暗变化时刷新预览
watch(
  () => [activeSubTab.value, themeStore.config.mermaidTheme, themeStore.config.mermaidLook, themeStore.effectiveIsDark],
  () => { renderMermaidPreview() }
)

// 字体选项
const fontOptions = [
  { label: '系统默认字体', value: 'system' },
  { label: '微软雅黑', value: "'Microsoft YaHei', sans-serif" },
  { label: '苹方', value: "'PingFang SC', sans-serif" },
  { label: '思源黑体', value: "'Noto Sans SC', sans-serif" }
]

// ---- 代码样式（代码主题 / 块背景 / 字体 / 预览）----
const codeThemeOptions = [
  { label: '跟随皮肤（自动明暗）', value: 'auto' },
  { label: '浅色', value: 'light' },
  { label: '深色', value: 'dark' }
]

const codeFontOptions = [
  { label: '系统等宽字体', value: 'system' },
  { label: 'JetBrains Mono', value: 'JetBrains Mono' },
  { label: 'Fira Code', value: 'Fira Code' },
  { label: 'Consolas', value: 'Consolas' },
  { label: 'Cascadia Code', value: 'Cascadia Code' },
  { label: 'Courier New', value: 'Courier New' }
]

const codeBlockBackgroundPresets = [
  { name: '深灰蓝', value: '#1e293b' },
  { name: '石墨黑', value: '#0d1117' },
  { name: '午夜黑', value: '#161b22' },
  { name: '暖深棕', value: '#24292f' },
  { name: '墨蓝', value: '#1f2937' }
]

// 代码预览示例：含注释/字符串/函数/模板串，能体现高亮配色差异
const CODE_SAMPLE = `function greet(name) {
  // 你好，世界
  const message = \`Hello, \${name}!\`
  console.log(message)
  return message
}

const result = greet('青松')
console.log(result)`

const escapeHtml = value =>
  String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

const codePreviewHtml = computed(() => {
  try {
    const highlighted = hljs.highlight(CODE_SAMPLE, { language: 'javascript' }).value
    return `<pre class="code-preview-pre"><code class="language-js hljs">${highlighted}</code></pre>`
  } catch (error) {
    console.warn('代码预览高亮失败:', error)
    return `<pre class="code-preview-pre"><code class="language-js hljs">${escapeHtml(CODE_SAMPLE)}</code></pre>`
  }
})

// 「当前主题」按生效背景匹配：皮肤固定背景时显示皮肤来源，避免标签与实际页面背景不符
const currentThemeName = computed(() => {
  const effectiveBg = themeStore.effectivePageBackground
  const found = themeStore.backgroundPresets.find(p => p.value === effectiveBg)
  if (found) return found.name
  const skin = themeStore.skinPresets.find(s => s.name === themeStore.config.chatSkin)
  if (skin?.pageBackground === effectiveBg && !skin.followPageBackground) return `${skin.label}（皮肤）`
  return '自定义背景'
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
  // 明暗由 themeStore.effectiveIsDark 推导（预设取标志/自定义取亮度/固定皮肤取皮肤），无需手动写 isThemeDark；
  // 面板按「生效明暗」匹配：固定背景皮肤激活时点浅色预设，生效明暗仍是皮肤的深色，
  // 若按 bg.isDark 匹配会把面板错切成白玻璃（白字白底冲突）
  themeStore.matchPanelToTheme()
}

// 自定义取色：没有预设标志，明暗由 effectiveIsDark 按颜色亮度推导，面板按生效明暗同步匹配
const selectCustomBackground = (color) => {
  themeStore.config.pageBackground = color
  themeStore.matchPanelToTheme()
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

/* 子标签页（分段控件风：激活态实心主色；溢出可横向滚动但隐藏滚动条） */
.sub-tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 1.25rem;
  padding: 4px;
  background: var(--app-bg-secondary, #f3f4f6);
  border: 1px solid var(--app-border-color, rgba(0, 0, 0, 0.06));
  border-radius: 12px;
  overflow-x: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
}
.sub-tabs::-webkit-scrollbar {
  display: none;
}
.sub-tab-btn {
  flex-shrink: 0;
  height: 34px;
  padding: 0 14px;
  border: none;
  border-radius: 9px;
  background: transparent;
  /* 普通态用主文字色：透明底直接落在近透明的 component 底色上，
     次级灰字在彩色/深色背景下对比度不足 4.5（contrast-audit 子标签普通硬门禁） */
  color: var(--app-text-primary, #1f2937);
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.18s ease, color 0.18s ease, box-shadow 0.18s ease;
  white-space: nowrap;
  font-size: 0.85rem;
}
.sub-tab-btn:hover {
  background: color-mix(in srgb, var(--app-active-bg, #3b82f6) 10%, transparent);
  color: var(--app-text-primary, #1f2937);
}
.sub-tab-btn.active {
  background: var(--app-active-bg, #3b82f6);
  color: var(--app-active-text, #ffffff);
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.18);
}

/* 表单容器 */
.config-form {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

/* 表单区域 */
.form-section {
  padding: 20px;
  background: var(--app-component-bg, rgba(255, 255, 255, 0.6));
  border-radius: 14px;
  border: 1px solid var(--app-border-color, #e5e7eb);
  margin-bottom: 1.25rem;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}
.form-section:last-child {
  margin-bottom: 0;
}

/* 区块标题：左侧主色竖条标识层级 */
.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 1.1rem 0;
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--app-text-primary, #374151);
}
.section-title::before {
  content: '';
  flex-shrink: 0;
  width: 3px;
  height: 14px;
  border-radius: 2px;
  background: var(--app-active-bg, #3b82f6);
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
  align-items: center;
  margin-bottom: 0.6rem;
  font-weight: 600;
  color: var(--app-text-primary, #374151);
  font-size: 0.9rem;
}

/* 表单项网格：宽屏多列、窄屏自动落一列；网格内去掉每项的分隔线，靠间距分区 */
.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 20px 24px;
}
.form-grid .form-group {
  min-width: 0;
  margin-bottom: 0;
  padding-bottom: 0;
  border-bottom: none;
}

/* 开关设置项网格（聊天行为）：两列卡片平铺 */
.settings-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 12px;
}
.settings-grid .form-group {
  margin-bottom: 0;
  padding-bottom: 0;
  border-bottom: none;
}
.settings-grid .setting-item {
  height: 100%;
  box-sizing: border-box;
  margin-bottom: 0;
}

/* 行内开关行（如图表样式的手绘开关） */
.inline-switch-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 34px;
}
.switch-hint {
  font-size: 0.8rem;
  color: var(--app-text-secondary, #6b7280);
  line-height: 1.5;
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
  border: 1px solid var(--app-border-color, rgba(0, 0, 0, 0.05));
  border-radius: 12px;
}
.background-presets {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}
.background-preset {
  width: 56px;
  height: 36px;
  border-radius: 8px;
  cursor: pointer;
  border: 2px solid transparent;
  /* 内描边让纯白/浅色预设在浅底上可辨 */
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.08);
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
  position: relative;
  overflow: hidden;
  flex-shrink: 0;
}
.background-preset:hover {
  transform: scale(1.05);
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.08), 0 4px 12px rgba(0, 0, 0, 0.15);
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
  border: 2px solid var(--app-border-color, rgba(0, 0, 0, 0.12));
  border-radius: 10px;
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
  font-size: 0.82rem;
  color: var(--app-text-secondary, #6b7280);
  margin-bottom: 0.6rem;
  font-weight: 600;
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
  font-size: 0.85rem;
  font-variant-numeric: tabular-nums;
  padding: 0.25rem 0.5rem;
  background: var(--app-bg-secondary, #f3f4f6);
  border: 1px solid var(--app-border-color, #e5e7eb);
  border-radius: 6px;
}

/* 字体预览 */
.font-preview {
  margin-top: 0.5rem;
  padding: 0.75rem;
  background: var(--app-component-bg, rgba(255, 255, 255, 0.95));
  border-radius: 8px;
  border: 1px solid var(--app-border-color, rgba(0, 0, 0, 0.1));
  color: var(--app-text-primary, #374151);
}

/* 设置项样式 */
.setting-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.9rem 1rem;
  background: var(--app-bg-secondary, #f9fafb);
  border: 1px solid var(--app-border-color, rgba(0, 0, 0, 0.05));
  border-radius: 12px;
  margin-bottom: 0.75rem;
  transition: background-color 0.18s ease;
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

/* ===== 图表样式（Mermaid） ===== */
.mermaid-hint {
  margin: 8px 0 0;
  font-size: 0.8rem;
  color: var(--app-text-secondary, #6b7280);
}
.mermaid-preview-box {
  padding: 16px;
  background: var(--app-bg-secondary, #f9fafb);
  border: 1px solid var(--app-border-color, #e5e7eb);
  border-radius: 12px;
  min-height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.mermaid-preview-render {
  width: 100%;
  overflow-x: auto;
  text-align: center;
}
.mermaid-preview-render :deep(svg) {
  max-width: 100%;
  height: auto;
}
.mermaid-preview-error {
  width: 100%;
  color: var(--app-text-secondary, #6b7280);
  font-size: 0.85rem;
}
.mermaid-preview-error pre {
  margin: 8px 0 0;
  padding: 8px;
  background: var(--app-component-bg, rgba(255, 255, 255, 0.6));
  border-radius: 6px;
  color: var(--app-text-primary, #374151);
  white-space: pre-wrap;
}

/* ===== 消息样式预览 ===== */
.message-preview {
  display: flex;
  flex-direction: column;
  padding: 14px;
  background: var(--app-bg-secondary, #f9fafb);
  border: 1px solid var(--app-border-color, rgba(0, 0, 0, 0.05));
  border-radius: 12px;
}
.mp-bubble {
  max-width: 70%;
  border-radius: var(--chat-radius, 12px);
  font-size: 0.88rem;
  line-height: 1.6;
  word-break: break-word;
}
.mp-ai {
  align-self: flex-start;
  background: var(--chat-ai-bubble-bg, #ffffff);
  border: 1px solid var(--chat-ai-bubble-border, var(--app-border-color, #e5e7eb));
  color: var(--app-text-primary, #1f2937);
}
.mp-user {
  align-self: flex-end;
  background: var(--chat-user-bubble-bg, #3b82f6);
  color: var(--chat-user-bubble-text, #ffffff);
}

/* ===== 代码样式预览 ===== */
.code-preview-box {
  padding: 12px;
  background: var(--app-bg-secondary, #f9fafb);
  border: 1px solid var(--app-border-color, rgba(0, 0, 0, 0.05));
  border-radius: 12px;
  min-height: 120px;
  max-height: 360px;
  overflow: auto;
}
.code-preview-render :deep(.code-preview-pre) {
  margin: 0;
  padding: 14px 16px;
  border-radius: 8px;
  background: var(--code-block-background, #1e293b);
  overflow-x: auto;
}
.code-preview-render :deep(code) {
  font-family: var(--code-font-family, Consolas, Monaco, monospace);
  font-size: var(--code-font-size, 14px);
  line-height: 1.6;
  color: var(--chat-code-text, #e2e8f0);
  white-space: pre;
}
.code-preview-render :deep(code.hljs) {
  background: transparent;
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

/* ===== 头像设置 ===== */
.avatar-hint {
  margin: -8px 0 16px;
  font-size: 0.82rem;
  color: var(--app-text-secondary, #6b7280);
}

.avatar-card {
  padding: 16px;
  margin-bottom: 18px;
  background: var(--app-bg-secondary, #f9fafb);
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
/* 透明棋盘格：中性灰 alpha 格子叠加在组件底色上，明/暗主题下均可见，不写死浅色 */
.avatar-preview-box {
  width: 200px;
  height: 200px;
  border-radius: 18px;
  border: 1px solid var(--app-border-color, #e5e7eb);
  background:
    repeating-conic-gradient(rgba(127, 127, 127, 0.16) 0% 25%, transparent 0% 50%) 0 0 / 20px 20px,
    var(--app-component-bg, #ffffff);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.avatar-preview-box img {
  width: 88%;
  height: 88%;
  object-fit: contain;
}
/* 链接 + 复制按钮单行排列，长链接省略号截断 */
.avatar-preview-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
}
.avatar-url {
  flex: 1;
  min-width: 0;
  font-size: 0.7rem;
  color: var(--app-text-secondary, #6b7280);
  background: var(--app-bg-secondary, #f3f4f6);
  padding: 4px 8px;
  border-radius: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 控制区：两列网格，种子/快捷挑选通栏 */
.avatar-controls {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 16px;
  align-content: start;
}
.avatar-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}
.avatar-field-full {
  grid-column: 1 / -1;
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
.avatar-bg-row {
  min-height: 34px;
}
.avatar-seed-row :deep(.n-input) {
  flex: 1;
}

/* 快捷挑选画廊：按宽度自动排布，宽屏一排放下 */
.avatar-gallery {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(52px, 1fr));
  gap: 8px;
}
.gallery-item {
  aspect-ratio: 1 / 1;
  padding: 6px;
  border-radius: 12px;
  border: 2px solid transparent;
  background: var(--app-bg-secondary, #f3f4f6);
  cursor: pointer;
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease, background-color 0.18s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}
.gallery-item:hover {
  transform: translateY(-2px);
  border-color: var(--app-active-bg, #3b82f6);
}
/* 激活底色用面板色混合而非写死白色，深色主题下不再出现亮块 */
.gallery-item.active {
  border-color: var(--app-active-bg, #3b82f6);
  background: color-mix(in srgb, var(--app-active-bg, #3b82f6) 12%, var(--app-component-bg, #ffffff) 88%);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--app-active-bg, #3b82f6) 20%, transparent);
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
  .avatar-preview {
    align-items: center;
  }
  .avatar-preview-box {
    width: 160px;
    height: 160px;
  }
  .avatar-controls {
    grid-template-columns: 1fr;
  }
}
</style>
