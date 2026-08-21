package com.qingsong.ai.service;

import com.qingsong.ai.entity.po.user.UserConfig;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 用户配置服务接口
 *
 * @author : caojiangjiang
 * @data : 2026/04/26
 */
public interface UserConfigService {

    /**
     * 根据ID查询用户配置
     *
     * @param id 用户ID
     * @return 用户配置信息
     */
    UserConfig getUserConfigById(Long id);

    /**
     * 根据用户名查询用户配置
     *
     * @param userName 用户姓名
     * @return 用户配置信息
     */
    UserConfig getUserConfigByUserName(String userName);

    /**
     * 查询所有用户配置
     *
     * @return 用户配置列表
     */
    List<UserConfig> getAllUserConfigs();

    /**
     * 创建用户配置
     *
     * @param userConfig 用户配置信息
     * @return 创建后的用户配置
     */
    UserConfig createUserConfig(UserConfig userConfig);

    /**
     * 更新用户配置
     *
     * @param userConfig 用户配置信息
     * @return 是否更新成功
     */
    boolean updateUserConfig(UserConfig userConfig);

    /**
     * 删除用户配置
     *
     * @param id 用户ID
     * @return 是否删除成功
     */
    boolean deleteUserConfig(Long id);

    /**
     * 批量删除用户配置
     *
     * @param ids 用户ID列表
     * @return 是否删除成功
     */
    boolean deleteUserConfigs(List<Long> ids);

    /**
     * 更新用户最后对话的role名称
     *
     * @param userId       用户ID
     * @param lastRoleName 最后对话的role名称
     * @return 是否更新成功
     */
    boolean updateLastRoleName(Long userId, String lastRoleName);

    UserConfig login(String account, String password);
}
