<template>
  <div class="app">
    <nav v-if="isHomePage" class="navbar">
          <router-link to="/" class="logo-wrapper">
            <div class="logo-brand">
              <svg class="logo-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M12 2L4 8L12 14L20 8L12 2Z" fill="currentColor" opacity="0.9" />
                <path d="M12 8L4 14L12 20L20 14L12 8Z" fill="currentColor" opacity="0.7" />
                <path d="M12 14L6 18.5L12 23L18 18.5L12 14Z" fill="currentColor" opacity="0.5" />
              </svg>
              <span class="logo-text">青松</span>
            </div>
            <span class="logo-slogan">让人生更轻松</span>
          </router-link>
          <div class="navbar-right">
            <router-link to="/chat" class="ai-entry-btn">
              <span class="ai-dot"></span>
              小江子AI
            </router-link>
            <n-dropdown :options="userOptions" @select="handleSelect">
              <button class="account-btn" type="button">
                <img v-if="userAvatarUrl && !avatarFailed" :src="userAvatarUrl" class="account-avatar-img" alt="账户头像" @error="avatarFailed = true" />
                <span v-else class="account-avatar">{{ avatarText }}</span>
              </button>
            </n-dropdown>
          </div>

        </nav>

        <div class="router-view-container" :class="{ 'with-navbar': isHomePage }">
          <router-view v-slot="{ Component, route: currentRoute }">
            <keep-alive v-if="currentRoute.meta.keepAlive">
              <component :is="Component" :key="currentRoute.name" />
            </keep-alive>
            <component :is="Component" :key="currentRoute.name" v-else />
          </router-view>
        </div>
  </div>
</template>

<script setup lang="ts">
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { computed, ref, watch } from 'vue'
import { NDropdown, useMessage } from 'naive-ui'
import { authService } from '@/services/authService'
import { useThemeStore } from '@/stores/theme'
import { buildAvatarUrl } from '@/shared/utils/avatarUtils'


const route = useRoute()
const router = useRouter()
const message = useMessage()
const themeStore = useThemeStore()

// 暴露 message 实例到 window，供 http 拦截器等服务层在 setup 外复用（Naive UI 官方推荐方案）
;(window as Window & { $message?: typeof message }).$message = message

const isHomePage = computed(() => route.path === '/')

// 账户头像文字：取登录账号首字符，无登录态时显示默认图标
const avatarText = computed(() => {
  const session = authService.getSession()
  const account = session?.account || session?.username || session?.nickname || ''
  return account ? String(account).charAt(0).toUpperCase() : '我'
})

// 用户自定义头像（配置中开启时使用 DiceBear 生成的链接）
const userAvatarUrl = computed(() => {
  const cfg = themeStore.config.userAvatar
  if (!cfg || !cfg.enabled) return ''
  return buildAvatarUrl(cfg)
})

// 头像加载失败时回退到首字母头像；配置变化后重置，重新尝试加载
const avatarFailed = ref(false)
watch(userAvatarUrl, () => {
  avatarFailed.value = false
})

const userOptions = [
  {
    label: '用户资料',
    key: 'profile',
  },
  {
    label: '编辑用户资料',
    key: 'editProfile',
  },
  {
    label: '退出登录',
    key: 'logout',
  }
]

const handleSelect = async (key: string) => {
  if (key === 'logout') {
    try {
      await authService.logout()
      message.success('已退出登录')
    } catch {
      message.warning('服务端登出失败，本地登录态已清除')
    } finally {
      router.push('/login')
    }
  } else if (key === 'editProfile') {
    message.info('编辑资料功能开发中，敬请期待')
  } else if (key === 'profile') {
    message.info('用户资料功能开发中，敬请期待')
  }
}
</script>

<style lang="scss">
:root {
  --bg-color: #f7f8fa;
  --text-color: #333;
}

:root {
  --n-message-color: var(--text-color);
  --n-message-bg-color: var(--bg-color);
}

.n-message {
  background-color: var(--bg-color) !important;
  color: var(--text-color) !important;
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html,
body,
#app {
  height: 100%;
  width: 100%;
}

body {
  font-family: var(--app-font-family);
  font-size: var(--app-font-size);
  color: var(--text-color);
  background: var(--bg-color);
}

.app {
  height: 100vh;
  /* fallback */
  height: 100dvh;
  display: flex;
  flex-direction: column;
}

.router-view-container {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  position: relative;
  background-color: #f0f2f5;
}

.navbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 2rem;
  background: rgba(var(--app-panel-background-rgb, 255, 255, 255), 0.15);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  position: sticky;
  top: 0;
  z-index: 100;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);

  .logo-wrapper {
    display: flex;
    align-items: center;
    gap: 1.25rem;
    text-decoration: none;
    color: var(--app-text-primary, #1a1a1a);
    transition: all 0.3s ease;

    &:hover {
      transform: translateY(-1px);

      .logo-icon {
        transform: scale(1.1);
      }
    }
  }

  .logo-brand {
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }

  .logo-icon {
    width: 32px;
    height: 32px;
    color: #059669;
    transition: transform 0.3s ease;
    filter: drop-shadow(0 2px 4px rgba(16, 185, 129, 0.3));
  }

  .logo-text {
    font-size: 1.75rem;
    font-weight: 700;
    letter-spacing: 2px;
    background: linear-gradient(135deg, #059669 0%, #10b981 50%, #34d399 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }

  .logo-slogan {
    font-size: 1rem;
    font-weight: 400;
    color: var(--app-text-secondary, rgba(26, 26, 26, 0.65));
    letter-spacing: 1px;
    position: relative;
    padding-left: 1.25rem;

    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 50%;
      transform: translateY(-50%);
      width: 1px;
      height: 60%;
      background: var(--app-border-color, rgba(0, 0, 0, 0.15));
    }
  }
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.ai-entry-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1.1rem;
  border-radius: 999px;
  text-decoration: none;
  font-size: 0.95rem;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #059669 0%, #10b981 50%, #34d399 100%);
  box-shadow: 0 4px 14px rgba(16, 185, 129, 0.35);
  transition: all 0.25s ease;

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 6px 18px rgba(16, 185, 129, 0.45);
  }

  .ai-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: #fff;
    box-shadow: 0 0 0 3px rgba(255, 255, 255, 0.35);
  }
}

.account-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.85);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  transition: all 0.25s ease;

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.18);
  }

  .account-avatar {
    font-size: 1rem;
    font-weight: 700;
    color: #059669;
  }

  .account-avatar-img {
    width: 28px;
    height: 28px;
    border-radius: 50%;
    object-fit: cover;
    display: block;
  }
}

.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (max-width: 768px) {
  .navbar {
    padding: 1rem;

    .logo-wrapper {
      flex-direction: column;
      align-items: flex-start;
      gap: 0.25rem;
    }

    .logo-slogan {
      padding-left: 0;
      font-size: 0.85rem;

      &::before {
        display: none;
      }
    }

    .logo-text {
      font-size: 1.5rem;
    }
  }
}
</style>
