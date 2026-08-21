import { CHAT_API_BASE_URL } from './baseUrl.js'
import http from '@/utils/http'
const phrasesCache = new Map()

export const rolePhrasesAPI = {
  // 获取角色短语列表
  async getRolePhrases(roleId) {
    if (phrasesCache.has(roleId)) {
      return phrasesCache.get(roleId)
    }

    try {
      const result = await http.get(`${CHAT_API_BASE_URL}/role-phrases/${roleId}`)
      if (result.ok === 1) {
        phrasesCache.set(roleId, result.data) // 只缓存 data 部分
        return result.data
      } else {
        throw new Error(result.msg || 'Failed to fetch phrases')
      }
    } catch (error) {
      console.error('API Error:', error)
      throw error
    }
  },

  // 添加角色短语
  async addRolePhrase(rolePhrase) {
    try {
      const result = await http.post(`${CHAT_API_BASE_URL}/role-phrases`, rolePhrase)

      // 操作成功后，使缓存失效
      if (result.ok === 1 && rolePhrase.roleId) {
        phrasesCache.delete(rolePhrase.roleId)
      }
      return result
    } catch (error) {
      console.error('API Error:', error)
      throw error
    }
  },

  // 更新角色短语
  async updateRolePhrase(rolePhrase) {
    try {
      const result = await http.put(`${CHAT_API_BASE_URL}/role-phrases`, rolePhrase)

      // 操作成功后，使缓存失效
      if (result.ok === 1 && rolePhrase.roleId) {
        phrasesCache.delete(rolePhrase.roleId)
      }
      return result
    } catch (error) {
      console.error('API Error:', error)
      throw error
    }
  },

  // 删除角色短语
  async deleteRolePhrase(id, roleId) {
    try {
      const result = await http.delete(`${CHAT_API_BASE_URL}/role-phrases/${id}`)

      // 操作成功后，使缓存失效
      if (result.ok === 1 && roleId) {
        phrasesCache.delete(roleId)
      }
      return result
    } catch (error) {
      console.error('API Error:', error)
      throw error
    }
  }
}
