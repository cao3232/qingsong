package com.qingsong.ai.entity.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通用信息展示VO类
 * 用于向前端返回各种信息数据
 *
 * @author qingsong Team
 * @since 2026-02-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelSourceInfoVo {

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

    /**
     * 总数量
     */
    private Long count;

    /**
     * 扩展字段2
     */
    private String maskApiKey;

    /**
     * 扩展字段3
     */
    private String apiKey;

}
