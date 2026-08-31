// 会话列表分页相关纯函数

// Date/时间字符串 → 后端 @DateTimeFormat(ISO.DATE_TIME) 可解析的本地 ISO（yyyy-MM-ddTHH:mm:ss）
export const formatLocalDateTime = value => {
  // new Date(null) 会解析成 epoch 0 而非 NaN，必须显式拦截空值
  if (value == null || value === '') return null
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return null
  const pad = n => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

// 上一页末条 → 下一页游标；缺字段返回 null（视为无更多页）
export const historyCursorOf = item => {
  if (!item) return null
  const before = formatLocalDateTime(item.lastMessageAt ?? item.createdAt)
  if (!before || item.sessionDbId == null) return null
  return { before, beforeId: item.sessionDbId }
}

// 依次合并多个列表，按 String(id) 去重（Set，O(n)），保留先出现者
export const mergeHistoryUnique = (...lists) => {
  const seen = new Set()
  const merged = []
  for (const list of lists) {
    for (const item of list || []) {
      const id = String(item?.id ?? '')
      if (!id || seen.has(id)) continue
      seen.add(id)
      merged.push(item)
    }
  }
  return merged
}

// 选中日期（yyyy-MM-dd）→ 左闭右开范围参数（按天筛选 = [当日, 次日)）
export const dayRangeOf = dateKey => {
  if (!dateKey || !/^\d{4}-\d{2}-\d{2}$/.test(dateKey)) return { start: null, end: null }
  const [y, m, d] = dateKey.split('-').map(Number)
  const next = new Date(y, m - 1, d + 1)
  return { start: `${dateKey}T00:00:00`, end: `${formatLocalDateTime(next).slice(0, 10)}T00:00:00` }
}
