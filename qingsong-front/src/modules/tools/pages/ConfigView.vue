<template>
  <div class="config-container">
    <header class="page-header">
      <div class="page-header-main">
        <h1 class="page-title">配置管理中心</h1>
        <div class="page-header-actions">
          <p class="page-description">统一管理系统外观、模型来源、模型配置与角色设置</p>
          <button class="home-btn" type="button" @click="goHome" title="返回首页">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
              stroke-linecap="round" stroke-linejoin="round">
              <path d="M3 12l9-9 9 9" />
              <path d="M5 10v10a1 1 0 0 0 1 1h4v-6h4v6h4a1 1 0 0 0 1-1V10" />
            </svg>
            返回首页
          </button>
        </div>
      </div>
    </header>
    <!-- 配置内容区域 -->
    <div class="config-content">
      <!-- 侧边栏 -->
      <div class="config-sidebar">
        <div class="sidebar-menu">
          <div
            class="menu-item"
            :class="{ active: isActive('system') }"
            @click="handleTabClick('system')"
          >
            <svg
              width="20"
              height="20"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <circle cx="12" cy="12" r="3" />
              <path d="M12 1v6m0 6v6" />
              <path d="m21 12-6-3-6 3-6-3" />
            </svg>
            系统设置
          </div>
          <div
            class="menu-item"
            :class="{ active: isActive('source') }"
            @click="handleTabClick('source')"
          >
            <svg
              width="20"
              height="20"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path
                d="M21 12a9 9 0 01-9 9m9-9a9 9 0 00-9-9m9 9H3m9 9a9 9 0 01-9-9m9 9c1.657 0 3-4.03 3-9s-1.343-9-3-9m0 18c-1.657 0-3-4.03-3-9s1.343-9 3-9m-9 9a9 9 0 019-9"
              />
            </svg>
            模型来源
          </div>
          <div
            class="menu-item"
            :class="{ active: isActive('model') }"
            @click="handleTabClick('model')"
          >
            <svg
              width="20"
              height="20"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path
                d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"
              />
              <polyline points="3.27,6.96 12,12.01 20.73,6.96" />
              <line x1="12" y1="22.08" x2="12" y2="12" />
            </svg>
            模型管理
          </div>
          <div
            class="menu-item"
            :class="{ active: isActive('role') }"
            @click="handleTabClick('role')"
          >
            <svg
              width="20"
              height="20"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
              <circle cx="12" cy="7" r="4" />
            </svg>
            角色管理
          </div>
          <div
            class="menu-item"
            :class="{ active: isActive('user') }"
            @click="handleTabClick('user')"
          >
            <svg
              width="20"
              height="20"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
              <circle cx="12" cy="7" r="4" />
            </svg>
            用户配置
          </div>
          <div
            class="menu-item"
            :class="{ active: isActive('dict') }"
            @click="handleTabClick('dict')"
          >
            <svg
              width="20"
              height="20"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M4 6h16" />
              <path d="M4 12h16" />
              <path d="M4 18h10" />
            </svg>
            字典管理
          </div>
        </div>
      </div>

      <!-- 主要内容区域：渲染子路由 -->
      <div class="config-main">
        <div class="config-panel">
          <router-view />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();

// 当前激活的标签页：由路由驱动（/config/{system,source,model,role,user}）
const activeTab = computed(() => {
  const segment = route.path.split("/").filter(Boolean)[1];
  return segment || "system";
});

const isActive = (tab) => activeTab.value === tab;

// 返回首页
const goHome = () => {
  router.push("/");
};

// 点击菜单项切换到对应子路由
// 用 replace 切换，避免每个标签页都压入历史，导致浏览器后退在配置页内来回转
const handleTabClick = (tab) => {
  router.replace(`/config/${tab}`);
};
</script>

<style scoped>
.config-container {
  min-height: 100vh;
  padding: 0;
  background: var(--app-background); /* 使用 themeStore 定义的背景 */
  overflow-y: auto;
  scrollbar-gutter: stable both-edges;
  background-size: cover;
  background-position: center;
  background-attachment: scroll;
}

