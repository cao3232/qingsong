package com.qingsong.ai.entity.vo;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.Objects;

/**
 * description...
 *
 * @author : caojiangjiang
 * @data : 2025/05/05 02:27
 */
@Data
public class RoleVO {
    private String id;
    private String name;
    private String favor;
    private Long sort;
    private Date createDate;
    private Date updateDate;
    private String description;

    /**
     * 会话数量
     */
    private Long sessionCount;
}
