const emojiData = require('emoji-datasource/emoji.json')

const CATEGORY_QUOTAS = Object.freeze({
  emotion: 100,
  people: 40,
  symbols: 25,
  activity: 15,
  animals: 10,
  everyday: 10
})

const CRITICAL_HEX = Object.freeze([
  '1F604', '1F44D', '1F60D', '1F602', '1F62D',
  '2764', '1F4AA', '1F631', '1F914'
])

const PRIORITY_HEX = Object.freeze(Array.from(new Set([
  ...CRITICAL_HEX,
  '1F600', '1F603', '1F601', '1F606', '1F605', '1F923', '1F642', '1F643',
  '1F609', '1F60A', '1F607', '1F970', '1F618', '1F617', '1F61A', '1F61C',
  '1F92A', '1F928', '1F9D0', '1F60E', '1F973', '1F60F', '1F612', '1F61E',
  '1F614', '1F61F', '1F615', '1F641', '1F623', '1F616', '1F62B', '1F629',
  '1F971', '1F624', '1F621', '1F620', '1F92C', '1F97A', '1F625', '1F630',
  '1F628', '1F627', '1F626', '1F622', '1F613', '1F62A', '1F634', '1F62E',
  '1F62F', '1F632', '1F633', '1F92F', '1F635', '1F974', '1F92E', '1F922',
  '1F927', '1F912', '1F915', '1F911', '1F920', '1F608', '1F47F', '1F480',
  '1F47B', '1F47D', '1F916', '1F4A9', '1F63A', '1F638', '1F639', '1F63B',
  '1F63C', '1F63D', '1F640', '1F63F', '1F63E', '1F648', '1F649', '1F64A',
  '1F48B', '1F49D', '1F496', '1F497', '1F493', '1F495', '1F494', '1F525',
  '2728', '1F389', '1F38A', '1F381', '1F3C6', '1F680', '1F44E', '1F44F',
  '1F64C', '1F64F', '1F44A', '270A', '1F91D', '1F44B', '1F91F', '1F918',
  '270C', '1F44C', '1F448', '1F449', '1F446', '1F447', '261D', '270B',
  '1F596', '1F64B', '1F937', '1F926', '1F4AF', '2705', '274C', '2753',
  '2757', '26A0', '1F4A1', '1F4AC', '1F4AB', '1F4A5', '1F31F', '2B50',
  '1F308', '2600', '1F319', '1F436', '1F431', '1F43B', '1F430', '1F435',
  '1F98A', '1F43C', '1F428', '1F42F', '1F981', '1F42E', '2615', '1F382',
  '1F355', '1F354', '1F37A', '1F34E', '1F353', '1F36D', '1F3B5', '1F3AE',
  '26BD', '1F3C0', '1F3AF', '1F3B8', '1F3A8', '1F4F7'
])))

const normalizeHex = unified => String(unified)
  .split('-')
  .filter(part => part.toUpperCase() !== 'FE0F')
  .map(part => part.replace(/^0+(?=[0-9A-Fa-f])/, '').toUpperCase())
  .join('-')

const classify = entry => {
  if (entry.category === 'Smileys & Emotion') return 'emotion'
  if (entry.category === 'People & Body') return 'people'
  if (entry.category === 'Symbols') return 'symbols'
  if (entry.category === 'Activities') return 'activity'
  if (entry.category === 'Animals & Nature') return 'animals'
  if (['Food & Drink', 'Travel & Places', 'Objects'].includes(entry.category)) return 'everyday'
  return null
}

const isBaseCandidate = entry => {
  const unified = String(entry.unified).toUpperCase()
  return !unified.includes('200D') &&
    !/1F3F[B-F]/.test(unified) &&
    entry.category !== 'Flags'
}

const buildTrendEmojiCatalog = () => {
  const rank = new Map(PRIORITY_HEX.map((hex, index) => [hex, index]))
  const candidates = emojiData
    .filter(isBaseCandidate)
    .map((entry, sourceIndex) => ({
      hex: normalizeHex(entry.unified),
      group: classify(entry),
      name: entry.name || entry.short_name,
      shortName: entry.short_name,
      sourceIndex
    }))
    .filter(item => item.group)
    .sort((left, right) => {
      const rankDiff = (rank.get(left.hex) ?? Number.MAX_SAFE_INTEGER) -
        (rank.get(right.hex) ?? Number.MAX_SAFE_INTEGER)
      return rankDiff || left.sourceIndex - right.sourceIndex
    })

  const selected = []
  const seen = new Set()
  for (const [group, quota] of Object.entries(CATEGORY_QUOTAS)) {
    let groupCount = 0
    for (const item of candidates) {
      if (item.group !== group || seen.has(item.hex)) continue
      selected.push(item)
      seen.add(item.hex)
      groupCount++
      if (groupCount === quota) break
    }
  }
  return selected
}

const toBlobFileName = item => `emoji_u${item.hex.toLowerCase().replace(/-/g, '_')}.svg`

module.exports = {
  CATEGORY_QUOTAS,
  CRITICAL_HEX,
  buildTrendEmojiCatalog,
  normalizeHex,
  toBlobFileName
}
