<template>
  <div class="home-container">
    <div class="apps-grid">
      <div
        v-for="item in homeMenuItems"
        :key="item.key"
        class="app-card"
        @click="navigateToApp(item)"
      >
        <div
          class="app-icon"
          :style="{ background: item.icon.gradient, color: item.icon.color }"
        >
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" v-html="item.icon.svg"></svg>
        </div>
        <h3 class="app-title">{{ item.title }}</h3>
        <p class="app-description">{{ item.description }}</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
// 菜单数据单一真源：与 scripts/contrast-audit.mjs 共用，改动后需跑 npm run test:contrast
import { homeMenuItems } from './homeMenu'

const router = useRouter()

const navigateToApp = (item) => {
  router.push(item.path)
}
</script>

<style scoped>
.home-container {
  min-height: 100vh;
  /* 使用视口高度，避免高度计算问题 */
  background: var(--app-background);
  /* 使用 themeStore 定义的背景 */
  padding: 3rem 2rem;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.3s ease;
  /* 添加过渡效果 */
  overflow-y: auto;
  /* 允许内容溢出时滚动 */
}

.apps-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 2.5rem;
  max-width: 1400px;
  width: 100%;
}

.app-card {
  background: rgba(var(--app-panel-background-rgb, 255, 255, 255), 0.95);
  /* 面板背景色变量（浅色白/深色黑由主题智能匹配），rgba() 内联后备值 */
  border-radius: 20px;
  padding: 2rem;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s ease;
  border: 1px solid rgba(var(--app-panel-background-rgb, 255, 255, 255), 0.2);
  /* 边框颜色也使用RGB变量 */
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  /* 更柔和的阴影 */
  height: fit-content;
}

.app-card:hover {
  transform: translateY(-8px) scale(1.03);
  /* 更明显的上浮和放大 */
  box-shadow: 0 15px 30px rgba(0, 0, 0, 0.15);
  /* 更明显的阴影 */
  /* 主题感知悬停态：在面板合成色上叠加 --app-card-hover-tint（theme.js 按明暗分档的白色提亮层），
     替换原先写死的浅色渐变——深色主题下「浅底 + 浅字」会导致对比度失效。
     该叠层口径已纳入 scripts/contrast-audit.mjs 硬门禁。 */
  background:
    linear-gradient(var(--app-card-hover-tint, rgba(255, 255, 255, 0.45)), var(--app-card-hover-tint, rgba(255, 255, 255, 0.45))),
    rgba(var(--app-panel-background-rgb, 255, 255, 255), 0.95);
}

.app-icon {
  width: 80px;
  height: 80px;
  margin: 0 auto 1.5rem;
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
  /* 背景渐变与图标色由 homeMenu.js 中各菜单项的 icon.gradient / icon.color 驱动 */
}

.app-icon svg {
  width: 40px;
  height: 40px;
}

.app-card:hover .app-icon {
  transform: scale(1.1) rotate(5deg);
  filter: brightness(1.1);
  /* 增加亮度 */
}

.app-title {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--app-text-primary, #1a202c);
  /* 跟随主题文字色，保证与页面背景对比度 */
  margin-bottom: 0.75rem;
}

.app-description {
  font-size: 1rem;
  color: var(--app-text-secondary, #4a5568);
  /* 跟随主题次级文字色，保证与面板对比度 */
  line-height: 1.6;
  margin: 0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .home-container {
    padding: 2rem 1.5rem;
  }

  .apps-grid {
    grid-template-columns: 1fr;
    gap: 2rem;
  }

  .app-card {
    padding: 1.5rem;
  }

  .app-icon {
    width: 60px;
    height: 60px;
  }

  .app-icon svg {
    width: 30px;
    height: 30px;
  }
}

@media (max-width: 480px) {
  .home-container {
    padding: 1.5rem 1rem;
  }

  .apps-grid {
    gap: 1.5rem;
  }

  .app-card {
    padding: 1.25rem;
  }

  .app-title {
    font-size: 1.3rem;
  }

  .app-description {
    font-size: 0.9rem;
  }
}
</style>
