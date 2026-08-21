package com.qingsong.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingsong.ai.entity.po.model.ModelSource;
import com.qingsong.ai.entity.vo.ModelSourceInfoVo;

import java.util.List;

/**
 * 模型来源服务接口
 *
 * @author caojiangjiang
 * @version 1.0
 * @since 2026-02-16
 */
public interface ModelSourceService extends IService<ModelSource> {

    /**
     * 根据来源编码获取模型来源
     *
     * @param sourceCode 来源编码
     * @return 模型来源
     */
    ModelSource getBySourceCode(String sourceCode);

    /**
     * 获取所有启用的模型来源
     *
     * @return 启用的模型来源列表
     */
    List<ModelSource> getActiveModelSources();

    /**
     * 获取所有启用的模型来源（按排序升序）
     *
     * @return 启用的模型来源列表
     */
    List<ModelSource> getActiveModelSourcesOrderBySort();

    /**
     * 统计启用的模型来源数量
     *
     * @return 数量
     */
    Long countActiveModelSources();

    /**
     * 启用/禁用模型来源
     *
     * @param id 模型来源ID
     */
    void toggleActive(Long id);

    List<ModelSourceInfoVo> getAllModelSourceInfo();

    ModelSource getCurrentModelSource();

    ModelSource updateModelSource(ModelSource modelSource);

    boolean removeSourceById(Long id);
}
