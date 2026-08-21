import http from '@/utils/http'

export const documentAPI = {
  async getDocuments(knowledgeId) {
    try {
      return await http.get(`/api/knowledge/documents/knowledge/${knowledgeId}`)
    } catch (error) {
      console.error('API Error:', error)
      return []
    }
  },

  async searchDocuments(knowledgeId, fileName) {
    try {
      return await http.get('/api/knowledge/documents/search', {
        params: { knowledgeId, fileName }
      })
    } catch (error) {
      console.error('API Error:', error)
      return []
    }
  },

  async addDocument(knowledgeId, fileName, path, sourceId = '') {
    return http.post('/api/knowledge/documents', null, {
      params: { knowledgeId, fileName, path, ...(sourceId ? { sourceId } : {}) }
    })
  },

  async markEmbedded(id) {
    return http.put(`/api/knowledge/documents/${id}/embedded`)
  },

  async deleteAllDocuments(knowledgeId) {
    return http.delete(`/api/knowledge/documents/knowledge/${knowledgeId}`)
  },

  async deleteDocument(knowledgeId, documentId) {
    try {
      const result = await http.delete(
        `/api/knowledge/documents/${knowledgeId}/${documentId}`
      )
      return result === true
    } catch (error) {
      console.error('删除文档失败:', error)
      return false
    }
  },

  async downloadDocument(documentId) {
    return http.get(`/api/knowledge/documents/${documentId}/download`, {
      responseType: 'blob'
    })
  },

  async uploadFile(knowledgeId, file) {
    try {
      const formData = new FormData()
      formData.append('file', file)
      const result = await http.post(`/api/knowledge/files/${knowledgeId}`, formData)
      return result === true
    } catch (error) {
      console.error('上传文件失败:', error)
      return false
    }
  },

  async getDocument(documentId) {
    try {
      return await http.get(`/api/knowledge/documents/${documentId}`)
    } catch (error) {
      console.error('获取文档信息失败:', error)
      return null
    }
  }
}
