package com.qingsong.ai.service;


import com.qingsong.ai.entity.po.role.RolePhrases;

import java.util.List;

public interface RolePhrasesService {
    List<RolePhrases> getRolePhrasesByRoleId(String roleId);

    /**
     * 保存角色短语
     */
    boolean saveRolePhrase(RolePhrases rolePhrase);

    /**
     * 更新角色短语
     */
    boolean updateRolePhrase(RolePhrases rolePhrase);

    /**
     * 删除角色短语
     */
    boolean deleteRolePhrase(Long id);
}
