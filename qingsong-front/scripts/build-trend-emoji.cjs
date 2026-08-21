const fs = require('fs')
const path = require('path')
const {
  CRITICAL_HEX,
  buildTrendEmojiCatalog,
  normalizeHex,
  toBlobFileName
} = require('./trend-emoji-catalog.cjs')

const ROOT = path.resolve(__dirname, '..')
const BLOB_VERSION = '0.0.1-dev.4'
const OPENMOJI_SOURCE = path.join(ROOT, 'node_modules/openmoji/black/svg')
const SET_OUTPUT = path.join(ROOT, 'src/modules/chat/utils/trendEmojiSets.js')
const MIN_PROVIDER_COUNT = 150

const OUTPUTS = {
  blob: path.join(ROOT, 'public/emoji-blob'),
  doodle: path.join(ROOT, 'public/emoji-doodle')
}

const fetchJson = async url => {
  let response
  try {
    response = await fetch(url, {
      headers: { 'User-Agent': 'jj-ai-trend-emoji-builder' }
    })
  } catch (error) {
    throw new Error(`${error.message}: ${url} (${error.cause?.message || 'unknown cause'})`)
  }
  if (!response.ok) throw new Error(`${response.status} ${response.statusText}: ${url}`)
  return response.json()
}

const fetchBuffer = async url => {
  let response
  try {
    response = await fetch(url, {
      headers: { 'User-Agent': 'jj-ai-trend-emoji-builder' }
    })
  } catch (error) {
    throw new Error(`${error.message}: ${url} (${error.cause?.message || 'unknown cause'})`)
  }
  if (!response.ok) throw new Error(`${response.status} ${response.statusText}: ${url}`)
  return Buffer.from(await response.arrayBuffer())
}

const normalizeOpenMojiFile = file => normalizeHex(file.replace(/\.svg$/i, ''))

const buildOpenMojiIndex = () => new Map(
  fs.readdirSync(OPENMOJI_SOURCE)
    .filter(file => file.endsWith('.svg'))
    .map(file => [normalizeOpenMojiFile(file), file])
)

const loadBlobIndex = async () => {
  const metadata = await fetchJson(`https://unpkg.com/react-blobmoji@${BLOB_VERSION}/?meta`)
  return new Set(
    metadata.files
      .map(file => file.path.replace(/^\//, ''))
      .filter(file => file.startsWith('src/blobmoji/svg/') && file.endsWith('.svg'))
  )
}

const clearOutputs = () => {
  for (const output of Object.values(OUTPUTS)) {
    fs.rmSync(output, { recursive: true, force: true })
    fs.mkdirSync(output, { recursive: true })
  }
}

const writeSets = sets => {
  const render = values => values.map(hex => `    '${hex}': true`).join(',\n')
  const content = `// 本文件由 scripts/build-trend-emoji.cjs 自动生成，请勿手动修改。\n` +
    `export const TREND_EMOJI_SETS = {\n` +
    `  blob: {\n${render(sets.blob)}\n  },\n` +
    `  doodle: {\n${render(sets.doodle)}\n  }\n` +
    `}\n`
  fs.writeFileSync(SET_OUTPUT, content)
}

const assertProvider = (provider, values) => {
  const missingCritical = CRITICAL_HEX.filter(hex => !values.includes(hex))
  if (missingCritical.length) {
    throw new Error(`${provider} 缺少关键表情: ${missingCritical.join(', ')}`)
  }
  if (values.length < MIN_PROVIDER_COUNT) {
    throw new Error(`${provider} 仅匹配 ${values.length} 个表情，低于 ${MIN_PROVIDER_COUNT} 个最低要求`)
  }
}

const main = async () => {
  if (!fs.existsSync(OPENMOJI_SOURCE)) {
    throw new Error('未找到 OpenMoji black/svg，请先运行 npm install')
  }

  const catalog = buildTrendEmojiCatalog()
  const [blobIndex, openMojiIndex] = await Promise.all([loadBlobIndex(), Promise.resolve(buildOpenMojiIndex())])
  const sets = { blob: [], doodle: [] }
  const missing = { blob: [], doodle: [] }
  clearOutputs()

  for (const item of catalog) {
    const blobFile = toBlobFileName(item)
    const blobPath = `src/blobmoji/svg/${blobFile}`
    if (blobIndex.has(blobPath)) {
      const url = `https://cdn.jsdelivr.net/npm/react-blobmoji@${BLOB_VERSION}/${blobPath}`
      fs.writeFileSync(path.join(OUTPUTS.blob, `${item.hex}.svg`), await fetchBuffer(url))
      sets.blob.push(item.hex)
    } else {
      missing.blob.push(item.hex)
    }

    const doodleFile = openMojiIndex.get(item.hex)
    if (doodleFile) {
      fs.copyFileSync(
        path.join(OPENMOJI_SOURCE, doodleFile),
        path.join(OUTPUTS.doodle, `${item.hex}.svg`)
      )
      sets.doodle.push(item.hex)
    } else {
      missing.doodle.push(item.hex)
    }
  }

  for (const [provider, values] of Object.entries(sets)) {
    console.log(
      `[build-trend-emoji] ${provider}: ${values.length}/${catalog.length}, ` +
      `missing: ${missing[provider].length}`
    )
    assertProvider(provider, values)
  }

  writeSets(sets)
}

main().catch(error => {
  console.error(`[build-trend-emoji] ${error.message}`)
  process.exit(1)
})
