package com.qingsong.ai.entity.po.user;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 用户配置表实体类
 *
 * @author : caojiangjiang
 * @data : 2026/04/26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_config")
public class UserConfig {

    /**
     * 用户ID（主键）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户姓名
     */
    private String userName;

    /**
     * 账号
     */
    private String account;

    /**
     * 用户密码
     */
    private String password;

    /**
     * 用户邮箱
     */
    private String receiveEmail;

    /**
     * 状态（默认：ACTIVE）
     */
    private String status;

    /**
     * 最后对话的role名称
     */
    private String lastRoleName;

    /**
     * 创建日期
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;

    /**
     * 更新日期
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;
}
