package com.qingsong.ai.entity.vo;

import lombok.Data;

/**
 * 模型可用性检测结果
 *
 * @author caojiangjiang
 * @version 1.0
 * @since 2026-08-20
 */
@Data
public class ModelCheckResult {

    /**
     * 模型ID
     */
    private String model;

    /**
     * 是否可用
     */
    private Boolean working;

    /**
     * HTTP状态码（0 表示网络错误/超时）
     */
    private Integer httpStatus;

    /**
     * 备注/失败原因
     */
    private String note;

    /**
     * 是否已入库（批量添加时有效；false 可能因检测失败或已存在）
     */
    private Boolean added;
}
