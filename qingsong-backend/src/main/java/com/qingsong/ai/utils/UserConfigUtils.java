package com.qingsong.ai.utils;

import com.qingsong.ai.context.UserContext;
import com.qingsong.ai.entity.po.user.UserConfig;
import com.qingsong.ai.service.UserConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 用户配置工具类
 * 提供便捷的方法获取当前用户配置信息
 *
 * @author : caojiangjiang
 * @data : 2026/04/26
 */
@Slf4j
@Component
public class UserConfigUtils {

    private static UserConfigService userConfigService;

    @Autowired
    public void setUserConfigService(UserConfigService userConfigService) {
        UserConfigUtils.userConfigService = userConfigService;
    }

    /**
     * 获取当前用户ID
     *
     * @return 用户ID，如果未设置则返回null
     */
    public static Long getCurrentUserId() {
        return UserContext.getCurrentUserId();
    }

    /**
     * 获取当前用户名
     *
     * @return 用户名，如果未设置则返回null
     */
    public static String getCurrentUserName() {
        return UserContext.getCurrentUserName();
    }

    /**
     * 获取当前用户配置（从上下文）
     * 注意：如果上下文中没有缓存配置，会返回null
     *
     * @return 用户配置，如果未设置则返回null
     */
    public static UserConfig getCurrentUserConfigFromContext() {
        return UserContext.getCurrentUserConfig();
    }

    /**
     * 获取当前用户配置（从数据库）
     * 如果上下文中已有配置则直接返回，否则查询数据库并缓存到上下文
     *
     * @return 用户配置，如果用户不存在则返回null
     */
    public static UserConfig getCurrentUserConfig() {
        // 1. 先从上下文获取
        UserConfig config = UserContext.getCurrentUserConfig();
        if (config != null) {
            log.debug("从上下文获取用户配置: userId={}", UserContext.getCurrentUserId());
            return config;
        }

        // 2. 从数据库查询
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            log.warn("用户上下文未设置，无法获取用户配置");
            return null;
        }

        try {
            config = userConfigService.getUserConfigById(userId);
            if (config != null) {
                // 缓存到上下文，避免重复查询
                UserContext.getContext().setUserConfig(config);
                log.debug("从数据库获取并缓存用户配置: userId={}", userId);
            }
            return config;
        } catch (Exception e) {
            log.error("获取用户配置失败: userId={}", userId, e);
            return null;
        }
    }

    /**
     * 获取当前用户的最后对话role名称
     *
     * @return 最后对话的role名称，如果不存在则返回null
     */
    public static String getCurrentLastRoleName() {
        UserConfig config = getCurrentUserConfig();
        return config != null ? config.getLastRoleName() : null;
    }

    /**
     * 获取当前用户的邮箱
     *
     * @return 用户邮箱，如果不存在则返回null
     */
    public static String getCurrentReceiveEmail() {
        UserConfig config = getCurrentUserConfig();
        return config != null ? config.getReceiveEmail() : null;
    }

    /**
     * 获取当前用户的状态
     *
     * @return 用户状态，如果不存在则返回null
     */
    public static String getCurrentStatus() {
        UserConfig config = getCurrentUserConfig();
        return config != null ? config.getStatus() : null;
    }

    /**
     * 更新当前用户的最后对话role名称
     *
     * @param lastRoleName 最后对话的role名称
     * @return 是否更新成功
     */
    public static boolean updateCurrentLastRoleName(String lastRoleName) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            log.warn("用户上下文未设置，无法更新最后对话role");
            return false;
        }

        try {
            boolean success = userConfigService.updateLastRoleName(userId, lastRoleName);
            if (success) {
                // 更新上下文中的缓存
                UserConfig config = UserContext.getCurrentUserConfig();
                if (config != null) {
                    config.setLastRoleName(lastRoleName);
                }
            }
            return success;
        } catch (Exception e) {
            log.error("更新用户最后对话role失败: userId={}, lastRoleName={}", userId, lastRoleName, e);
            return false;
        }
    }

    /**
     * 检查当前用户是否存在
     *
     * @return true-存在，false-不存在
     */
    public static boolean isCurrentUserExists() {
        return getCurrentUserConfig() != null;
    }

    /**
     * 检查当前用户是否激活
     *
     * @return true-激活，false-未激活或用户不存在
     */
    public static boolean isCurrentUserActive() {
        UserConfig config = getCurrentUserConfig();
        return config != null && "ACTIVE".equals(config.getStatus());
    }
}
