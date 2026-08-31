/**
 * 页面背景预设 + 聊天皮肤 + 首页路由菜单 对比度校验规则工具
 *
 * 校验口径（WCAG 2.x，硬门禁，任一不达标即非 0 退出）：
 * A. 页面背景预设（backgroundPresets，60 个）：
 *    - 主文字 vs 页面背景          ≥ 4.5:1（文字可能直接落在背景上，如导航/空状态）
 *    - 主文字 vs 面板合成色        ≥ 4.5:1（首页卡片标题等）
 *    - 次级文字 vs 面板合成色      ≥ 4.5:1（首页卡片描述、配置页说明等）
 *    - 主文字 vs 悬停面板合成色    ≥ 4.5:1（首页菜单卡片 :hover 态标题）
 *    - 次级文字 vs 悬停面板合成色  ≥ 4.5:1（首页菜单卡片 :hover 态描述）
 *    - isDark 标志一致性：预设的 isDark 必须与 isDarkColor() 亮度推导一致
 *      （运行时 effectiveIsDark 对自定义颜色按亮度推导、对预设取标志，两套口径不得分叉）
 *    - 菜单/子标签交互态（D 段，/config 页面）：
 *      侧栏普通/悬停/激活、子标签普通/悬停/激活 各 ≥ 4.5:1
 *      （侧栏面板 = --app-panel-background 叠页面；悬停底 = --app-component-bg；
 *        侧栏激活 = color-mix(accent 14%, 面板)；子标签激活 = --app-active-bg 填充 + 白字）
 * B. 聊天皮肤（skinPresets，12 款）：
 *    - 主文字 vs 壁纸 / 面板、次级文字 vs 面板          ≥ 4.5:1
 *    - 强调文字 vs 强调色、用户/AI 气泡、标题、链接、行内代码、引用块、输入区、标题栏  ≥ 4.5:1
 *    - 固定背景皮肤（非 followPageBackground）：首页文字 vs 皮肤背景、首页次级 vs 面板 ≥ 4.5:1
 *      （皮肤 pageBackground 经 effectivePageBackground 生效为 --app-background，文字随 effectiveIsDark）
 * C. 首页路由菜单（src/app/pages/homeMenu.js，10 项）：
 *    - 图标颜色 vs 图标渐变代表色  ≥ 3:1（WCAG 1.4.11 非文本对比度，图标语义由文字标签兜底，取非文本档）
 *
 * 面板合成色：首页 .app-card 使用 rgba(var(--app-panel-background-rgb), 0.95)，
 * 即面板色（浅色 rgba(255,255,255,0.5) / 深色 rgba(0,0,0,0.3)）以 95% 不透明度叠加到页面背景上。
 *
 * 悬停面板合成色：.app-card:hover 在面板之上再叠一层 --app-card-hover-tint（提亮语义，
 * 浅色 rgba(255,255,255,0.45) / 深色 rgba(255,255,255,0.1)，取自 theme.js applyStyles），
 * 即 compositeOver(面板合成色, 悬停色, 悬停不透明度)。
 *
 * 「次级文字 vs 原始背景」仅作信息展示：次级文字（--app-text-secondary）在设计上只落在面板上，
 * 不直接落背景，因此不纳入硬门禁。
 *
 * 聊天皮肤颜色解析：hex / rgba() / linear-gradient() 均取色标平均值（与既有渐变近似口径一致）；
 * 带透明度（rgba）的前景按所在层级叠合到下层背景再取平均（如 AI 气泡/面板半透明则先叠到壁纸上）。
 *
 * 用法：node scripts/contrast-audit.mjs
 */
import { createPinia, setActivePinia } from 'pinia'
import { useThemeStore } from '../src/stores/theme.js'
import { homeMenuItems } from '../src/app/pages/homeMenu.js'
// 色彩数学与运行时主题推导共用同一模块（src/shared/utils/colorUtils.js），
// 保证审计口径与 effectiveIsDark 的明暗推导一致
import {
  luminance,
  hexToRgb,
  representativeRgb,
  extractColorStops,
  contrastRatio,
  compositeOver,
  isDarkColor
} from '../src/shared/utils/colorUtils.js'

