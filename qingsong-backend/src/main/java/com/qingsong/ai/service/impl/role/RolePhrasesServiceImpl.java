package com.qingsong.ai.service.impl.role;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qingsong.ai.entity.po.role.RolePhrases;
import com.qingsong.ai.mapper.role.RolePhrasesMapper;
import com.qingsong.ai.service.RolePhrasesService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class RolePhrasesServiceImpl implements RolePhrasesService {
    private final RolePhrasesMapper rolePhrasesMapper;


    @Override
    public List<RolePhrases> getRolePhrasesByRoleId(String roleId) {
        QueryWrapper<RolePhrases> rolePhrasesQueryWrapper = new QueryWrapper<>();
        rolePhrasesQueryWrapper.eq("role_id", roleId);
        return rolePhrasesMapper.selectList(rolePhrasesQueryWrapper);
    }

    @Override
    public boolean saveRolePhrase(RolePhrases rolePhrase) {
        return rolePhrasesMapper.insert(rolePhrase) > 0;
    }

    @Override
    public boolean updateRolePhrase(RolePhrases rolePhrase) {
        return rolePhrasesMapper.updateById(rolePhrase) > 0;
    }

    @Override
    public boolean deleteRolePhrase(Long id) {
        return rolePhrasesMapper.deleteById(id) > 0;
    }
}
