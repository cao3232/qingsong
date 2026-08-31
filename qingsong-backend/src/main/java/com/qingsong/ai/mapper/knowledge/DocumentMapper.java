package com.qingsong.ai.mapper.knowledge;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingsong.ai.entity.po.knowledge.DocumentBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 文档 Mapper 接口
 *
 * @author AI Architect
 * @since 2026-03-20
 */
@Mapper
public interface DocumentMapper extends BaseMapper<DocumentBase> {

    /**
     * 按知识库分组统计文档数量（一次性聚合，避免 N+1）
     *
     * @return 每行 {knowledge_id, cnt}
     */
    @Select("SELECT knowledge_id, COUNT(*) AS cnt FROM document GROUP BY knowledge_id")
    List<Map<String, Object>> countGroupByKnowledgeId();
}
