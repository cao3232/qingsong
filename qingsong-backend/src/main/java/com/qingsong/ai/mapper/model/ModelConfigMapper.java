package com.qingsong.ai.mapper.model;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingsong.ai.entity.po.model.ModelConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 模型配置数据访问接口
 *
 * @author caojiangjiang
 * @version 1.0
 * @since 2026-01-26
 */
@Mapper
@Repository
public interface ModelConfigMapper extends BaseMapper<ModelConfig> {

    /**
     * 根据模型ID查找模型配置
     *
     * @param modelId 模型ID
     * @return 模型配置
     */
    ModelConfig findByModelId(@Param("modelId") String modelId);

    /**
     * 根据模型类型查找所有活动的模型配置
     *
     * @param type 模型类型
     * @return 模型配置列表
     */
    List<ModelConfig> findByTypeAndIsActiveTrue(@Param("type") String type);

    /**
     * 根据模型来源查找所有活动的模型配置
     *
     * @param modelSource 模型来源ID
     * @return 模型配置列表
     */
    List<ModelConfig> findBySource(@Param("source") Integer modelSource);

    /**
     * 查找所有活动的模型配置
     *
     * @return 模型配置列表
     */
    List<ModelConfig> findByIsActiveTrue();

    /**
     * 根据模型类型统计活动的模型数量
     *
     * @param type 模型类型
     * @return 数量
     */
    Long countByTypeAndIsActiveTrue(@Param("type") String type);

    List<ModelConfig> selectChangeTopList(Long id);
}