const AA_TEXT = 4.5

globalThis.document = {
  documentElement: { style: { setProperty: () => {} } }
}
globalThis.localStorage = { getItem: () => null, setItem: () => {} }

setActivePinia(createPinia())
const themeStore = useThemeStore()
themeStore.init()

const presets = themeStore.backgroundPresets

// ---- 与 applyStyles 保持一致的主题配色（topicTrackerContrast = 'high' 默认档）----
const THEME = {
  light: {
    textPrimary: hexToRgb('#374151'),
    textSecondary: hexToRgb('#6b7280'),
    panelRgb: [255, 255, 255],
    panelAlpha: 0.5,
    // --app-card-hover-tint（浅色）：rgba(255, 255, 255, 0.45)，首页卡片悬停提亮层
    hoverRgb: [255, 255, 255],
    hoverAlpha: 0.45,
    // --app-component-bg（浅色）：rgba(248,250,252,0.5)，侧栏悬停/表单区底色
    componentRgb: [248, 250, 252],
    componentAlpha: 0.5,
    // --app-bg-secondary（浅色）：#f3f4f6 不透明，子标签轨道底色
    bgSecondaryRgb: [243, 244, 246],
    bgSecondaryAlpha: 1,
    // --app-active-bg（浅色）：#2563eb 实心；--app-active-text：#ffffff
    accentRgb: [37, 99, 235],
    accentAlpha: 1,
    activeText: [255, 255, 255]
  },
  dark: {
    textPrimary: hexToRgb('#f8f9fa'),
    textSecondary: hexToRgb('#adb5bd'),
    panelRgb: [0, 0, 0],
    panelAlpha: 0.3,
    // --app-card-hover-tint（深色）：rgba(255, 255, 255, 0.1)
    hoverRgb: [255, 255, 255],
    hoverAlpha: 0.1,
    // --app-component-bg（深色）：rgba(255,255,255,0.05)
    componentRgb: [255, 255, 255],
    componentAlpha: 0.05,
    // --app-bg-secondary（深色）：rgba(0,0,0,0.18) 压暗（中亮度深色背景上叠亮层会稀释白字对比度）
    bgSecondaryRgb: [0, 0, 0],
    bgSecondaryAlpha: 0.18,
    // --app-active-bg（深色）：rgba(59,130,246,0.2) 半透明，叠页面背景后再测白字
    accentRgb: [59, 130, 246],
    accentAlpha: 0.2,
    activeText: [255, 255, 255]
  }
}
// 首页卡片面板的实际不透明度（HomePage.vue .app-card）
const HOME_PANEL_ALPHA = 0.95
// WCAG 1.4.11 非文本（图形/图标）对比度阈值
const AA_NON_TEXT = 3

const FAILS = []

