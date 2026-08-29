import './assets/main.css'
import './shared/styles/common.scss'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { useThemeStore } from './stores/theme'
import { clearLegacyAuthCookie } from './services/authSession'

clearLegacyAuthCookie()

const app = createApp(App)

app.use(createPinia())
app.use(router)

// 初始化主题 store，加载配置并应用样式
const themeStore = useThemeStore()
themeStore.init()

app.mount('#app')
