/**
 * 色彩数学共享模块（纯函数，无环境依赖）
 *
 * 供两处使用，保证「运行时主题推导」与「构建期对比度审计」用同一套口径：
 * 1. src/stores/theme.js —— effectiveIsDark 明暗推导（isDarkColor）
 * 2. scripts/contrast-audit.mjs —— WCAG 对比度硬门禁（luminance / contrastRatio 等）
 *
 * 解析口径：hex / rgb() / rgba() / linear-gradient() 均取色标平均值作为代表色；
 * rgba 的 alpha 通道在明暗推导中忽略（页面背景不支持半透明叠底语义，取色器 alpha 仅作展示）。
 */

// sRGB 通道 → 线性亮度分量（WCAG 2.x 定义）
export const channelToLinear = (c) => {
  const s = c / 255
  return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4)
}

// 相对亮度（0 = 黑，1 = 白）
export const luminance = (r, g, b) =>
  0.2126 * channelToLinear(r) + 0.7152 * channelToLinear(g) + 0.0722 * channelToLinear(b)

// '#abc' / '#aabbcc' → [r, g, b]；非法输入返回 null
export const hexToRgb = (hex) => {
  let h = String(hex).replace('#', '')
  if (h.length === 3) h = h.split('').map((x) => x + x).join('')
  if (!/^[0-9a-fA-F]{6}$/.test(h)) return null
  return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)]
}

// 提取颜色值里所有色标（hex + rgb/rgba），供渐变取平均
export const extractColorStops = (value) => {
  const stops = []
  const str = String(value)
  const rgbaRe = /rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*(?:,\s*([\d.]+)\s*)?\)/g
  let m
  while ((m = rgbaRe.exec(str)) !== null) {
    stops.push({ rgb: [+m[1], +m[2], +m[3]], alpha: m[4] !== undefined ? Math.min(1, +m[4]) : 1 })
  }
  const hexRe = /#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})/g
  while ((m = hexRe.exec(str)) !== null) {
    stops.push({ rgb: hexToRgb(m[0]), alpha: 1 })
  }
  return stops.filter((s) => s.rgb)
}

// 任意颜色值 → 代表色 [r,g,b]（各色标算术平均）；无法解析返回 null
export const representativeRgb = (value) => {
  const stops = extractColorStops(value)
  if (!stops.length) return null
  const sum = [0, 0, 0]
  for (const s of stops) {
    sum[0] += s.rgb[0]
    sum[1] += s.rgb[1]
    sum[2] += s.rgb[2]
  }
  return sum.map((v) => Math.round(v / stops.length))
}

// WCAG 对比度（1 ~ 21）
export const contrastRatio = (l1, l2) => {
  const [hi, lo] = [l1, l2].sort((a, b) => b - a)
  return (hi + 0.05) / (lo + 0.05)
}

// 半透明前景（fg + alpha）叠加到背景 bg 上的合成色
export const compositeOver = (bg, fg, alpha) => {
  const a = Math.min(1, Math.max(0, alpha))
  return [0, 1, 2].map((i) => Math.round(fg[i] * a + bg[i] * (1 - a)))
}

// 明暗分界亮度：现有背景预设深色最大 0.167 / 浅色最小 0.416（见 contrast-audit 一致性检查），
// 阈值取 0.25 落在空档内，对自定义颜色给出确定性的明暗判定。
export const DARK_LUMINANCE_THRESHOLD = 0.25

// 推导任意背景色的明暗：代表色亮度低于阈值视为深色（配浅色文字）
export const isDarkColor = (value) => {
  const rep = representativeRgb(value)
  if (!rep) return false // 无法解析时按浅色兜底（深色文字在未知背景上更稳妥）
  return luminance(...rep) < DARK_LUMINANCE_THRESHOLD
}
