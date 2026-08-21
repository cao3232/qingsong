import { CHAT_API_BASE_URL } from './baseUrl.js'
import http from '@/utils/http'

export const roleAPI = {
  // 角色使用统计：总榜 + 今日榜（各取前 10，按使用次数降序）
  async getStats() {
    try {
      const responseData = await http.get(`${CHAT_API_BASE_URL}/role/stats`)

      if (responseData.ok === 0) {
        console.error('获取角色统计失败，服务器响应:', responseData)
        throw new Error(responseData.msg || '请求失败')
      }

      return responseData.data || { total: [], today: [] }
    } catch (error) {
      console.error('Failed to get role stats:', error)
      throw error
    }
  },

  // 收藏角色
  async favorRole(id, favor) {
    try {
      // 确保 ID 和 favor 都作为字符串处理
      const roleId = String(id)
      const favorValue = String(favor)

      const responseData = await http.put(`${CHAT_API_BASE_URL}/role/favor`, {
        id: roleId,
        favor: favorValue
      })
      
      // 检查响应状态
      if (responseData.ok === 0) {
        console.error('收藏角色失败，服务器响应:', responseData)
        throw new Error(responseData.msg || '请求失败')
      }

      return responseData
    } catch (error) {
      console.error('Failed to favor role:', error)
      throw error
    }
  },

  // 更新角色排序
  async updateRoleSort(roleId, sortValue) {
    try {
      // 确保 roleId 作为字符串处理
      const id = String(roleId)
      const sort = String(sortValue)

      // 临时模拟API调用，等待后端接口开发
      // 模拟网络延迟
      await new Promise(resolve => setTimeout(resolve, 300))
      
      // 模拟成功响应
      return { success: true, data: { id, sort } }
      
      // 实际API调用代码（等待后端开发完成后启用）
      // const response = await fetchAuth(`${API_BASE_URL}/roles/${roleId}`, {
      //   method: 'PUT',
      //   headers: {
      //     'Content-Type': 'application/json'
      //   },
      //   body: JSON.stringify({
      //     sort: sortValue
      //   })
      // })
      // return await response.json()
    } catch (error) {
      console.error('Failed to update role sort:', error)
      throw error
    }
  },

  // 批量更新角色排序
  async batchUpdateRoleSort(roleUpdates) {
    try {
      // 确保所有 ID 和排序值都作为字符串处理
      const processedUpdates = roleUpdates.map(update => ({
        id: String(update.id),
        sort: String(update.sort)
      }))
      
      // 临时模拟API调用，等待后端接口开发
      // 模拟网络延迟
      await new Promise(resolve => setTimeout(resolve, 300))
      
      // 模拟成功响应
      return { success: true, data: processedUpdates }
      
      // 实际API调用代码（等待后端开发完成后启用）
      // const response = await fetchAuth(`${API_BASE_URL}/roles/batch-sort`, {
      //   method: 'PUT',
      //   headers: {
      //     'Content-Type': 'application/json'
      //   },
      //   body: JSON.stringify({
      //     updates: roleUpdates
      //   })
      // })
      // return await response.json()
    } catch (error) {
      console.error('Failed to batch update role sort:', error)
      throw error
    }
  },

  // 更新角色顺序（拖拽排序）- 智能最小化更新
  async updateRoleOrder(newRoleIds, originalRoles) {
    try {
      // 计算需要更新的最小角色集合
      const updatesNeeded = this.calculateMinimalUpdates(newRoleIds, originalRoles)
      
      if (updatesNeeded.length === 0) {
        return { ok: 1, msg: '排序无变化' }
      }

      const responseData = await http.put(`${CHAT_API_BASE_URL}/role/sort`, updatesNeeded)
      
      // 检查响应状态
      if (responseData.ok === 0) {
        console.error('更新角色排序失败，服务器响应:', responseData)
        throw new Error(responseData.msg || '请求失败')
      }

      return responseData
    } catch (error) {
      console.error('Failed to update role order:', error)
      throw error
    }
  },

  // 计算最小化更新策略
  calculateMinimalUpdates(newRoleIds, originalRoles) {
    const updates = []
    
    // 创建原始排序映射，处理null值
    const originalSortMap = new Map()
    originalRoles.forEach(role => {
      const sortValue = role.value.sort
      // 处理null、undefined、空字符串等情况，统一转换为数字
      const numericSort = (sortValue === null || sortValue === undefined || sortValue === '') 
        ? 0 
        : parseInt(sortValue) || 0
      originalSortMap.set(String(role.value.id), numericSort)
    })
    
    // 使用间隔排序策略，为插入留出空间
    const SORT_INTERVAL = 100 // 每个角色之间间隔100，便于插入
    
    for (let i = 0; i < newRoleIds.length; i++) {
      const roleId = String(newRoleIds[i])
      const newSortValue = (i + 1) * SORT_INTERVAL
      const originalSortValue = originalSortMap.get(roleId) || 0
      
      // 只有当排序值真正改变时才添加到更新列表
      if (originalSortValue !== newSortValue) {
        updates.push({
          id: roleId,
          sort: String(newSortValue)
        })
      }
    }
    
    return updates
  },

  // 单个角色位置调整（更精确的排序）
  async updateSingleRolePosition(draggedRoleId, targetPosition, allRoles) {
    try {
      // 使用二分插入策略，只更新必要的角色
      const insertUpdates = this.calculateInsertionUpdates(draggedRoleId, targetPosition, allRoles)
      
      if (insertUpdates.length === 0) {
        return { ok: 1, msg: '位置无变化' }
      }

      const responseData = await http.put(`${CHAT_API_BASE_URL}/role/sort`, insertUpdates)
      
      if (responseData.ok === 0) {
        throw new Error(responseData.msg || '请求失败')
      }
      
      return responseData
    } catch (error) {
      console.error('Failed to update single role position:', error)
      throw error
    }
  },

  // 计算插入位置的最小更新 - 处理null值
  calculateInsertionUpdates(draggedRoleId, targetIndex, allRoles) {
    const updates = []
    const SORT_INTERVAL = 100
    
    // 安全地获取排序值，处理null、undefined等情况
    const getSafeSort = (role) => {
      if (!role || !role.value) return 0
      const sortValue = role.value.sort
      if (sortValue === null || sortValue === undefined || sortValue === '') return 0
      const parsed = parseInt(sortValue)
      return isNaN(parsed) ? 0 : parsed
    }
    
    // 找到目标位置前后的排序值
    const prevRole = targetIndex > 0 ? allRoles[targetIndex - 1] : null
    const nextRole = targetIndex < allRoles.length ? allRoles[targetIndex] : null
    
    const prevSort = getSafeSort(prevRole)
    let nextSort = getSafeSort(nextRole)
    
    // 如果nextSort为0或无效，使用默认计算
    if (nextSort === 0 && nextRole) {
      nextSort = (targetIndex + 1) * SORT_INTERVAL
    } else if (!nextRole) {
      nextSort = (targetIndex + 1) * SORT_INTERVAL
    }
    
    // 计算新的排序值
    let newSort
    if (nextSort - prevSort > 1) {
      // 有足够空间，插入中间值
      newSort = Math.floor((prevSort + nextSort) / 2)
      // 确保新排序值大于前一个
      if (newSort <= prevSort) {
        newSort = prevSort + SORT_INTERVAL
      }
    } else {
      // 空间不足，需要重新分配后续角色的排序值
      newSort = prevSort + SORT_INTERVAL
      
      // 更新后续角色的排序值，处理null值
      for (let i = targetIndex; i < allRoles.length; i++) {
        const role = allRoles[i]
        if (String(role.value.id) !== String(draggedRoleId)) {
          const newSortValue = newSort + (i - targetIndex + 1) * SORT_INTERVAL
          updates.push({
            id: String(role.value.id),
            sort: String(newSortValue)
          })
        }
      }
    }
    
    // 确保新排序值是有效的正整数
    if (newSort <= 0) {
      newSort = SORT_INTERVAL
    }
    
    // 添加被拖拽角色的新排序值
    updates.push({
      id: String(draggedRoleId),
      sort: String(newSort)
    })
    
    return updates
  }
}
