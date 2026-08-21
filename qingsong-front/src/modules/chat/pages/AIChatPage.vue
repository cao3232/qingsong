  <template>
  <div
    class="ai-chat-view"
    :class="{
      'sidebar-collapsed': sidebarCollapsed,
      'role-panel-expanded': !rolePanelCollapsed,
      'chat-skin-cloud-immortal': isCloudImmortal
    }"
  >
    <div v-if="isCloudImmortal" class="immortal-scene" aria-hidden="true">
      <div class="immortal-mist-far"></div>
      <div class="immortal-mist-near"></div>
      <div class="immortal-gate-watermark"></div>
      <div class="immortal-taiji-mark"></div>
      <div class="immortal-bagua"></div>
      <div class="immortal-cloud-seal"></div>
      <div class="immortal-rune"></div>
    </div>
    <div class="role-drawer-host">
      <RoleSidebar
        :roles="roles"
        :stats="roleStats"
        :selected-role="selectedRole"
        :selected-role-name="selectedRoleName"
        :initial-collapsed="rolePanelCollapsed"
        @start-new-chat="startNewChat"
        @roles-updated="handleRolesUpdated"
        @panel-toggle="handlePanelToggle"
      />
    </div>

    <!-- 主要聊天区域 -->
    <div class="main-chat-area">
      <!-- 移动端侧边栏遮罩 -->
      <div
        v-if="!sidebarCollapsed"
        class="sidebar-backdrop"
        @click="toggleSidebar"
      ></div>
      <!-- 可折叠的聊天历史侧边栏 -->
      <div class="sidebar-wrapper" :class="{ 'collapsed': sidebarCollapsed }">
        <button 
          class="sidebar-toggle" 
          @click="toggleSidebar"
          :title="sidebarCollapsed ? '展开聊天记录' : '收起聊天记录'"
        >
          <svg v-if="sidebarCollapsed" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M9 18l6-6-6-6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          <svg v-else viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M15 18l-6-6 6-6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>
        
        <ConversationSidebar
          v-show="!sidebarCollapsed"
          :selected-role="selectedRole"
          :selected-role-name="selectedRoleName"
          :chat-history="chatHistory"
          :current-chat-id="currentChatId"
          :current-messages="currentMessages"
          @load-chat="loadChat"
          @load-roles="loadRoles"
          @load-history="loadChatHistory(false)"
          @load-latest-chat="loadLatestChat"
          @jump-to-message="handleJumpToMessage"
          @create-new-chat="startNewChat"
          @rag-change="handleRagChange"
        />
      </div>
      
      <!-- 聊天主界面 -->
      <div class="chat-main-wrapper">
        <ChatWorkspace
          :current-messages="currentMessages"
          :is-streaming="isStreaming"
          :selected-role="selectedRole"
          :current-chat-id="currentChatId"
          :current-chat-name="currentChatName"
          :chat-history="chatHistory"
          :switch-conversation="switchConversation"
          :selected-role-name="selectedRoleName"
          :sidebar-collapsed="sidebarCollapsed"
          :rag-enabled="ragEnabled"
          :selected-knowledge-base="selectedKnowledgeBase"
          @send-message="sendMessage"
          @cancel-streaming="cancelStreaming"
          @update:currentMessages="updateCurrentMessages"
          @append-message="appendMessage"
          @clear-chat="handleClearChat"
          @update-chat-name="handleUpdateChatName"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, watch } from 'vue'
import { useThemeStore } from '../../../stores/theme.js'
import { RoleSidebar, ConversationSidebar, ChatWorkspace } from '../components/index.js'
import { useAIChatPage } from '../composables/index.js'
import '../../../shared/styles/common.scss'
import '../themes/cloudImmortal.scss'

const themeStore = useThemeStore()
const isCloudImmortal = computed(() => themeStore.config.chatSkin === 'cloud-immortal')
const overlayThemeClass = 'chat-skin-cloud-immortal-overlays'

watch(isCloudImmortal, enabled => {
  if (typeof document === 'undefined') return
  document.body.classList.toggle(overlayThemeClass, enabled)
  if (enabled) document.body.setAttribute('data-chat-skin', 'cloud-immortal')
  else if (document.body.getAttribute('data-chat-skin') === 'cloud-immortal') {
    document.body.removeAttribute('data-chat-skin')
  }
}, { immediate: true })

