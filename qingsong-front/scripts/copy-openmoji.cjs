// 复制 OpenMoji 静态资源到 public/emoji-openmoji
// 源：node_modules/openmoji/color/svg（4495 个彩色 SVG，CC BY-SA 4.0）
// 文件名统一为「大写 hex、codepoint 以 - 连接、去除 FE0F 变体选择符、去除前导零」形式，与运行时解析命名一致。
// 注意：OpenMoji 源文件为 4 位补零命名（如 0032-FE0F-20E3.svg），需去掉前导零（-> 32-20E3.svg）。
const fs = require('fs')
const path = require('path')

const src = path.resolve(__dirname, '../node_modules/openmoji/color/svg')
const dest = path.resolve(__dirname, '../public/emoji-openmoji')

if (!fs.existsSync(src)) {
  console.warn('[copy-openmoji] 未找到 openmoji 资源，请先 npm install。')
  process.exit(0)
}

// 去除 FE0F，并去掉每个 codepoint 的前导零（0032 -> 32，避免与运行时命名不一致）
const normalize = file => {
  const base = file.replace(/\.svg$/i, '')
  const stripped = base
    .replace(/-FE0F/g, '')
    .split('-')
    .map(part => part.replace(/^0+(?=[0-9A-Fa-f])/, ''))
    .join('-')
  return `${stripped}.svg`
}

// 清理旧产物，避免残留旧的补零命名文件
fs.rmSync(dest, { recursive: true, force: true })
fs.mkdirSync(dest, { recursive: true })
let copied = 0
for (const file of fs.readdirSync(src)) {
  if (!file.endsWith('.svg')) continue
  fs.copyFileSync(path.join(src, file), path.join(dest, normalize(file)))
  copied++
}
console.log(`[copy-openmoji] 已复制 ${copied} 个 OpenMoji 资源 -> public/emoji-openmoji`)
