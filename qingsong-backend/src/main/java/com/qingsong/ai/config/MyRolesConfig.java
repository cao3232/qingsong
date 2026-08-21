package com.qingsong.ai.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qingsong.ai.entity.po.role.Role;
import com.qingsong.ai.mapper.role.RoleMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * description...
 *
 * @author : caojiangjiang
 * @data : 2025/05/04 22:16
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "my")
@Data
public class MyRolesConfig implements ApplicationListener<ApplicationReadyEvent> {

    @Autowired
    @Lazy
    private RoleMapper roleMapper;

    private Set<Role> allRoles;

    private Map<String, Role> roleMap;

    public Boolean init() {
        allRoles = new LinkedHashSet<>();
        allRoles.addAll(roleMapper.selectList(null));
        roleMap = allRoles.stream()
                .collect(Collectors.toMap(Role::getName, Function.identity()));
        return true;
    }

    public Boolean initRoleById(List<String> ids) {
        QueryWrapper<Role> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", ids);
        allRoles.addAll(roleMapper.selectList(queryWrapper));
        return true;
    }

    public Boolean removeRoleByRole(List<Role> roles) {
        allRoles.removeAll(roles);
        return true;
    }


    public void autoUpdateSort(Role currentRole) {
        Long sort = currentRole.getSort();
        if (Objects.isNull(sort)) {
            QueryWrapper<Role> queryWrapper = new QueryWrapper<>();
            queryWrapper.orderByDesc("sort");
            queryWrapper.eq("favor", currentRole.getFavor());
            queryWrapper.eq("id", currentRole.getId());
            Role role = roleMapper.selectOne(queryWrapper);
            currentRole.setSort(role.getSort() + 100);
            roleMapper.updateById(currentRole);
        }
        // 查找
        Role campareRole = allRoles.stream()
                .filter(r -> r.getFavor().equals(currentRole.getFavor()))
                .filter(r -> Objects.nonNull(r.getSort()))
                .filter(r -> r.getSort().compareTo(sort) > 0)
                .sorted(Comparator.comparing(Role::getSort))
                .findFirst()
                .get();
        if (campareRole == null) {
            log.info("没有找到比{}大的角色", sort);
            return;
        }
        currentRole.setSort(campareRole.getSort());
        currentRole.setUpdateDate(new Date());
        campareRole.setSort(sort);
        campareRole.setUpdateDate(new Date());
        roleMapper.updateById(currentRole);
        roleMapper.updateById(campareRole);

        initRoleById(List.of(currentRole.getId(), campareRole.getId()));
        log.info("成功交换 将{}与{}交换", currentRole.getName(), campareRole.getName());
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        this.init();
    }
}
