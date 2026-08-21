package com.qingsong.ai.entity.vo;

import lombok.Data;

import java.util.List;

/**
 * 来源 /models 接口返回的模型条目
 *
 * @author caojiangjiang
 * @version 1.0
 * @since 2026-08-20
 */
@Data
public class ModelDiscoverItem {

    /**
     * 模型ID
     */
    private String model;

    /**
     * 归属方（owned_by）
     */
    private String ownedBy;

    /**
     * 支持的协议类型（supported_endpoint_types，如 anthropic/openai）
     */
    private List<String> supportedEndpointTypes;
}