for (const preset of presets) {
  const rep = representativeRgb(preset.value)
  if (!rep) {
    FAILS.push({ name: preset.name, reason: `无法解析颜色: ${preset.value}` })
    continue
  }
  // 预设 isDark 标志必须与运行时明暗推导（isDarkColor，effectiveIsDark 的自定义颜色口径）一致，
  // 否则「用户自选颜色」和「预设」会走出两套明暗，首页文字色与背景失配
  if (isDarkColor(preset.value) !== !!preset.isDark) {
    FAILS.push({
      name: preset.name,
      value: preset.value,
      reason: `isDark=${preset.isDark} 与亮度推导不一致（代表色亮度越过 0.25 阈值），请修正标志或调整配色`
    })
    continue
  }
  const theme = preset.isDark ? THEME.dark : THEME.light
  const bgLum = luminance(...rep)
  const panel = compositeOver(rep, theme.panelRgb, HOME_PANEL_ALPHA)
  const panelLum = luminance(...panel)
  // 首页菜单卡片 :hover：面板之上再叠 --app-card-hover-tint（HomePage.vue .app-card:hover）
  const hoverPanel = compositeOver(panel, theme.hoverRgb, theme.hoverAlpha)
  const hoverPanelLum = luminance(...hoverPanel)
  const textPrimaryLum = luminance(...theme.textPrimary)
  const textSecondaryLum = luminance(...theme.textSecondary)

  // ---- D. 配置页菜单/子标签交互态（ConfigView .menu-item / SystemSettingPage .sub-tab-btn）----
  // 侧栏面板 = --app-panel-background（智能匹配后的默认玻璃色）叠页面背景
  const sidebarPanel = compositeOver(rep, theme.panelRgb, theme.panelAlpha)
  const sidebarPanelLum = luminance(...sidebarPanel)
  // --app-component-bg 叠页面（侧栏 hover 底、子标签所在 config-panel 底）
  const componentBg = compositeOver(rep, theme.componentRgb, theme.componentAlpha)
  const componentBgLum = luminance(...componentBg)
  // 主色叠页面（dark 档 --app-active-bg 为半透明蓝）
  const accentOnPage = compositeOver(rep, theme.accentRgb, theme.accentAlpha)
  // 侧栏激活项：color-mix(accent 14%, 侧栏面板)
  const sidebarActive = compositeOver(sidebarPanel, accentOnPage, 0.14)
  // 子标签轨道底色：.sub-tabs 用 --app-bg-secondary，叠在 config-panel（componentBg 层）之上
  const subTabTrack = compositeOver(componentBg, theme.bgSecondaryRgb, theme.bgSecondaryAlpha)
  // 子标签悬停：color-mix(accent 10%, transparent) 叠轨道底
  const subTabHover = compositeOver(subTabTrack, theme.accentRgb, theme.accentAlpha * 0.10)
  // 子标签激活：--app-active-bg 填充（dark 档半透明需叠到轨道底上）
  const subTabActive = compositeOver(subTabTrack, theme.accentRgb, theme.accentAlpha)
  const activeTextLum = luminance(...theme.activeText)

  const checks = [
    { label: '主文字 vs 背景', ratio: contrastRatio(bgLum, textPrimaryLum) },
    { label: '主文字 vs 面板', ratio: contrastRatio(panelLum, textPrimaryLum) },
    { label: '次级文字 vs 面板', ratio: contrastRatio(panelLum, textSecondaryLum) },
    { label: '悬停主文字 vs 悬停面板', ratio: contrastRatio(hoverPanelLum, textPrimaryLum) },
    { label: '悬停次级文字 vs 悬停面板', ratio: contrastRatio(hoverPanelLum, textSecondaryLum) },
    { label: '侧栏普通项', ratio: contrastRatio(sidebarPanelLum, textPrimaryLum) },
    { label: '侧栏悬停项', ratio: contrastRatio(luminance(...compositeOver(sidebarPanel, theme.componentRgb, theme.componentAlpha)), textPrimaryLum) },
    { label: '侧栏激活项', ratio: contrastRatio(luminance(...sidebarActive), textPrimaryLum) },
    { label: '子标签普通', ratio: contrastRatio(luminance(...subTabTrack), textPrimaryLum) },
    { label: '子标签悬停', ratio: contrastRatio(luminance(...subTabHover), textPrimaryLum) },
    { label: '子标签激活', ratio: contrastRatio(luminance(...subTabActive), activeTextLum) }
  ]

  const infoSecondaryBg = contrastRatio(bgLum, textSecondaryLum)
  const failed = checks.filter((c) => c.ratio < AA_TEXT)
  const repHex = `#${rep.map((n) => n.toString(16).padStart(2, '0')).join('')}`

  if (failed.length > 0) {
    FAILS.push({
      name: preset.name,
      value: preset.value,
      isDark: preset.isDark,
      rep: repHex,
      checks: checks.map((c) => `${c.label}=${c.ratio.toFixed(2)}`).join('，'),
      infoSecondaryBg: `次级文字 vs 背景=${infoSecondaryBg.toFixed(2)}`
    })
  } else {
    console.log(
      `PASS | ${preset.name.padEnd(10)} | ${preset.isDark ? 'dark' : 'light'.padEnd(4)} | ${repHex} | ` +
        checks.map((c) => `${c.ratio.toFixed(2)}`).join('/') +
        `（次级vs背景 ${infoSecondaryBg.toFixed(2)}）`
    )
  }
}

