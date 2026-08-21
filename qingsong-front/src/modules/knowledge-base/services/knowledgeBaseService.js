import http from '@/utils/http'

export const knowledgeAPI = {
  async getBases(active) {
    try {
      const params = {}
      if (active !== null && active !== undefined) {
        params.active = active
      }
      return await http.get('/api/knowledge/bases', { params })
    } catch (error) {
      console.error('API Error:', error)
      return []
    }
  },

  async searchBases(keyword) {
    try {
      return await http.get('/api/knowledge/bases/search', {
        params: { keyword }
      })
    } catch (error) {
      console.error('API Error:', error)
      return []
    }
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