.page-title {
  margin: 0 0 2rem 0;
  font-size: 2rem;
  font-weight: 700;
  color: var(--app-text-primary, #1f2937);
}

.config-content {
  display: flex;
  gap: 1.5rem;
  align-items: flex-start;
  max-width: 1440px;
  margin: 0 auto;
}

.config-sidebar {
  width: 280px;
  background: var(--app-component-bg, rgba(255, 255, 255, 0.92));
  border-radius: 12px;
  padding: 1.25rem;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04), 0 2px 8px rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(229, 231, 235, 0.6);
  position: sticky;
  top: 0;
  backdrop-filter: blur(6px);
}

.sidebar-menu {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 0;
}

.menu-item:focus-visible,
.sub-tab-btn:focus-visible,
.table-btn:focus-visible,
.btn-primary:focus-visible,
.btn-add-model:focus-visible,
.retry-btn:focus-visible {
  outline: 2px solid var(--app-active-bg, #3b82f6);
  outline-offset: 2px;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.9rem 1.2rem;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  color: var(--app-text-secondary, #6b7280);
  font-weight: 500;
}

.menu-item:hover {
  background: var(--app-hover-bg, rgba(59, 130, 246, 0.1));
  color: var(--app-active-text-hover, #3b82f6);
}

.menu-item.active {
  background: var(--app-active-bg, #3b82f6);
  color: var(--app-active-text, white);
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.25);
}

.config-main {
  flex: 1;
  padding: 0;
}

.config-panel {
  background: var(--app-component-bg, rgba(255, 255, 255, 0.92));
  border-radius: 12px;
  padding: 1.75rem;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04), 0 2px 8px rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(229, 231, 235, 0.6);
  backdrop-filter: blur(6px);
}

.panel-header {
  margin-bottom: 1.5rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid var(--app-border-color, rgba(0, 0, 0, 0.1));
}

.panel-header h2 {
  margin: 0 0 0.5rem 0;
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--app-text-primary, #1f2937);
}

.panel-header p {
  margin: 0;
  color: var(--app-text-secondary, #6b7280);
  font-size: 0.9rem;
}

.config-form {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

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

.config-container {
  position: relative;
  isolation: isolate;
  background: radial-gradient(
      circle at 12% 16%,
      rgba(255, 181, 120, 0.9) 0,
      rgba(255, 181, 120, 0.36) 12%,
      transparent 26%
    ),
    radial-gradient(
      circle at 84% 12%,
      rgba(125, 211, 252, 0.88) 0,
      rgba(125, 211, 252, 0.34) 12%,
      transparent 28%
    ),
    radial-gradient(
      circle at 82% 78%,
      rgba(244, 114, 182, 0.82) 0,
      rgba(244, 114, 182, 0.26) 14%,
      transparent 30%
    ),
    radial-gradient(
      circle at 18% 84%,
      rgba(134, 239, 172, 0.78) 0,
      rgba(134, 239, 172, 0.24) 13%,
      transparent 28%
    ),
    linear-gradient(180deg, #fff8ef 0%, #fffef8 36%, #f1fff7 68%, #eef7ff 100%);
}

.config-container::before,
.config-container::after {
  content: "";
  position: fixed;
  pointer-events: none;
  z-index: -1;
}

.config-container::before {
  top: 72px;
  right: clamp(18px, 7vw, 96px);
  width: clamp(180px, 24vw, 320px);
  height: clamp(180px, 24vw, 320px);
  border-radius: 44% 56% 58% 42%;
  background: radial-gradient(
      circle at 30% 30%,
      rgba(255, 255, 255, 0.72),
      transparent 34%
    ),
    linear-gradient(
      145deg,
      rgba(251, 191, 36, 0.56),
      rgba(249, 115, 22, 0.3) 42%,
      rgba(244, 114, 182, 0.44)
    );
  box-shadow: 0 32px 60px rgba(251, 146, 60, 0.18);
  transform: rotate(-14deg);
}

.config-container::after {
  left: clamp(10px, 5vw, 72px);
  bottom: clamp(32px, 6vh, 80px);
  width: clamp(220px, 28vw, 360px);
  height: clamp(130px, 18vw, 220px);
  border-radius: 999px;
  background: radial-gradient(
      circle at 18% 35%,
      rgba(255, 255, 255, 0.54),
      transparent 22%
    ),
    linear-gradient(
      120deg,
      rgba(96, 165, 250, 0.34),
      rgba(52, 211, 153, 0.28) 52%,
      rgba(250, 204, 21, 0.3)
    );
  box-shadow: 0 24px 48px rgba(14, 165, 233, 0.14);
  transform: rotate(12deg);
}

.page-title {
  font-weight: 800;
  letter-spacing: -0.04em;
  color: #2f2346;
  text-shadow: 0 2px 0 rgba(255, 255, 255, 0.38);
}

.config-content {
  position: relative;
  z-index: 1;
}

.config-sidebar {
  background: rgba(255, 251, 245, 0.86);
  border-radius: 28px;
  box-shadow: 0 20px 48px rgba(191, 129, 74, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.72);
  top: 1rem;
  backdrop-filter: blur(10px);
}

.menu-item {
  border-radius: 18px;
  transition: transform 0.18s ease, background-color 0.18s ease, color 0.18s ease,
    box-shadow 0.18s ease;
  color: #7c5f4a;
  font-weight: 600;
}

.menu-item:hover {
  background: rgba(255, 237, 213, 0.88);
  color: #b45309;
  transform: translateY(-1px);
}

.menu-item.active {
  background: linear-gradient(135deg, #ffb86c 0%, #ff8fab 52%, #7dd3fc 100%);
  color: #ffffff;
  box-shadow: 0 16px 28px rgba(244, 114, 182, 0.24);
}

.config-panel {
  background: linear-gradient(
    180deg,
    rgba(255, 252, 248, 0.94) 0%,
    rgba(255, 255, 255, 0.92) 100%
  );
  border-radius: 32px;
  box-shadow: 0 24px 56px rgba(120, 87, 61, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(12px);
}

.panel-header {
  border-bottom: 1px solid rgba(245, 158, 11, 0.16);
}

.panel-header h2 {
  font-weight: 700;
  color: #3f2f58;
}

.panel-header p {
  color: #8b6f61;
}

.form-section {
  background: rgba(255, 255, 255, 0.72);
  border-radius: 24px;
  border: 1px solid rgba(255, 230, 214, 0.95);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.72);
}

.section-title {
  font-weight: 700;
  color: #4b3d66;
  border-bottom: 1px solid rgba(251, 191, 36, 0.18);
}

.background-selector {
  background: rgba(255, 247, 237, 0.68);
  border: 1px solid rgba(255, 230, 214, 0.85);
  border-radius: 18px;
}

.background-preset {
  border-radius: 999px;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.8),
    0 8px 18px rgba(160, 107, 63, 0.12);
}

.background-preset:hover {
  transform: translateY(-2px) scale(1.03);
}

.background-preset.active {
  box-shadow: 0 0 0 3px rgba(255, 255, 255, 0.92), 0 0 0 6px rgba(244, 114, 182, 0.28),
    0 12px 24px rgba(244, 114, 182, 0.2);
}

.custom-background {
  background: rgba(255, 255, 255, 0.74);
  border-radius: 16px;
  border: 1px solid rgba(255, 230, 214, 0.9);
}

.sub-tab-btn {
  border-radius: 999px;
  border: 1px solid rgba(255, 223, 201, 0.92);
  background: rgba(255, 255, 255, 0.72);
  color: #8a5d46;
  font-weight: 600;
}

.sub-tab-btn:hover {
  background: rgba(255, 241, 226, 0.96);
  color: #b45309;
}

.sub-tab-btn.active {
  background: linear-gradient(135deg, rgba(255, 184, 108, 0.95), rgba(251, 146, 60, 0.9));
  color: #ffffff;
  box-shadow: 0 12px 24px rgba(249, 115, 22, 0.18);
}

.btn-primary,
.btn-add-model {
  background: linear-gradient(135deg, #ffb86c 0%, #fb7185 100%);
  box-shadow: 0 16px 24px rgba(244, 114, 182, 0.22);
}

.btn-primary:hover:not(:disabled),
.btn-add-model:hover {
  background: linear-gradient(135deg, #ffae52 0%, #f43f5e 100%);
}

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

.form-group label {
  display: block;
  margin-bottom: 0.75rem;
  font-weight: 600;
  color: var(--app-text-primary, #374151);
  font-size: 0.95rem;
}

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
  content: "✓";
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: var(--app-active-text, white);
  font-weight: bold;
  font-size: 16px;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.5);
}

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

.color-picker {
  width: 60px;
  height: 40px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
}

.form-input {
  width: 100%;
  padding: 0.75rem 1rem;
  border: 1px solid var(--app-border-color, #d1d5db);
  border-radius: 8px;
  font-size: 0.9rem;
  transition: all 0.2s ease;
}

.form-input:focus {
  outline: none;
  border-color: var(--app-active-bg, #3b82f6);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.form-select {
  width: 100%;
  padding: 0.75rem 1rem;
  border: 1px solid var(--app-border-color, #d1d5db);
  border-radius: 8px;
  font-size: 0.9rem;
  background: var(--app-component-bg, white);
  cursor: pointer;
  transition: all 0.2s ease;
}

.form-select:focus {
  outline: none;
  border-color: var(--app-active-bg, #3b82f6);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.slider-group {
  display: flex;
  align-items: center;
  gap: 1rem;
}

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

.form-slider {
  flex: 1;
  height: 6px;
  border-radius: 3px;
  background: var(--app-component-bg, #e5e7eb);
  outline: none;
  cursor: pointer;
}

.form-slider::-webkit-slider-thumb {
  appearance: none;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--app-active-bg, #3b82f6);
  cursor: pointer;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

.font-size-value {
  min-width: 50px;
  text-align: center;
  font-weight: 500;
  color: var(--app-text-primary, #374151);
}

.font-preview {
  margin-top: 0.5rem;
  padding: 0.75rem;
  background: var(--app-component-bg, rgba(255, 255, 255, 0.95));
  border-radius: 6px;
  border: 1px solid var(--app-border-color, rgba(0, 0, 0, 0.1));
  color: var(--app-text-primary, #374151);
}

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

.radio-group {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
}

.radio-label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  cursor: pointer;
  font-size: 0.9rem;
  color: var(--app-text-primary, #374151);
}

.radio-label input[type="radio"] {
  margin: 0;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  cursor: pointer;
  font-size: 0.9rem;
  color: var(--app-text-primary, #374151);
  padding: 0.5rem 0;
}

.checkbox-label input[type="checkbox"] {
  margin: 0;
  width: 18px;
  height: 18px;
}

.slider-value {
  min-width: 50px;
  text-align: center;
  font-weight: 500;
  color: var(--app-text-primary, #374151);
}

.model-table-container {
  position: relative;
  height: 400px;
  overflow-x: auto;
  scrollbar-gutter: stable both-edges;
  border-radius: 10px;
  border: 1px solid var(--app-border-color, #e5e7eb);
  background: var(--app-component-bg, rgba(255, 255, 255, 0.8));
}

.model-table tbody tr:nth-child(even) {
  background: rgba(249, 250, 251, 0.5);
}

.model-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.875rem;
}

.model-table thead {
  background: var(--app-bg-secondary, #f9fafb);
  position: sticky;
  top: 0;
  z-index: 10;
}

.model-table th {
  padding: 0.875rem 1rem;
  text-align: left;
  font-weight: 600;
  color: var(--app-text-primary, #1f2937);
  font-size: 0.8rem;
  border-bottom: 2px solid var(--app-border-color, #e5e7eb);
  white-space: nowrap;
}

.model-table tbody tr {
  border-bottom: 1px solid var(--app-border-color, #e5e7eb);
  transition: background-color 0.15s ease;
}

.model-table tbody tr:hover {
  background: var(--app-hover-bg, rgba(59, 130, 246, 0.05));
}

.model-table tbody tr:last-child {
  border-bottom: none;
}

.model-table td {
  padding: 0.75rem 1rem;
  color: var(--app-text-primary, #374151);
  vertical-align: middle;
}

.model-name-cell {
  min-width: 140px;
}

.model-name {
  font-weight: 600;
  color: var(--app-text-primary, #1f2937);
}

.model-code-cell {
  min-width: 160px;
}

.model-code {
  font-family: "Courier New", monospace;
  font-size: 0.8rem;
  padding: 0.2rem 0.5rem;
  background: var(--app-bg-secondary, #f3f4f6);
  color: var(--app-text-secondary, #6b7280);
  border-radius: 4px;
  font-weight: 500;
  display: inline-block;
}

.url-cell {
  max-width: 220px;
}

.url-cell,
.model-code-cell,
.model-name-cell {
  word-break: break-all;
}

.url-link {
  font-size: 0.8rem;
  color: var(--app-active-bg, #3b82f6);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: block;
  text-decoration: none;
  cursor: pointer;
  transition: color 0.2s ease;
}

.url-link:hover {
  color: #2563eb;
  text-decoration: underline;
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

.tag-source {
  background: rgba(139, 92, 246, 0.1);
  color: #8b5cf6;
}

.table-placeholder {
  color: var(--app-text-secondary, #9ca3af);
}

.favor-count {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--app-text-secondary, #6b7280);
  font-size: 0.85rem;
}

.favor-count::before {
  content: "★";
  color: #fbbf24;
}

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

.date-cell {
  font-size: 0.8rem;
  color: var(--app-text-secondary, #6b7280);
  min-width: 140px;
}

.actions-cell {
  white-space: nowrap;
  min-width: 80px;
}

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

.table-btn:not(:last-child) {
  margin-right: 0.5rem;
}

@media (max-width: 1024px) {
  .config-content {
    gap: 1.5rem;
  }

  .config-sidebar {
    width: 240px;
  }
}

@media (max-width: 768px) {
  .config-container {
    padding: 1.25rem;
  }

  .config-container::before {
    top: 96px;
    right: -28px;
    width: 180px;
    height: 180px;
    opacity: 0.72;
  }

  .config-container::after {
    left: -32px;
    bottom: 32px;
    width: 200px;
    height: 120px;
    opacity: 0.72;
  }

  .page-title {
    font-size: 1.4rem;
  }

  .page-header {
    margin-bottom: 1rem;
    padding: 0;
  }

  .page-header-main {
    margin-top: 0.75rem;
    flex-direction: column;
    align-items: flex-start;
    gap: 0.45rem;
  }

  .page-description {
    max-width: none;
    font-size: 0.85rem;
    text-align: left;
  }

  .config-content {
    flex-direction: column;
  }

  .config-sidebar {
    width: 100%;
    position: static;
  }

  .sidebar-menu {
    flex-direction: row;
    overflow-x: auto;
    padding-bottom: 0.25rem;
  }

  .menu-item {
    white-space: nowrap;
    margin-bottom: 0;
    padding: 0.75rem 1rem;
  }

  .config-panel {
    padding: 1.25rem;
  }

  .panel-header {
    margin-bottom: 1.5rem;
  }

  .header-content {
    flex-direction: column;
    align-items: flex-start;
  }

  .form-section {
    padding: 1.25rem;
  }

  .source-selector {
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  }
}
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

.btn-upload {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1.5rem;
  border: 1px solid var(--app-border-color, #d1d5db);
  border-radius: 8px;
  background: var(--app-component-bg, white);
  color: var(--app-text-primary, #374151);
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-upload:hover {
  border-color: var(--app-active-bg, #3b82f6);
  color: var(--app-active-bg, #3b82f6);
}

.image-preview-wrapper {
  position: relative;
}

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

.list-progress {
  position: sticky;
  top: 0;
  left: 0;
  height: 3px;
  background: rgba(59, 130, 246, 0.1);
  z-index: 2;
  pointer-events: none;
}

.list-progress span {
  display: block;
  height: 100%;
  width: 0;
  background: var(--app-active-bg, #3b82f6);
  transition: width 0.15s ease;
}

/* 加载状态 */
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
}

.retry-btn:hover {
  background: var(--app-active-bg, #3b82f6);
  color: white;
}

.header-actions {
  display: inline-flex;
  align-items: center;
  gap: 0.75rem;
}

.btn-secondary {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.55rem 1rem;
  border: 1px solid var(--app-border-color, #cbd5f5);
  border-radius: 8px;
  font-size: 0.85rem;
  font-weight: 500;
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

/* 弹窗样式 - 已替换为 naiveUI 原生组件 */
.header-content {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
}

.header-content h2 {
  margin: 0 0 0.5rem 0;
}

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
  background: var(--app-active-bg, #3b82f6);
  color: white;
  box-shadow: 0 2px 4px rgba(59, 130, 246, 0.2);
}

.btn-primary:hover:not(:disabled) {
  background: #2563eb;
  box-shadow: 0 3px 6px rgba(59, 130, 246, 0.25);
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 按来源分组的模型列表样式 */
.model-by-source-container {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

/* 来源选择器 - 横向卡片 */
.source-selector {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
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
  font-family: "Courier New", monospace;
  font-size: 0.7rem;
  color: var(--app-text-secondary, #6b7280);
}

.source-model-count {
  font-size: 0.7rem;
  color: var(--app-active-bg, #3b82f6);
}

/* 选中来源的模型列表面板 */
.source-models-panel {
  background: var(--app-component-bg, rgba(255, 255, 255, 0.8));
  border: 1px solid var(--app-border-color, #e5e7eb);
  border-radius: 10px;
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
  background: transparent;
  color: var(--app-active-bg, #3b82f6);
  border-radius: 6px;
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
  border: 1px dashed var(--app-border-color, #e5e7eb);
}

/* Config Shell Refresh */
.config-container {
  --config-panel-bg: var(--app-panel-background, rgba(255, 255, 255, 0.94));
  --config-panel-muted: var(--app-component-bg, rgba(248, 250, 252, 0.5));
  --config-line: var(--app-border-color, rgba(0, 0, 0, 0.08));
  --config-ink: var(--app-text-primary, #374151);
  --config-muted: var(--app-text-secondary, #6b7280);
  --config-accent: var(--app-active-bg, #3b82f6);
  --config-accent-text: var(--app-active-text, #ffffff);
  background: radial-gradient(
      circle at 100% 0%,
      rgba(var(--app-panel-background-rgb, 255, 255, 255), 0.88),
      transparent 24%
    ),
    radial-gradient(
      circle at 0% 18%,
      rgba(var(--app-panel-background-rgb, 255, 255, 255), 0.56),
      transparent 22%
    ),
    var(--app-background);
}

.page-header {
  max-width: 1480px;
  margin: 0 auto 20px;
  padding: 4px 4px 0;
}

.page-header-main {
  margin-top: 10px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  padding: 0 2px;
}

.page-header-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.home-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: 1px solid var(--config-line, #e5e7eb);
  border-radius: 999px;
  background: var(--config-panel-bg, rgba(255, 255, 255, 0.92));
  color: var(--config-muted, #6b7280);
  font-size: 0.85rem;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    color: var(--config-accent, #3b82f6);
    border-color: var(--config-accent, #3b82f6);
    background: color-mix(
      in srgb,
      var(--config-accent, #3b82f6) 8%,
      var(--config-panel-bg, #ffffff) 92%
    );
  }
}

.page-title {
  margin: 0;
  font-size: clamp(24px, 2.8vw, 32px);
  font-weight: 750;
  line-height: 1.1;
  letter-spacing: -0.03em;
  color: var(--config-ink);
  text-shadow: none;
}

.page-description {
  margin: 0;
  max-width: 520px;
  font-size: 0.95rem;
  line-height: 1.6;
  color: var(--config-muted);
  text-align: right;
}

.config-content {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.config-sidebar {
  width: auto;
  padding: 16px;
  border: 1px solid var(--config-line);
  border-radius: 22px;
  background: var(--config-panel-bg);
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.07);
  backdrop-filter: blur(0);
  position: sticky;
  top: 18px;
}

.menu-item {
  min-height: 48px;
  padding: 0 14px;
  border: 1px solid transparent;
  border-radius: 14px;
  color: var(--config-muted);
  font-weight: 600;
  transform: none;
}

.menu-item:hover {
  background: var(--config-panel-muted);
  color: var(--config-ink);
}

.menu-item.active {
  background: color-mix(in srgb, var(--config-accent) 14%, var(--config-panel-bg) 86%);
  color: var(--config-ink);
  border-color: color-mix(in srgb, var(--config-line) 60%, var(--config-accent) 40%);
  box-shadow: none;
}

.config-main {
  min-width: 0;
}

.config-panel {
  padding: 18px;
  border: 1px solid var(--config-line);
  border-radius: 22px;
  background: var(--config-panel-bg);
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.07);
  backdrop-filter: none;
}

.panel-header {
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--config-line);
}

.panel-header h2 {
  color: var(--config-ink);
  font-size: 24px;
  letter-spacing: -0.02em;
}

.panel-header p {
  color: var(--config-muted);
}

.sub-tabs {
  gap: 8px;
  margin-bottom: 14px;
  padding: 0;
  background: transparent;
}

.sub-tab-btn {
  min-height: 36px;
  padding: 0 14px;
  border: 1px solid var(--config-line);
  border-radius: 999px;
  background: var(--config-panel-muted);
  color: var(--config-muted);
  font-weight: 600;
}

.sub-tab-btn:hover {
  background: color-mix(
    in srgb,
    var(--config-panel-muted) 76%,
    rgba(255, 255, 255, 0.62) 24%
  );
  color: var(--config-ink);
}

.sub-tab-btn.active {
  background: var(--config-accent);
  color: var(--config-accent-text);
  box-shadow: none;
}

.config-form {
  gap: 14px;
}

.form-section {
  margin-bottom: 0;
  padding: 16px;
  border: 1px solid var(--config-line);
  border-radius: 18px;
  background: var(--config-panel-muted);
  box-shadow: none;
}

.section-title {
  margin-bottom: 14px;
  padding-bottom: 10px;
  color: var(--config-ink);
  border-bottom: 1px solid var(--config-line);
}

.background-selector,
.custom-background,
.font-preview,
.no-source-selected {
  background: rgba(var(--app-panel-background-rgb, 255, 255, 255), 0.6);
  border-color: var(--config-line);
}

.current-theme-tag,
.slider-value-text {
  background: var(--config-panel-muted);
  color: var(--config-muted);
}

.theme-name {
  color: var(--config-ink);
}

.source-card,
.source-models-panel,
.model-table-container {
  border-radius: 18px;
}

@media (max-width: 1100px) {
  .config-content {
    grid-template-columns: 1fr;
  }

  .config-sidebar {
    position: static;
    top: auto;
  }
}

@media (max-width: 768px) {
  .config-container {
    padding: 12px 12px 20px;
  }

  .config-panel,
  .config-sidebar {
    padding: 14px;
    border-radius: 18px;
  }
}
</style>
