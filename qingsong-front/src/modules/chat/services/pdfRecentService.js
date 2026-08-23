// 最近阅读的 PDF 持久化（IndexedDB）
// 记录包含文件本体（Blob），点击即可重新打开，无需再次选择文件
const DB_NAME = 'qingsong-pdf-reader'
const STORE = 'recent'
const DB_VERSION = 1

// 最近记录上限（文件体积可能较大，控制存储占用）
export const PDF_RECENT_MAX = 8

const openDb = () => new Promise((resolve, reject) => {
  if (typeof indexedDB === 'undefined') return reject(new Error('IndexedDB unavailable'))
  const request = indexedDB.open(DB_NAME, DB_VERSION)
  request.onupgradeneeded = () => {
    const db = request.result
    if (!db.objectStoreNames.contains(STORE)) db.createObjectStore(STORE, { keyPath: 'id' })
  }
  request.onsuccess = () => resolve(request.result)
  request.onerror = () => reject(request.error)
})

// 在 store 上执行一次操作，等待事务完成后返回操作结果
const storeOp = async (mode, fn) => {
  const db = await openDb()
  try {
    return await new Promise((resolve, reject) => {
      const transaction = db.transaction(STORE, mode)
      const store = transaction.objectStore(STORE)
      let request
      try {
        request = fn(store)
      } catch (error) {
        reject(error)
        return
      }
      // oncomplete 时 request.result 已就绪；getAll 返回数组，put/delete 返回 undefined
      transaction.oncomplete = () => resolve(request?.result)
      transaction.onerror = () => reject(transaction.error)
      transaction.onabort = () => reject(transaction.error)
    })
  } finally {
    db.close()
  }
}

// 记录 id：文件名 + 大小，同名同大小视为同一份文件
const recordId = file => `${file.name}_${file.size}`

// 读取最近阅读列表（按最近打开时间倒序）
export const listRecentPdfs = async () => {
  try {
    const rows = await storeOp('readonly', store => store.getAll())
    return rows
      .filter(row => row?.blob)
      .sort((a, b) => (b.lastOpened || 0) - (a.lastOpened || 0))
  } catch {
    return []
  }
}

// 保存/刷新一条最近阅读（打开成功时调用）
export const saveRecentPdf = async file => {
  if (!file || typeof indexedDB === 'undefined') return
  try {
    await storeOp('readwrite', store =>
      store.put({
        id: recordId(file),
        name: file.name,
        size: file.size,
        lastOpened: Date.now(),
        blob: file
      })
    )
    await pruneRecentPdfs()
  } catch {
    // 存储失败不阻塞阅读
  }
}

// 移除单条最近阅读
export const removeRecentPdf = async id => {
  try {
    await storeOp('readwrite', store => store.delete(id))
  } catch {
    // 忽略
  }
}

// 清空所有最近阅读
export const clearRecentPdfs = async () => {
  try {
    await storeOp('readwrite', store => store.clear())
  } catch {
    // 忽略
  }
}

// 超出上限时淘汰最早打开的记录
const pruneRecentPdfs = async () => {
  const rows = await listRecentPdfs()
  const overflow = rows.slice(PDF_RECENT_MAX)
  for (const row of overflow) await removeRecentPdf(row.id)
}
