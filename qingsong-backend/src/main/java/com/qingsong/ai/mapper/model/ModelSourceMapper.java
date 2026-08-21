package com.qingsong.ai.mapper.model;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingsong.ai.entity.po.model.ModelSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 模型来源数据访问接口
 *
 * @author caojiangjiang
 * @version 1.0
 * @since 2026-02-16
 */
@Mapper
@Repository
public interface ModelSourceMapper extends BaseMapper<ModelSource> {

    /**
     * 根据来源编码查找模型来源
     *
     * @param sourceCode 来源编码
     * @return 模型来源
     */
    ModelSource findBySourceCode(@Param("sourceCode") String sourceCode);

    /**
     * 查找所有启用的模型来源
     *
     * @return 模型来源列表
     */
    List<ModelSource> findByIsActiveTrue();

    /**
     * 根据排序查找启用的模型来源
     *
     * @return 模型来源列表（按排序升序）
     */
    List<ModelSource> findByIsActiveTrueOrderBySortOrderAsc();

    /**
     * 统计启用的模型来源数量
     *
     * @return 数量
     */
    Long countByIsActiveTrue();
}
