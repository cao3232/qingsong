package com.qingsong.ai.controller.model;

import com.qingsong.ai.entity.dto.model.ModelBatchAddRequest;
import com.qingsong.ai.entity.po.model.ModelConfig;
import com.qingsong.ai.entity.vo.ModelCheckResult;
import com.qingsong.ai.entity.vo.ModelDiscoverItem;
import com.qingsong.ai.entity.vo.Result;
import com.qingsong.ai.service.ModelConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 模型配置控制器
 *
 * @author caojiangjiang
 * @version 1.0
 * @since 2026-01-26
 */
@RestController
@RequestMapping("/api/model-configs")
public class ModelConfigController {

    private final ModelConfigService modelConfigService;

    @Autowired
    public ModelConfigController(ModelConfigService modelConfigService) {
        this.modelConfigService = modelConfigService;
    }


    /**
     * 创建模型配置
     *
     * @param modelConfig 模型配置
     * @return 创建后的模型配置
     */
    @PostMapping
    public Result<ModelConfig> createModelConfig(@Valid @RequestBody ModelConfig modelConfig) {
        modelConfig.setIsTop(false);
        boolean result = modelConfigService.save(modelConfig);
        if (result) {
            return Result.ok(modelConfig);
        } else {
            return Result.fail("创建失败");
        }
    }

    /**
     * 更新模型配置
     *
     * @param id          模型配置ID
     * @param modelConfig 模型配置
     * @return 更新后的模型配置
     */
    @PutMapping("/{id}")
    public Result<ModelConfig> updateModelConfig(@PathVariable Long id, @Valid @RequestBody ModelConfig modelConfig) {
        modelConfig.setId(id);
        return Result.ok(modelConfigService.updateModelConfig(modelConfig));
    }

    /**
     * 删除模型配置
     *
     * @param id 模型配置ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteModelConfig(@PathVariable Long id) {
        boolean result = modelConfigService.removeById(id);
        if (result) {
            return Result.ok();
        } else {
            return Result.fail("删除失败");
        }
    }


    /**
     * 获取所有活动的模型配置
     *
     * @return 活动的模型配置列表
     */
    @GetMapping("/active")
    public Result<List<ModelConfig>> getActiveModelConfigs() {
        List<ModelConfig> configs = modelConfigService.getActiveModelConfigs();
        return Result.ok(configs);
    }

    /**
     * 激活模型
     *
     * @param id 模型id
     */
    @PostMapping("/{id}/activate")
    public Result active(@PathVariable Long id) {
        modelConfigService.active(id);
        return Result.ok();
    }

    /**
     * 置顶模型
     */
    @PostMapping("/{id}/top")
    public Result top(@PathVariable Long id) {
        modelConfigService.top(id);
        return Result.ok();
    }

    /**
     * 根据模型来源获取活动的模型配置
     *
     * @param sourceId 模型来源ID
     * @return 活动的模型配置列表
     */
    @GetMapping("/source/{sourceId}")
    public Result<List<ModelConfig>> getModelConfigsBySource(@PathVariable Integer sourceId) {
        List<ModelConfig> configs = modelConfigService.getActiveModelConfigsBySource(sourceId);
        return Result.ok(configs);
    }


    /**
     * 测试模型可用性
     */
    @GetMapping("/test/{sourceId}")
    public Result<Boolean> sourceId(@PathVariable String sourceId) {
        boolean result = modelConfigService.testModel(sourceId);
        return Result.ok(result);
    }

    /**
     * 拉取来源下的模型列表（不做可用性检测）
     *
     * @param sourceId 模型来源ID
     * @return 模型列表（含归属、支持协议等元信息）
     */
    @GetMapping("/discover/{sourceId}")
    public Result<List<ModelDiscoverItem>> discoverModels(@PathVariable Long sourceId) {
        List<ModelDiscoverItem> results = modelConfigService.discoverModels(String.valueOf(sourceId));
        return Result.ok(results);
    }

    /**
     * 批量检测并添加模型配置
     *
     * @param request 批量添加请求（check=true 只添加检测通过的模型）
     * @return 每个模型的检测结果（含是否入库）
     */
    @PostMapping("/batch")
    public Result<List<ModelCheckResult>> batchAddModels(@Valid @RequestBody ModelBatchAddRequest request) {
        return Result.ok(modelConfigService.batchAddModels(request));
    }

    /**
     * 检测单个模型是否可用（不写库）
     *
     * @param sourceId 模型来源ID
     * @param modelId  模型ID
     * @return 检测结果
     */
    @PostMapping("/check")
    public Result<ModelCheckResult> checkModel(@RequestParam Long sourceId, @RequestParam String modelId) {
        return Result.ok(modelConfigService.checkModel(String.valueOf(sourceId), modelId));
    }

}
