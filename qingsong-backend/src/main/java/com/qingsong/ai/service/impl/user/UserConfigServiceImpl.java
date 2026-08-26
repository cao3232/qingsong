package com.qingsong.ai.service.impl.user;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingsong.ai.entity.po.user.UserConfig;
import com.qingsong.ai.mapper.user.UserConfigMapper;
import com.qingsong.ai.service.UserConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.thymeleaf.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 用户配置服务实现类
 *
 * @author : caojiangjiang
 * @data : 2026/04/26
 */
@Service
@RequiredArgsConstructor
public class UserConfigServiceImpl implements UserConfigService {

    private final UserConfigMapper userConfigMapper;

    @Override
    public UserConfig getUserConfigById(Long id) {
        return userConfigMapper.selectById(id);
    }

    @Override
    public UserConfig getUserConfigByUserName(String userName) {
        LambdaQueryWrapper<UserConfig> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserConfig::getUserName, userName);
        return userConfigMapper.selectOne(queryWrapper);
    }

    @Override
    public List<UserConfig> getAllUserConfigs() {
        return userConfigMapper.selectList(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserConfig createUserConfig(UserConfig userConfig) {

        Long existCount = userConfigMapper.selectCount(new LambdaQueryWrapper<UserConfig>()
                .eq(UserConfig::getAccount, userConfig.getAccount()));
        if (existCount > 0) {
            throw new RuntimeException("用户账号已存在");
        }
        // 设置默认值
        if (userConfig.getStatus() == null || userConfig.getStatus().isEmpty()) {
            userConfig.setStatus("ACTIVE");
        }

        userConfig.setUserName(userConfig.getAccount() + "_"+ RandomUtil.randomString(4));

        userConfig.setCreatedAt(new Date());
        userConfig.setUpdatedAt(new Date());

        userConfigMapper.insert(userConfig);
        return userConfig;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateUserConfig(UserConfig userConfig) {
        if (userConfig.getId() == null) {
            throw new RuntimeException("用户ID不能为空");
        }

        userConfig.setUpdatedAt(new Date());
        return userConfigMapper.updateById(userConfig) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteUserConfig(Long id) {
        if (id == null) {
            throw new RuntimeException("用户ID不能为空");
        }
        return userConfigMapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteUserConfigs(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            throw new RuntimeException("请选择要删除的用户配置");
        }

        // 检查是否有重复ID
        if (!Objects.equals(ids.size(), ids.stream().distinct().count())) {
            throw new RuntimeException("请勿重复选择用户配置");
        }

        return userConfigMapper.deleteBatchIds(ids) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateLastRoleName(Long userId, String lastRoleName) {
        if (userId == null) {
            throw new RuntimeException("用户ID不能为空");
        }

        UserConfig userConfig = new UserConfig();
        userConfig.setId(userId);
        userConfig.setLastRoleName(lastRoleName);
        userConfig.setUpdatedAt(new Date());

        return userConfigMapper.updateById(userConfig) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateContextSize(Long userId, Integer contextSize) {
        if (userId == null) {
            throw new RuntimeException("用户ID不能为空");
        }

        UserConfig userConfig = new UserConfig();
        userConfig.setId(userId);
        userConfig.setContextSize(contextSize);
        userConfig.setUpdatedAt(new Date());

        return userConfigMapper.updateById(userConfig) > 0;
    }

    @Override
    public UserConfig login(String account, String userPassword) {
        // 根据用户账号去查询
        UserConfig userConfig = userConfigMapper.selectOne(new LambdaQueryWrapper<UserConfig>()
                .eq(UserConfig::getAccount, account));
        if (userConfig == null) {
            return null;
        }
        String password = userConfig.getPassword();
        if (StringUtils.equals(password, userPassword)) {
            StpUtil.login(userConfig.getId());
            return userConfig;
        }
        return null;
    }
}
