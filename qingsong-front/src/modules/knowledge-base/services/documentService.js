import http from '@/utils/http'

export const documentAPI = {
  async getDocuments(knowledgeId, pageNum = 1, pageSize = 10, embedding, fileType) {
    const params = { pageNum, pageSize }
    if (embedding !== null && embedding !== undefined) params.embedding = embedding
    if (fileType) params.fileType = fileType
    return http.get(`/api/knowledge/documents/knowledge/${knowledgeId}`, { params })
  },

  async searchDocuments(knowledgeId, fileName, pageNum = 1, pageSize = 10, embedding, fileType) {
    const params = { knowledgeId, fileName, pageNum, pageSize }
    if (embedding !== null && embedding !== undefined) params.embedding = embedding
    if (fileType) params.fileType = fileType
    return http.get('/api/knowledge/documents/search', { params })
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

  async reEmbedDocument(documentId) {
    return http.post(`/api/knowledge/documents/${documentId}/reembed`)
  },

  async reEmbedPending(knowledgeId) {
    return http.post(`/api/knowledge/documents/knowledge/${knowledgeId}/reembed-pending`)
  },

  async downloadDocument(documentId) {
    return http.get(`/api/knowledge/documents/${documentId}/download`, {
      responseType: 'blob'
    })
  },

  async uploadFile(knowledgeId, file) {
    const formData = new FormData()
    formData.append('file', file)
    return http.post(`/api/knowledge/files/${knowledgeId}`, formData)
  }
}