console.log('')

// ---- 聊天皮肤（skinPresets）审计 ----
const skinPresets = themeStore.skinPresets

// 把任意颜色值解析为代表色 [r,g,b]；base 是该层叠合的下层背景代表色
const resolveColor = (value, base) => {
  const stops = extractColorStops(value)
  if (!stops.length) return base
  const sum = [0, 0, 0]
  for (const s of stops) {
    const c = s.alpha >= 1 ? s.rgb : compositeOver(base, s.rgb, s.alpha)
    sum[0] += c[0]
    sum[1] += c[1]
    sum[2] += c[2]
  }
  return sum.map((v) => Math.round(v / stops.length))
}

const varOf = (skin, key, fallback) => skin.vars[key] || fallback

for (const skin of skinPresets) {
  const wallpaper = resolveColor(varOf(skin, '--chat-wallpaper', skin.isDark ? '#1f2937' : '#eef2f7'), [0, 0, 0])
  const panel = resolveColor(varOf(skin, '--chat-panel', '#c0c0c0'), wallpaper)
  const aiBubble = resolveColor(varOf(skin, '--chat-ai-bubble-bg', '#ffffff'), wallpaper)
  const surface = resolveColor(varOf(skin, '--chat-surface', '#ffffff'), wallpaper)
  const inlineCodeBg = resolveColor(varOf(skin, '--chat-inline-code-bg', '#f1f5f9'), aiBubble)
  const blockquoteBg = resolveColor(varOf(skin, '--chat-blockquote-bg', '#f1f5f9'), aiBubble)
  const userBubble = resolveColor(varOf(skin, '--chat-user-bubble-bg', '#3b82f6'), wallpaper)
  const accent = resolveColor(varOf(skin, '--chat-accent', '#000080'), wallpaper)
  const titlebar = resolveColor(
    `linear-gradient(135deg, ${varOf(skin, '--chat-titlebar-start', '#000080')}, ${varOf(skin, '--chat-titlebar-end', '#1084d0')})`,
    wallpaper
  )

  const pairs = [
    { label: '主文字vs壁纸', fg: resolveColor(varOf(skin, '--chat-text', '#000000'), wallpaper), bg: wallpaper },
    { label: '主文字vs面板', fg: resolveColor(varOf(skin, '--chat-text', '#000000'), panel), bg: panel },
    { label: '次级vs面板', fg: resolveColor(varOf(skin, '--chat-text-muted', '#5f5f5f'), panel), bg: panel },
    { label: '强调vs强调色', fg: resolveColor(varOf(skin, '--chat-text-on-accent', '#ffffff'), accent), bg: accent },
    { label: '用户气泡', fg: resolveColor(varOf(skin, '--chat-user-bubble-text', '#ffffff'), userBubble), bg: userBubble },
    { label: '正文vs气泡', fg: resolveColor(varOf(skin, '--chat-markdown-text', '#374151'), aiBubble), bg: aiBubble },
    { label: '标题vs气泡', fg: resolveColor(varOf(skin, '--chat-markdown-heading', '#111827'), aiBubble), bg: aiBubble },
    { label: '链接vs气泡', fg: resolveColor(varOf(skin, '--chat-link', '#1d4ed8'), aiBubble), bg: aiBubble },
    { label: '行内代码', fg: resolveColor(varOf(skin, '--chat-inline-code-text', '#b42318'), inlineCodeBg), bg: inlineCodeBg },
    { label: '引用块', fg: resolveColor(varOf(skin, '--chat-blockquote-text', '#475569'), blockquoteBg), bg: blockquoteBg },
    { label: '输入区', fg: resolveColor(varOf(skin, '--chat-surface-text', '#111827'), surface), bg: surface },
    { label: '标题栏', fg: resolveColor(varOf(skin, '--chat-titlebar-text', '#ffffff'), titlebar), bg: titlebar }
  ]

  // 皮肤固定背景（非 followPageBackground）时经 effectivePageBackground 生效为 --app-background，
  // 首页/导航栏文字色走 --app-text-primary/secondary（随 effectiveIsDark = skin.isDark），此处一并校验；
  // 跟随型皮肤的页面背景取用户自选（已由 A 段预设 + 明暗一致性检查覆盖），不重复校验。
  if (!skin.followPageBackground && skin.pageBackground) {
    const appTheme = skin.isDark ? THEME.dark : THEME.light
    const appPage = resolveColor(skin.pageBackground, [0, 0, 0])
    const appPanel = compositeOver(appPage, appTheme.panelRgb, HOME_PANEL_ALPHA)
    pairs.push(
      { label: '首页文字vs皮肤背景', fg: appTheme.textPrimary, bg: appPage },
      { label: '首页次级vs面板', fg: appTheme.textSecondary, bg: appPanel }
    )
  }

  const failed = pairs.filter((p) => contrastRatio(luminance(...p.fg), luminance(...p.bg)) < AA_TEXT)
  if (failed.length > 0) {
    FAILS.push({
      name: `皮肤 ${skin.name}`,
      reason: '以下组合低于 WCAG AA',
      checks: pairs.map((p) => `${p.label}=${contrastRatio(luminance(...p.fg), luminance(...p.bg)).toFixed(2)}`).join('，')
    })
  } else {
    console.log(
      `PASS | 皮肤 ${skin.name.padEnd(14)} | ` +
        pairs.map((p) => contrastRatio(luminance(...p.fg), luminance(...p.bg)).toFixed(2)).join('/')
    )
  }
}

