package com.qingsong.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingsong.ai.entity.dto.model.ModelBatchAddRequest;
import com.qingsong.ai.entity.po.model.ModelConfig;
import com.qingsong.ai.entity.vo.ModelCheckResult;
import com.qingsong.ai.entity.vo.ModelDiscoverItem;

import java.util.List;

/**
 * 模型配置服务接口
 *
 * @author caojiangjiang
 * @version 1.0
 * @since 2026-01-26
 */
public interface ModelConfigService extends IService<ModelConfig> {

    /**
     * 获取所有活动的模型配置
     *
     * @return 活动的模型配置列表
     */
    List<ModelConfig> getActiveModelConfigs();

    /**
     * 根据模型来源获取活动的模型配置
     *
     * @param modelSource 模型来源ID
     * @return 活动的模型配置列表
     */
    List<ModelConfig> getActiveModelConfigsBySource(Integer modelSource);

    void active(Long id);

    void top(Long id);

    boolean testModel(String sourceId);

    /**
     * 拉取来源下的模型列表（不做可用性检测）
     * <p>
     * 从来源的 /models 接口拉取全部模型，返回模型ID及元信息（归属、支持协议）。
     * 检测放到批量添加时按选中项逐个进行，避免全量检测耗时/触发限流。
     *
     * @param sourceId 模型来源ID
     * @return 模型列表（含元信息）
     */
    List<ModelDiscoverItem> discoverModels(String sourceId);

    /**
     * 批量检测并添加模型配置
     * <p>
     * 对选中的每个模型做可用性检测，可用的按 model_source + code 去重后入库。
     *
     * @param request 批量添加请求
     * @return 每个模型的检测结果（含是否入库）
     */
    List<ModelCheckResult> batchAddModels(ModelBatchAddRequest request);

    /**
     * 检测单个模型是否可用（不写库）
     *
     * @param sourceId 模型来源ID
     * @param modelId  模型ID
     * @return 检测结果
     */
    ModelCheckResult checkModel(String sourceId, String modelId);

    ModelConfig updateModelConfig(ModelConfig modelConfig);

    String getInnerModel();
}
