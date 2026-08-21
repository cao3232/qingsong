package com.qingsong.ai.controller.role;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.qingsong.ai.config.MyRolesConfig;
import com.qingsong.ai.entity.po.role.Role;
import com.qingsong.ai.entity.vo.Result;
import com.qingsong.ai.entity.vo.RoleVO;
import com.qingsong.ai.entity.vo.roles.RoleStatsVO;
import com.qingsong.ai.entity.vo.roles.SortRoleVO;
import com.qingsong.ai.mapper.role.RoleMapper;
import com.qingsong.ai.service.RoleService;
import com.qingsong.ai.service.RoleUsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


@RestController
@RequiredArgsConstructor
public class RolesController {
    private final MyRolesConfig myRolesConfig;
    private final RoleMapper roleMapper;
    private final RoleService roleService;
    private final RoleUsageService roleUsageService;

    @RequestMapping("/roles")
    public Result roles() {
        Set<Role> allRolesVO = new HashSet<>();
        Set<Role> allRoles = myRolesConfig.getAllRoles();

        allRoles.forEach(role -> {
            Role roleVO = role.builder()
                    .id(role.getId())
                    .name(role.getName())
                    .favor(role.getFavor())
                    .sort(role.getSort())
                    .createDate(role.getCreateDate())
                    .updateDate(role.getUpdateDate())
                    .description(role.getDescription())
                    .build();
            allRolesVO.add(roleVO);
        });

        return Result.ok(allRolesVO);
    }


    @PutMapping("/role/favor")
    public Result updateRoleFavor(@RequestBody Role role) {
        role.setUpdateDate(new Date());
        return roleMapper.updateById(role) == 1 ? Result.ok(myRolesConfig.initRoleById(Collections.singletonList(role.getId()))) : Result.fail("更新失败");
    }

    @PutMapping("/role/sort")
    public Result updateRoleSort(@RequestBody List<SortRoleVO> roles) {
        for (SortRoleVO role : roles) {
            role.setUpdateDate(new Date());
            UpdateWrapper<Role> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("id", role.getId())
                    .set("sort", role.getSort())
                    .set("update_date", new Date());
            roleMapper.update(updateWrapper);
        }
        return Result.ok(myRolesConfig.initRoleById(roles.stream().map(SortRoleVO::getId).distinct().toList()));
    }


    /**
     * 角色使用统计：总榜 + 今日榜（各取前 10，按使用次数降序）
     *
     * @return
     */
    @GetMapping("/role/stats")
    public Result<RoleStatsVO> roleStats() {
        return Result.ok(roleUsageService.getStats());
    }

    /**
     * 获取所有角色，包含对话信息（管理界面使用）
     *
     * @return
     */
    @GetMapping("/admin/roles/all")
    public Result<List<RoleVO>> getAllRoles() {
        return Result.ok(roleService.getAllRolesInfo());
    }

    /**
     * 获取单个角色信息（管理界面）
     */
    @GetMapping("/admin/roles/{id}")
    public Result<Role> getRole(@PathVariable String id) {
        return Result.ok(roleService.getRoleInfo(id));
    }

    /**
     * 批量删除角色，及其全部对话信息
     *
     * @return
     */
    @DeleteMapping("/admin/roles")
    public Result deleteRoles(@RequestBody List<String> ids) {
        roleService.deleteRoles(ids);
        return Result.ok();
    }

    /**
     * 编辑角色
     */
    @PutMapping("/admin/roles")
    public Result updateRole(@RequestBody Role role) {
        role.setUpdateDate(new Date());
        return roleMapper.updateById(role) == 1 ? Result.ok(myRolesConfig.initRoleById(Collections.singletonList(role.getId()))) : Result.fail("更新失败");
    }

    /**
     * 创建角色
     */
    @PostMapping("/admin/roles")
    public Result createRole(@RequestBody Role role) {
        role.setCreateDate(new Date());
        role.setUpdateDate(new Date());
        return roleMapper.insert(role) == 1 ? Result.ok(myRolesConfig.initRoleById(Collections.singletonList(role.getId()))) : Result.fail("创建失败");
    }


}
