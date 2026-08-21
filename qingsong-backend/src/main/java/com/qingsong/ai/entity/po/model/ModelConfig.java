package com.qingsong.ai.entity.po.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 模型配置实体
 *
 * @author caojiangjiang
 * @version 1.0
 * @since 2026-01-26
 */
@Data
@TableName("model_config")
public class ModelConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 模型配置唯一标识符
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;


    /**
     * 模型名称
     */
    @TableField("name")
    private String name;

    /**
     * 模型值或配置信息
     */
    @TableField("code")
    private String code;

    /**
     * 模型类型，如大语言模型、嵌入模型等
     */
    @TableField("type")
    private String type;

    /**
     * 模型来源ID，关联model_source表
     */
    @TableField("model_source")
    private Long modelSource;

    /**
     * 模型排序顺序，数值越小优先级越高
     */
    @TableField("model_order")
    private Integer modelOrder;

    /**
     * 是否激活
     */
    @TableField("is_active")
    private Boolean isActive;

    /**
     * 创建人
     */
    @TableField("created_by")
    private String createdBy;

    /**
     * 更新人
     */
    @TableField("updated_by")
    private String updatedBy;

    /**
     * 创建时间
     */
    @TableField("create_date")
    private LocalDateTime createDate;

    /**
     * 更新时间
     */
    @TableField("update_date")
    private LocalDateTime updateDate;

    /**
     * 是否默认使用
     */
    @TableField("is_top")
    private Boolean isTop;
}
