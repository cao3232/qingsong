package com.qingsong.ai.controller.user;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import com.qingsong.ai.entity.po.user.UserConfig;
import com.qingsong.ai.entity.vo.Result;
import com.qingsong.ai.entity.vo.user.UserInfoLoginDTO;
import com.qingsong.ai.entity.vo.user.UserInfoRegistDTO;
import com.qingsong.ai.service.UserConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 用户配置控制器
 *
 * @author : caojiangjiang
 * @data : 2026/04/26
 */
@RestController
@RequestMapping("/user-config")
@RequiredArgsConstructor
public class UserConfigController {

    private final UserConfigService userConfigService;


    /**
     * 用户登录
     *
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody @Valid UserInfoLoginDTO userInfo) {
        UserConfig userConfig = userConfigService.login(userInfo.getAccount(), userInfo.getPassword());
        if (userConfig == null) {
            return Result.fail("用户不存在");
        }
        // userConfig 转为map
        Map<String, Object> response = BeanUtil.beanToMap(userConfig);
        System.out.println(StpUtil.getTokenInfo());
        response.put("token", StpUtil.getTokenInfo().getTokenValue());
        return Result.ok(response);
    }

    @PostMapping("/logout")
    public Result logout() {
        StpUtil.logout();
        return Result.ok();
    }

    @GetMapping("/session")
    public Result<Boolean> validateSession() {
        return Result.ok(StpUtil.isLogin());
    }

    /**
     * 用户注册
     *
     */
    @PostMapping("/register")
    public Result<UserConfig> register(@RequestBody @Valid UserInfoRegistDTO userInfo) {
        if (!StringUtils.pathEquals(userInfo.getRePassword(), userInfo.getPassword())) {
            return Result.fail("密码不一致");
        }
        UserConfig userConfig = new UserConfig();
        BeanUtils.copyProperties(userInfo, userConfig);
        return Result.ok(userConfigService.createUserConfig(userConfig));
    }

    /**
     * 根据ID查询用户配置
     *
     * @param id 用户ID
     * @return 用户配置信息
     */
    @GetMapping("/{id}")
    public Result<UserConfig> getUserConfigById(@PathVariable Long id) {
        UserConfig userConfig = userConfigService.getUserConfigById(id);
        return userConfig != null ? Result.ok(userConfig) : Result.fail("未找到用户配置");
    }

    /**
     * 根据用户名查询用户配置
     *
     * @param userName 用户姓名
     * @return 用户配置信息
     */
    @GetMapping("/by-name/{userName}")
    public Result<UserConfig> getUserConfigByUserName(@PathVariable String userName) {
        UserConfig userConfig = userConfigService.getUserConfigByUserName(userName);
        return userConfig != null ? Result.ok(userConfig) : Result.fail("未找到用户配置");
    }

    /**
     * 查询所有用户配置
     *
     * @return 用户配置列表
     */
    @GetMapping("/all")
    public Result<List<UserConfig>> getAllUserConfigs() {
        List<UserConfig> userConfigs = userConfigService.getAllUserConfigs();
        return Result.ok(userConfigs);
    }

    /**
     * 创建用户配置
     *
     * @param userConfig 用户配置信息
     * @return 创建后的用户配置
     */
    @PostMapping
    public Result<UserConfig> createUserConfig(@RequestBody UserConfig userConfig) {
        try {
            UserConfig created = userConfigService.createUserConfig(userConfig);
            return Result.ok(created);
        } catch (Exception e) {
            return Result.fail("创建失败: " + e.getMessage());
        }
    }

    /**
     * 更新用户配置
     *
     * @param userConfig 用户配置信息
     * @return 操作结果
     */
    @PutMapping
    public Result updateUserConfig(@RequestBody UserConfig userConfig) {
        try {
            boolean success = userConfigService.updateUserConfig(userConfig);
            return success ? Result.ok() : Result.fail("更新失败");
        } catch (Exception e) {
            return Result.fail("更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除用户配置
     *
     * @param id 用户ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public Result deleteUserConfig(@PathVariable Long id) {
        try {
            boolean success = userConfigService.deleteUserConfig(id);
            return success ? Result.ok() : Result.fail("删除失败");
        } catch (Exception e) {
            return Result.fail("删除失败: " + e.getMessage());
        }
    }

    /**
     * 批量删除用户配置
     *
     * @param ids 用户ID列表
     * @return 操作结果
     */
    @DeleteMapping("/batch")
    public Result deleteUserConfigs(@RequestBody List<Long> ids) {
        try {
            boolean success = userConfigService.deleteUserConfigs(ids);
            return success ? Result.ok() : Result.fail("批量删除失败");
        } catch (Exception e) {
            return Result.fail("批量删除失败: " + e.getMessage());
        }
    }

    /**
     * 更新用户最后对话的role名称
     *
     * @param userId       用户ID
     * @param lastRoleName 最后对话的role名称
     * @return 操作结果
     */
    @PutMapping("/{userId}/last-role")
    public Result updateLastRoleName(@PathVariable Long userId, @RequestBody String lastRoleName) {
        try {
            boolean success = userConfigService.updateLastRoleName(userId, lastRoleName);
            return success ? Result.ok() : Result.fail("更新失败");
        } catch (Exception e) {
            return Result.fail("更新失败: " + e.getMessage());
        }
    }
}
