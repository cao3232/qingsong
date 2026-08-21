package com.qingsong.ai.entity.po.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 模型来源实体
 *
 * @author caojiangjiang
 * @version 1.0
 * @since 2026-02-16
 */
@Data
@TableName("model_source")
public class ModelSource implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 来源唯一标识符
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 来源编码，如 OPENAI、AZURE、LOCAL
     */
    @TableField("source_code")
    private String sourceCode;

    /**
     * 来源名称
     */
    @TableField("source_name")
    private String sourceName;

    /**
     * API基础地址
     */
    @TableField("api_base_url")
    private String apiBaseUrl;


    /**
     * API密钥
     */
    @TableField("api_key")
    private String apiKey;


    /**
     * 是否启用该来源
     */
    @TableField("is_active")
    private Boolean isActive;

    /**
     * 来源排序，数值越小越靠前
     */
    @TableField("sort_order")
    private Integer sortOrder;

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
}
