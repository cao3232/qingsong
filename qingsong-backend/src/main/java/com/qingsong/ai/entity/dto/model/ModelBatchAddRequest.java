package com.qingsong.ai.entity.dto.model;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 批量添加模型请求
 *
 * @author caojiangjiang
 * @version 1.0
 * @since 2026-08-20
 */
@Data
public class ModelBatchAddRequest {

    /**
     * 模型来源ID
     */
    @NotNull
    private Long sourceId;

    /**
     * 要添加的模型ID列表（去重，已存在的不重复添加）
     */
    @NotEmpty
    private List<String> modelIds;

    /**
     * 是否在添加前检测可用性（true：只添加检测通过的模型；false：跳过检测直接入库），默认 true
     */
    private Boolean check = true;
}