console.log('')

// ---- 首页路由菜单（homeMenuItems）审计：图标颜色 vs 图标渐变 ≥ 3:1（WCAG 1.4.11 非文本）----
// 菜单数据与 HomePage.vue 共用单一真源 src/app/pages/homeMenu.js，
// 图标为静态渐变（不随主题明暗变化），与预设无关，统一校验一次。
for (const item of homeMenuItems) {
  const iconBg = representativeRgb(item.icon.gradient)
  if (!iconBg) {
    FAILS.push({ name: `菜单图标 ${item.key}`, reason: `无法解析图标渐变: ${item.icon.gradient}` })
    continue
  }
  const iconFg = hexToRgb(item.icon.color)
  const ratio = contrastRatio(luminance(...iconFg), luminance(...iconBg))
  if (ratio < AA_NON_TEXT) {
    FAILS.push({
      name: `菜单图标 ${item.key}（${item.title}）`,
      reason: `图标颜色 ${item.icon.color} vs 渐变 ${item.icon.gradient} 对比度 ${ratio.toFixed(2)} < ${AA_NON_TEXT}:1（WCAG 非文本）`
    })
  } else {
    console.log(`PASS | 菜单图标 ${item.key.padEnd(14)} | ${ratio.toFixed(2)}`)
  }
}

console.log('')
console.log(`==== 对比度审计结果：页面背景 ${presets.length} 个 + 聊天皮肤 ${skinPresets.length} 个 + 菜单图标 ${homeMenuItems.length} 个，失败 ${FAILS.length} 个 ====`)

for (const f of FAILS) {
  if (f.reason) console.log(`FAIL | ${f.name} | ${f.reason}`)
  else if (f.value) console.log(`FAIL | ${f.name} | ${f.value}`)
  else console.log(`FAIL | ${f.name}`)
  if (f.checks) console.log(`      ${f.checks}${f.infoSecondaryBg ? '，' + f.infoSecondaryBg : ''}`)
}

if (FAILS.length > 0) {
  console.log('')
  console.log('校验未通过：背景/皮肤预设请修正配色（提亮到 ≥0.41 用深字 / 压暗到 ≤0.17 用浅字）；')
  console.log('菜单图标请调整 homeMenu.js 中 icon.color 或 icon.gradient 使对比度 ≥ 3:1，随后重跑。')
  process.exit(1)
}
