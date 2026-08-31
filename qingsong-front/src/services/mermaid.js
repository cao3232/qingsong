import { useThemeStore } from '../stores/theme.js'

// mermaid 共享服务（跨模块：聊天 Markdown 图表 + agent-lab DAG）
// 收敛为单一模块单例与统一配置入口，避免多处 initialize 全局互相覆盖。

let mermaidModulePromise = null

// 'auto' 主题 → 跟随页面/皮肤明暗：暗色用 mermaid 'dark'，亮色用 'neutral'；
// 其余显式主题原样透传；空值按 auto 兜底。
export const resolveMermaidTheme = (themeValue, isThemeDark) => {
  if (themeValue && themeValue !== 'auto') return themeValue
  return isThemeDark ? 'dark' : 'neutral'
}

// 懒加载 mermaid 模块单例（仅在真正出现图表时才加载，避免拖大主包）
export const getMermaid = () => {
  if (!mermaidModulePromise) {
    mermaidModulePromise = import('mermaid').then(mod => mod.default)
  }
  return mermaidModulePromise
}

// 每次渲染前调用，把当前用户配置幂等地应用到 mermaid 全局配置。
// overrides 供调用方固定覆盖（如 GraphRunner 保持 neutral 固定外观，不受用户配置影响）。
export const applyMermaidConfig = (mermaid, overrides = {}) => {
  const themeStore = useThemeStore()
  const conf = themeStore.config
  mermaid.initialize({
    startOnLoad: false,
    securityLevel: 'strict',
    suppressErrorRendering: true,
    // 明暗跟随推导出的生效值（皮肤固定背景用皮肤明暗，自定义背景按亮度推导），
    // 不读可独立改写的 config.isThemeDark，避免与页面实际背景脱钩
    theme: resolveMermaidTheme(conf.mermaidTheme, themeStore.effectiveIsDark),
    look: conf.mermaidLook === 'handDrawn' ? 'handDrawn' : 'classic',
    fontFamily: 'var(--code-font-family, system-ui)',
    ...overrides
  })
}
