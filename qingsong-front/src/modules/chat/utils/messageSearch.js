// 摘要文本按关键词分段（大小写不敏感，与 MySQL utf8mb4 默认 collation 行为一致），供 <mark> 高亮渲染
export const highlightKeyword = (text, keyword) => {
  const source = String(text || '')
  const key = String(keyword || '').trim()
  if (!source) return []
  if (!key) return [{ text: source, mark: false }]
  const segments = []
  const lowerSource = source.toLowerCase()
  const lowerKey = key.toLowerCase()
  let cursor = 0
  while (cursor < source.length) {
    const hit = lowerSource.indexOf(lowerKey, cursor)
    if (hit === -1) {
      segments.push({ text: source.slice(cursor), mark: false })
      break
    }
    if (hit > cursor) segments.push({ text: source.slice(cursor, hit), mark: false })
    segments.push({ text: source.slice(hit, hit + key.length), mark: true })
    cursor = hit + key.length
  }
  return segments.filter(seg => seg.text)
}
