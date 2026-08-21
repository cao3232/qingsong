import { CHAT_API_BASE_URL } from './baseUrl.js'
import http from '@/utils/http'

export const chatKnowledgeAPI = {
  async getBases(active) {
    try {
      const params = new URLSearchParams()
      if (active !== null && active !== undefined) {
        params.append('active', active)
      }

      const query = params.toString()
      return await http.get(
        `${CHAT_API_BASE_URL}/api/knowledge/bases${query ? `?${query}` : ''}`
      )
    } catch (error) {
      console.error('Chat Knowledge API Error:', error)
      return []
    }
  }
}
