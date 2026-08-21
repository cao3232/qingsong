<template>
  <div class="home-container">
    <div class="apps-grid">
      <!-- AI 对话 -->
      <div class="app-card" @click="navigateToApp('chat')">
        <div class="app-icon chat-icon">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path
              d="M7.12 18.36L4 20l1.08-3.46A7.77 7.77 0 013.5 12c0-4.14 3.8-7.5 8.5-7.5s8.5 3.36 8.5 7.5-3.8 7.5-8.5 7.5c-1.28 0-2.5-.25-3.62-.7-.42-.16-.88-.14-1.26.06z"
              fill="currentColor" opacity="0.22" />
            <path
              d="M7.12 18.36L4 20l1.08-3.46A7.77 7.77 0 013.5 12c0-4.14 3.8-7.5 8.5-7.5s8.5 3.36 8.5 7.5-3.8 7.5-8.5 7.5c-1.28 0-2.5-.25-3.62-.7-.42-.16-.88-.14-1.26.06z"
              stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
            <path d="M8.75 10.5h6.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            <path d="M8.75 13.5h4.25" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            <circle cx="15.85" cy="13.5" r="1.15" fill="currentColor" />
          </svg>
        </div>
        <h3 class="app-title">AI 对话</h3>
        <p class="app-description">与AI助手进行智能对话</p>
      </div>

      <!-- PDF 阅读 -->
      <div class="app-card" @click="navigateToApp('pdf-reader')">
        <div class="app-icon pdf-reader-icon">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M6 3h9l3 3v15H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" />
            <path d="M14 3v4h4M8 12h8M8 15h6M8 18h4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
          </svg>
        </div>
        <h3 class="app-title">PDF 阅读</h3>
        <p class="app-description">阅读 PDF 书籍并使用 TTS 朗读</p>
      </div>

      <!-- 系统配置 -->
      <div class="app-card" @click="navigateToApp('config')">
        <div class="app-icon config-icon">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 15a3 3 0 100-6 3 3 0 000 6z" stroke="currentColor" stroke-width="2" stroke-linecap="round"
              stroke-linejoin="round" />
            <path
              d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-2 2 2 2 0 01-2-2v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83 0 2 2 0 010-2.83l.06-.06a1.65 1.65 0 00.33-1.82 1.65 1.65 0 00-1.51-1H3a2 2 0 01-2-2 2 2 0 012-2h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 010-2.83 2 2 0 012.83 0l.06.06a1.65 1.65 0 001.82.33H9a1.65 1.65 0 001-1.51V3a2 2 0 012-2 2 2 0 012 2v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 0 2 2 0 010 2.83l-.06.06a1.65 1.65 0 00-.33 1.82V9a1.65 1.65 0 001.51 1H21a2 2 0 012 2 2 2 0 01-2 2h-.09a1.65 1.65 0 00-1.51 1z"
              stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </div>
        <h3 class="app-title">系统配置</h3>
        <p class="app-description">管理API密钥和模型设置</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const navigateToApp = (appType) => {
  switch (appType) {
    case 'chat':
      router.push('/chat')
      break
    case 'pdf-reader':
      router.push('/pdf-reader')
      break
    case 'config':
      router.push('/config')
      break
    default:
      alert('该功能正在开发中，敬请期待！')
  }
}
</script>

<style scoped>
.home-container {
  min-height: 100vh;
  background: var(--app-background);
  padding: 3rem 2rem;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.3s ease;
  overflow-y: auto;
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
  border-radius: 20px;
  padding: 2rem;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s ease;
  border: 1px solid rgba(var(--app-panel-background-rgb, 255, 255, 255), 0.2);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  height: fit-content;
}

.app-card:hover {
  transform: translateY(-8px) scale(1.03);
  box-shadow: 0 15px 30px rgba(0, 0, 0, 0.15);
  background: linear-gradient(135deg, #e0f7fa 0%, #e8f5e9 100%);
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
}

.app-icon svg {
  width: 40px;
  height: 40px;
}

.chat-icon {
  background: linear-gradient(135deg, #0f9bff 0%, #44e2c4 100%);
  color: white;
}

.pdf-reader-icon {
  background: linear-gradient(135deg, #0f766e 0%, #38b2ac 100%);
  color: white;
}

.config-icon {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  color: white;
}

.app-card:not(.coming-soon):hover .app-icon {
  transform: scale(1.1) rotate(5deg);
  filter: brightness(1.1);
}

.app-title {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--app-text-primary, #1a202c);
  margin-bottom: 0.75rem;
}

.app-description {
  font-size: 1rem;
  color: var(--app-text-secondary, #4a5568);
  line-height: 1.6;
  margin: 0;
}

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
