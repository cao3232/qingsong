package com.qingsong.ai.entity.vo.user;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * description...
 *
 * @author : caojiangjiang
 * @data : 2026/05/16 17:07
 */
@Data
public class UserInfoRegistDTO {
    @NotBlank(message = "账号不能为空")
    private String account;
    @NotBlank(message = "密码不能为空")
    private String password;
    @NotBlank(message = "确认密码不能为空")
    private String rePassword;
}