onBeforeUnmount(() => {
  if (typeof document === 'undefined') return
  document.body.classList.remove(overlayThemeClass)
  if (document.body.getAttribute('data-chat-skin') === 'cloud-immortal') {
    document.body.removeAttribute('data-chat-skin')
  }
})

const {
  appendMessage,
  cancelStreaming,
  chatHistory,
  currentChatId,
  currentChatName,
  currentMessages,
  handleClearChat,
  handleJumpToMessage,
  handlePanelToggle,
  handleRagChange,
  handleRolesUpdated,
  handleUpdateChatName,
  isStreaming,
  loadChat,
  loadChatHistory,
  loadLatestChat,
  loadRoles,
  ragEnabled,
  rolePanelCollapsed,
  roleStats,
  roles,
  selectedKnowledgeBase,
  selectedRole,
  selectedRoleName,
  sendMessage,
  sidebarCollapsed,
  startNewChat,
  switchConversation,
  toggleSidebar,
  updateCurrentMessages
} = useAIChatPage()
</script>

<style scoped>
/* ===== RETRO OS 90s DESKTOP STYLE ===== */

.ai-chat-view {
  display: flex;
  height: 100vh;
  height: 100dvh;
  /* Teal wallpaper - classic 90s desktop */
  background: var(--chat-wallpaper, #008080);
  background-image:
    repeating-linear-gradient(0deg, transparent, transparent 2px, var(--chat-wallpaper-grid, rgba(0,0,0,0.03)) 2px, var(--chat-wallpaper-grid, rgba(0,0,0,0.03)) 4px),
    repeating-linear-gradient(90deg, transparent, transparent 2px, var(--chat-wallpaper-grid, rgba(0,0,0,0.03)) 2px, var(--chat-wallpaper-grid, rgba(0,0,0,0.03)) 4px);
  font-family: var(--chat-font-family, 'MS Sans Serif', 'Segoe UI', Tahoma, sans-serif);
  font-size: 13px;
  color: var(--chat-text, #000000);
  overflow: hidden;
  position: relative;
  gap: 4px;
  padding: 4px;
  box-sizing: border-box;
  min-height: 0;
}

.role-drawer-host {
  width: 60px;
  min-width: 60px;
  height: 100%;
  position: relative;
  z-index: 24;
  box-sizing: border-box;
  padding: 2px;
  transition: width 0.15s ease, min-width 0.15s ease;
  /* Retro window panel style */
  background: var(--chat-panel, #c0c0c0);
  border: 2px solid;
  border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
  border-radius: var(--chat-radius, 0);
}

.ai-chat-view.role-panel-expanded {
  .role-drawer-host {
    width: 60px;
    min-width: 60px;
  }
}

/* Force pixel-perfect font rendering */
.ai-chat-view :deep(.chat-message),
.ai-chat-view :deep(.message-content),
.ai-chat-view :deep(.user-message),
.ai-chat-view :deep(.assistant-message),
.ai-chat-view :deep(.message-text),
.ai-chat-view :deep(.message-item) {
  font-family: var(--chat-font-family, 'MS Sans Serif', 'Segoe UI', Tahoma, sans-serif) !important;
  font-size: 13px !important;
}

/* Main area - grey container like desktop workspace */
.main-chat-area {
  flex: 1;
  display: flex;
  height: 100%;
  overflow: hidden;
  position: relative;
  gap: 4px;
  background: var(--chat-panel, #c0c0c0);
  border: 2px solid;
  border-color: var(--chat-bevel-frame-light, #dfdfdf) var(--chat-bevel-frame-dark, #404040) var(--chat-bevel-frame-dark, #404040) var(--chat-bevel-frame-light, #dfdfdf);
  border-radius: var(--chat-radius, 0);
  min-width: 0;
  min-height: 0;
  box-sizing: border-box;
  transition: all 0.15s ease;
  overflow: visible;
}

/* Sidebar wrapper - looks like a floating window */
.sidebar-wrapper {
  position: relative;
  width: 240px;
  min-width: 240px;
  height: 100%;
  min-height: 0;
  box-sizing: border-box;
  transition: all 0.15s ease;

  /* Retro 3D window effect */
  &.collapsed {
    width: 10px;
    min-width: 10px;

    .sidebar-toggle {
      left: 0;
      right: auto;
      border-left: 2px solid var(--chat-bevel-light, #ffffff);
      border-right: 2px solid var(--chat-bevel-shadow, #808080);
      border-top: 2px solid var(--chat-bevel-light, #ffffff);
      border-bottom: 2px solid var(--chat-bevel-shadow, #808080);
      border-radius: var(--chat-radius, 0);

      &:hover {
        left: 0;
        right: auto;
      }
    }
  }
}

/* Toggle button - retro button style */
.sidebar-toggle {
  position: absolute;
  top: 50%;
  right: -18px;
  transform: translateY(-50%);
  width: 16px;
  height: 48px;
  /* Raised 3D button surface */
  background: var(--chat-panel, #c0c0c0);
  border: 2px solid;
  border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
  border-left: none;
  border-radius: var(--chat-radius, 0);
  cursor: pointer;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--chat-text, #000000);
  box-shadow: var(--chat-shadow, none);
  transition: none;

  &:hover {
    background: var(--chat-panel-hover, #d4d4d4);
    border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
  }

  &:active {
    border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
    background: var(--chat-panel, #c0c0c0);
    padding: 1px 0 0 1px;
  }

  svg {
    width: 12px;
    height: 12px;
  }
}

/* Chat main wrapper */
.chat-main-wrapper {
  flex: 1;
  min-width: 0;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;

  .ai-chat-view.sidebar-collapsed & {
    margin-left: 0;
  }
}

/* Collapsed state */
.ai-chat-view.sidebar-collapsed {
  .main-chat-area {
    gap: 0;
  }

  .chat-main-wrapper {
    max-width: none;
  }
}

/* Deep chat main area overrides - make it a proper retro window */
.ai-chat-view :deep(.chat-main) {
  max-width: none !important;
  width: 100% !important;
  /* Remove modern rounding and shadows */
  border-radius: var(--chat-radius, 0) !important;
  box-shadow: var(--chat-shadow, none) !important;
  border: 2px solid;
  border-color: var(--chat-bevel-frame-light, #dfdfdf) var(--chat-bevel-frame-dark, #404040) var(--chat-bevel-frame-dark, #404040) var(--chat-bevel-frame-light, #dfdfdf);
  background: var(--chat-panel, #c0c0c0);
  padding: 0 !important;
  margin: 0 !important;

  /* Message list area is owned by ChatWorkspace (.messages) to avoid a
     conflicting "two skins" override; the input area keeps the retro skin. */
  .input-area {
    padding: 6px !important;
    border-top: 2px solid;
    border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
    background: var(--chat-panel, #c0c0c0);

    .input-container {
      max-width: none !important;
      width: 100% !important;
    }
  }
}

/* Collapsed sidebar deep overrides (input area only) */
.ai-chat-view.sidebar-collapsed :deep(.chat-main) {
  .input-area {
    padding: 6px 10px !important;
  }
}

@media (max-width: 1200px) {
  .ai-chat-view :deep(.chat-main) {
    .input-area {
      padding: 6px 16px !important;
    }
  }
}

@media (max-width: 1024px) {
  .ai-chat-view {
    gap: 3px;
    padding: 3px;
  }

  .role-drawer-host {
    width: 56px;
    min-width: 56px;
    padding: 2px;
  }

  .ai-chat-view.role-panel-expanded {
    .role-drawer-host {
      width: 56px;
      min-width: 56px;
    }
  }

  .main-chat-area {
    border-radius: var(--chat-radius, 0);
    gap: 3px;
  }

  .sidebar-wrapper {
    width: 220px;
    min-width: 220px;

    &.collapsed {
      width: 10px;
      min-width: 10px;
    }
  }
}

/* 侧边栏遮罩 - 仅移动端显示 */
.sidebar-backdrop {
  display: none;
}

@media (max-width: 768px) {
  .ai-chat-view {
    flex-direction: column;
    height: 100vh;
    height: 100dvh;
    gap: 0;
    padding: 0;
  }

  /* 角色栏 - 顶部紧凑横条 */
  .role-drawer-host {
    width: 100%;
    min-width: 100%;
    height: auto;
    display: block;
    padding: 0;
    /* 不设 z-index，避免创建堆叠上下文导致角色面板被侧边栏遮盖 */
    flex-shrink: 0;
    position: relative;
  }

  /* 主聊天区 - 占据所有剩余空间 */
  .main-chat-area {
    flex: 1;
    flex-direction: column;
    border-radius: var(--chat-radius, 0);
    gap: 0;
    min-height: 0;
    height: auto;
    /* visible 允许侧边栏浮层和 toggle 按钮溢出显示 */
    overflow: visible;
    position: relative;
  }

  /* 侧边栏遮罩 */
  .sidebar-backdrop {
    display: block;
    position: absolute;
    inset: 0;
    background: var(--chat-backdrop, rgba(0, 0, 0, 0.45));
    z-index: 55;
    opacity: 1;
    pointer-events: auto;
    transition: opacity 0.2s ease;
  }

  /* 侧边栏 - 浮层抽屉，不占布局空间 */
  .sidebar-wrapper {
    position: absolute;
    top: 0;
    left: 0;
    bottom: 0;
    width: 85%;
    max-width: 300px;
    min-width: unset;
    height: 100%;
    z-index: 60;
    background: var(--chat-panel, #c0c0c0);
    border-right: 2px solid var(--chat-bevel-shadow, #808080);
    box-shadow: 3px 0 12px rgba(0, 0, 0, 0.3);
    transition: width 0.2s ease;
    overflow: visible;

    &.collapsed {
      width: 0;
      border-right: none;
      box-shadow: var(--chat-shadow, none);

      .sidebar-toggle {
        position: absolute;
        top: 50%;
        left: 0;
        right: auto;
        transform: translateY(-50%);
        border: 2px solid;
        border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
        border-left: none;
        z-index: 61;
      }
    }
  }

  /* 侧边栏开关按钮 */
  .sidebar-toggle {
    position: absolute;
    top: 50%;
    right: -18px;
    transform: translateY(-50%);
    width: 18px;
    height: 48px;
    z-index: 61;

    svg {
      width: 12px;
      height: 12px;
    }
  }

  .chat-main-wrapper {
    flex: 1;
    height: auto;
    min-width: 0;
  }
}

@media (max-width: 480px) {
  .ai-chat-view {
    height: 100vh;
    height: 100dvh;
    gap: 0;
    padding: 0;
  }

  .main-chat-area {
    margin: 0;
    border-radius: var(--chat-radius, 0);
    box-shadow: var(--chat-shadow, none);
    height: auto;
    min-height: 0;
    gap: 0;
  }

  .sidebar-wrapper {
    max-width: 280px;
  }

  .sidebar-toggle {
    width: 16px;
    height: 40px;
  }
}

/* Jump highlight - retro dialog style */
:deep(.jump-highlight) {
  position: relative;
  background: var(--chat-favorite-on, #ffff00) !important;
  border: 2px dotted var(--chat-accent, #000080) !important;
  border-radius: var(--chat-radius, 0) !important;
  box-shadow:
    inset 1px 1px 0 var(--chat-inset-light, #ffffff),
    inset -1px -1px 0 var(--chat-inset-shadow, #808080),
    0 0 0 2px #000000;
  transform: none;
  transition: none;
  z-index: 10;
}

:deep(.jump-pulse) {
  animation: retroBlink 0.5s step-end infinite;
}

@keyframes retroBlink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

/* Retro scrollbar override */
.chat-container :deep(*::-webkit-scrollbar) {
  width: var(--chat-scrollbar-size, 17px);
  height: var(--chat-scrollbar-size, 17px);
}

.chat-container :deep(*::-webkit-scrollbar-track) {
  background: var(--chat-scrollbar-track, repeating-conic-gradient(#c0c0c0 0% 25%, #ffffff 0% 50%) 50% / 2px 2px);
  border: 1px solid var(--chat-scrollbar-border, #808080);
}

.chat-container :deep(*::-webkit-scrollbar-thumb) {
  background: var(--chat-scrollbar-thumb, #c0c0c0);
  border: 2px solid;
  border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
  border-radius: var(--chat-radius, 0);

  &:hover {
    background: var(--chat-panel-hover, #d4d4d4);
  }

  &:active {
    border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
  }
}
</style>
