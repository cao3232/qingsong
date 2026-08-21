// 从 @iconify-json/fluent-emoji 提取精选 3D 表情到 public/emoji-fluent，并生成运行时集合文件。
// - 精选范围：emoji-datasource 中 Smileys & Emotion / People & Body 类别、且 Fluent 有对应命名的表情
// - 输出：public/emoji-fluent/<HEX>.svg（大写 hex、去除 FE0F）
// - 生成：src/modules/chat/utils/fluentEmojiSet.js（运行时判断是否可替换，未收录回退原生）
const fs = require('fs')
const path = require('path')

const iconsPath = path.resolve(__dirname, '../node_modules/@iconify-json/fluent-emoji/icons.json')
const emojiDataPath = path.resolve(__dirname, '../node_modules/emoji-datasource/emoji.json')
const dest = path.resolve(__dirname, '../public/emoji-fluent')
const setOut = path.resolve(__dirname, '../src/modules/chat/utils/fluentEmojiSet.js')

if (!fs.existsSync(iconsPath)) {
  console.warn('[build-emoji-fluent] 未找到 @iconify-json/fluent-emoji，请先 npm install。')
  process.exit(0)
}
if (!fs.existsSync(emojiDataPath)) {
  console.warn('[build-emoji-fluent] 未找到 emoji-datasource，请先 npm install。')
  process.exit(0)
}

const { icons, width = 32, height = 32 } = JSON.parse(fs.readFileSync(iconsPath, 'utf8'))
const emojiData = JSON.parse(fs.readFileSync(emojiDataPath, 'utf8'))

const toKebab = value =>
  String(value)
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
// 与运行时 toCodepointHex 保持一致：大写、去 FE0F
const normalizeHex = unified =>
  String(unified)
    .split('-')
    .filter(part => part.toUpperCase() !== 'FE0F')
    .join('-')
    .toUpperCase()

const FOCUS = new Set(['Smileys & Emotion', 'People & Body'])

// 高频常用表情的手工映射（codepoint -> Fluent 图标名），覆盖命名与 CLDR 不一致的常用表情
const FLUENT_ALIASES = {
  '2764': 'red-heart',
  '1F604': 'grinning-face-with-smiling-eyes',
  '1F605': 'grinning-face-with-sweat',
  '1F606': 'grinning-squinting-face',
  '1F60D': 'smiling-face-with-hearts',
  '1F60B': 'face-savoring-food',
  '1F613': 'cold-face',
  '1F618': 'face-blowing-a-kiss',
  '1F61B': 'face-with-tongue',
  '1F61C': 'winking-face-with-tongue',
  '1F61D': 'squinting-face-with-tongue',
  '1F630': 'anxious-face-with-sweat',
  '1F638': 'grinning-cat-with-smiling-eyes',
  '1F639': 'cat-with-tears-of-joy',
  '1F63B': 'smiling-cat-with-heart-eyes',
  '1F63C': 'cat-with-wry-smile',
  '1F63E': 'pouting-cat',
  '1F63F': 'crying-cat',
  '1F640': 'weary-cat',
  '1F929': 'star-struck',
  '1F92A': 'zany-face',
  '1F92B': 'shushing-face',
  '1F92C': 'face-with-symbols-on-mouth',
  '1F92D': 'face-with-hand-over-mouth',
  '1F92E': 'face-vomiting',
  '1F92F': 'exploding-head',
  '1F970': 'smiling-face-with-smiling-eyes',
  '1F973': 'partying-face',
  '1F974': 'woozy-face',
  '1F975': 'hot-face',
  '1F976': 'cold-face',
  '1F97A': 'pleading-face',
  '1F44D': 'thumbs-up',
  '1F44E': 'thumbs-down',
  '1F44F': 'clapping-hands',
  '1F64F': 'folded-hands',
  '1F4A5': 'collision',
  '1F4AF': 'hundred-points',
  '1F44A': 'oncoming-fist',
  '1F381': 'wrapped-gift',
  '1F4AA': 'flexed-biceps',
  '1F918': 'sign-of-the-horns',
  '1F91F': 'love-you-gesture',
  '1F64B': 'person-raising-hand',
  '1F596': 'hand-with-fingers-splayed',
  '1F64D': 'person-frowning',
  '1F64E': 'person-pouting',
  '1F90C': 'pinched-fingers',
  '1F90F': 'pinching-hand',
  '1F91D': 'handshake',
  '1F4A6': 'sweat-droplets',
  '1F4AB': 'dizzy',
  '1F4AC': 'speech-balloon',
  '1F5E8': 'left-speech-bubble',
  '1F603': 'grinning-face-with-big-eyes',
  '1F625': 'sad-but-relieved-face',
  '1F63A': 'grinning-cat',
  '1F63D': 'kissing-cat',
  '1F64C': 'raising-hands',
  '1F525': 'fire',
  '1F680': 'rocket',
  '1F389': 'party-popper'
}

const selected = []
const seen = new Set()
const pushSelected = (hex, key) => {
  if (!hex || seen.has(hex) || !icons[key]) return
  seen.add(hex)
  selected.push({ hex, name: key })
}

for (const entry of emojiData) {
  if (!FOCUS.has(entry.category)) continue
  // 优先使用 CLDR 名称（entry.name），其 kebab 形式与 Fluent 图标命名一致
  const kebab = toKebab(entry.name || entry.short_name)
  pushSelected(normalizeHex(entry.unified), kebab)
}

// 补充高频常用表情（命名与 CLDR 不一致的部分）
for (const [hex, key] of Object.entries(FLUENT_ALIASES)) {
  pushSelected(hex, key)
}

fs.mkdirSync(dest, { recursive: true })
const setEntries = []
for (const { hex, name } of selected) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}">${icons[name].body}</svg>`
  fs.writeFileSync(path.join(dest, `${hex}.svg`), svg)
  setEntries.push(`  '${hex}': true`)
}

const setContent = `// 本文件由 scripts/build-emoji-fluent.cjs 自动生成，请勿手动修改。
// 包含 Fluent 3D 精选表情的 codepoint（大写 hex、去除 FE0F），用于运行时判断是否可替换。
// 未运行构建脚本时为默认空集（fluent provider 将回退为原生渲染）。
export const FLUENT_EMOJI_SET = {
${setEntries.join(',\n')}
}
`
fs.writeFileSync(setOut, setContent)

const relativeSet = path.relative(path.resolve(__dirname, '..'), setOut).replace(/\\/g, '/')
console.log(
  `[build-emoji-fluent] 已提取 ${selected.length} 个 Fluent 3D 精选表情 -> public/emoji-fluent，并生成 ${relativeSet}`
)
