package com.qingsong.ai.service;

import com.qingsong.ai.entity.po.role.Role;
import com.qingsong.ai.entity.vo.RoleVO;

import java.util.ArrayList;
import java.util.List;

public interface RoleService {
    ArrayList<RoleVO> getAllRolesInfo();

    void deleteRoles(List<String> ids);

    Role getRoleInfo(String id);
}
