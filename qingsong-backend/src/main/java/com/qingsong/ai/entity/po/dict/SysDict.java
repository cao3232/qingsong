package com.qingsong.ai.entity.po.dict;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 业务字典表
 */
@Data
@TableName("sys_dict")
public class SysDict implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 字典类型编码，如 chat_model
     */
    @TableField("dict_code")
    private String dictCode;

    /**
     * 字典项值，前端表单存这个
     */
    @TableField("item_key")
    private String itemKey;

    /**
     * 展示文案
     */
    @TableField("item_label")
    private String itemLabel;

    /**
     * 附加JSON(可选)
     */
    @TableField("item_extra")
    private String itemExtra;

    /**
     * 排序
     */
    @TableField("sort")
    private Integer sort;

    /**
     * 1启用/0停用
     */
    @TableField("status")
    private Integer status;

    /**
     * 备注
     */
    @TableField("remark")
    private String remark;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
