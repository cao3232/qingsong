import http from '@/utils/http'

const unwrapData = (result) => {
  if (!result || result.ok !== 1) {
    throw new Error(result?.msg || '字典请求失败')
  }
  return result.data
}

// 业务字典接口封装
export const dictService = {
  // 下发全量启用字典 { version, items: { [code]: [{ key, label, extra, sort }] } }
  getAll: async () => unwrapData(await http.get('/api/dict/all')),

  // 下发单个字典类型的启用项
  getByCode: async (code) => unwrapData(await http.get(`/api/dict/${encodeURIComponent(code)}`)),

  // 管理端：分页查询
  page: async (params) => unwrapData(await http.get('/api/dict/admin/page', { params })),

  // 管理端：新增
  create: async (payload) => unwrapData(await http.post('/api/dict/admin', payload)),

  // 管理端：更新
  update: async (id, payload) => unwrapData(await http.put(`/api/dict/admin/${id}`, payload)),

  // 管理端：删除
  remove: async (id) => unwrapData(await http.delete(`/api/dict/admin/${id}`)),

  // 管理端：手动刷新缓存
  reload: async () => unwrapData(await http.post('/api/dict/admin/reload'))
}
