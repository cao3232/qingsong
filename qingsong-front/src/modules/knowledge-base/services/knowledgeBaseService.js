import http from '@/utils/http'

export const knowledgeAPI = {
  async getBases(active) {
    const params = {}
    if (active !== null && active !== undefined) {
      params.active = active
    }
    return http.get('/api/knowledge/bases', { params })
  },

  async searchBases(keyword) {
    return http.get('/api/knowledge/bases/search', {
      params: { keyword }
    })
  },

  async createBase(name, description = '') {
    return http.post('/api/knowledge/bases', null, {
      params: { name, ...(description ? { description } : {}) }
    })
  },

  async updateBase(knowledgeBase) {
    return http.put('/api/knowledge/bases', knowledgeBase)
  },

  async updateStatus(ids, isActive) {
    return http.put(`/api/knowledge/bases/status?isActive=${isActive}`, ids)
  },

  async deleteBase(id) {
    return http.delete(`/api/knowledge/bases/${id}`)
  }
}
