// 代码高亮主题服务（跨模块：聊天 Markdown 代码块 + 设置页预览）
// 收敛 highlight.js 主题样式注入的单一真源：
// - 浅/深两套 github 主题 CSS 用 ?inline 作为字符串打进产物（浏览器内动态 import）；
// - 由本模块管理一个 <style>，切换主题只改其 textContent，不依赖 Vite 注入顺序。
// - 仅浏览器环境才动态 import CSS，node（单元测试）下直接短路，不引入别名/静态 CSS 依赖。
const loadThemeCss = (() => {
  const cache = {
    light: null,
    dark: null
  }
  return name => {
    if (!cache[name]) {
      cache[name] = name === 'light'
        ? import('highlight.js/styles/github.css?inline')
        : import('highlight.js/styles/github-dark.css?inline')
    }
    return cache[name]
  }
})()

let themeStyleEl = null
// 切换令牌：applyCodeTheme 是异步的，快速连续切换主题时，
// 仅应用最后一次调用的结果，避免旧 CSS 加载完成后覆盖新选择。
let applyToken = 0

// 'light' | 'dark'：把对应高亮主题应用到页面（幂等，可重复调用）。
// 代码块背景色与代码文字色由 theme.js applyStyles 同步设置 CSS 变量，这里只负责 token 配色。
export const applyCodeTheme = async (theme = 'dark') => {
  if (typeof document === 'undefined' || typeof document.createElement !== 'function') return
  const resolved = theme === 'light' ? 'light' : 'dark'
  const token = ++applyToken
  if (!themeStyleEl) {
    themeStyleEl = document.createElement('style')
    themeStyleEl.setAttribute('data-role', 'hljs-theme')
    document.head.appendChild(themeStyleEl)
  }
  try {
    const mod = await loadThemeCss(resolved)
    if (themeStyleEl && mod?.default && token === applyToken) {
      themeStyleEl.textContent = mod.default
    }
  } catch (error) {
    console.error('代码高亮主题加载失败:', error)
  }
}
