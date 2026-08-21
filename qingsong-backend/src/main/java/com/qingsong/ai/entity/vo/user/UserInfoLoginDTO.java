package com.qingsong.ai.entity.vo.user;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * description...
 *
 * @author : caojiangjiang
 * @data : 2026/05/16 18:41
 */
@Data
public class UserInfoLoginDTO {

    @NotBlank(message = "账号不能为空")
    private String account;
    @NotBlank(message = "密码不能为空")
    private String password;
    // @NotBlank(message = "验证码不能为空")
    private String captcha;
}
