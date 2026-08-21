package com.qingsong.ai.service.impl.role;

import com.qingsong.ai.config.MyRolesConfig;
import com.qingsong.ai.constants.RedisConstants;
import com.qingsong.ai.entity.po.role.Role;
import com.qingsong.ai.entity.vo.RoleVO;
import com.qingsong.ai.mapper.role.RoleMapper;
import com.qingsong.ai.service.RoleService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * description...
 *
 * @author : caojiangjiang
 * @data : 2026/02/22 12:14
 */
@Service
public class RoleServiceImpl implements RoleService {

    @Autowired
    private MyRolesConfig myRolesConfig;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RoleMapper roleMapper;

    @Override
    public ArrayList<RoleVO> getAllRolesInfo() {
        Set<Role> allRoles = myRolesConfig.getAllRoles();
        ArrayList<RoleVO> list = new ArrayList<>(allRoles.size());
        // 获取redis中所有角色的会话
        allRoles.forEach(role -> {
            RoleVO roleVO = new RoleVO();
            BeanUtils.copyProperties(role, roleVO);
            String userHistoryKey = String.format(RedisConstants.USER_ROLE_HISTORY_KEY.getRedisKey(), "chat", role.getName());
            Long count = Optional.ofNullable(stringRedisTemplate.opsForHash().size(userHistoryKey)).orElse(0L);
            roleVO.setSessionCount(count);
            list.add(roleVO);
        });
        list.sort((o1, o2) -> o2.getSessionCount().compareTo(o1.getSessionCount()));
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRoles(List<String> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            throw new RuntimeException("请选择要删除的角色");
        }
        if (!Objects.equals(ids.size(), new HashSet<>(ids).size())) {
            throw new RuntimeException("请勿重复选择角色");
        }
        // 查找对应的Role信息
        List<Role> roles = roleMapper.selectByIds(ids);
        if (CollectionUtils.isEmpty(roles)) {
            throw new RuntimeException("没有找到对应的角色");
        }
        // 删除数据库
        roleMapper.deleteByIds(ids);
        // 删除redis 对话
        HashSet<String> keySet = new HashSet<>();
        for (Role role : roles) {
            String userHistoryKey = String.format(RedisConstants.USER_ROLE_HISTORY_KEY.getRedisKey(), "chat", role.getName());
            String chatMessgaeIds = String.format(RedisConstants.USER_ROLE_HISTORY_MESSAGE_KEY.getRedisKey(), "chat", role.getName());
            keySet.add(userHistoryKey);
            keySet.add(chatMessgaeIds);
        }
        stringRedisTemplate.delete(keySet);
        // 更新内存信息
        myRolesConfig.removeRoleByRole(roles);
    }

    @Override
    public Role getRoleInfo(String id) {
        return roleMapper.selectById(id);
    }


}
