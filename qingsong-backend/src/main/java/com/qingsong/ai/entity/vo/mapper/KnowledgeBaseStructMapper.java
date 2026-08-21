package com.qingsong.ai.entity.vo.mapper;

import com.qingsong.ai.entity.po.knowledge.KnowledgeBase;
import com.qingsong.ai.entity.vo.knowledge.KnowledgeBaseRespVO;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface KnowledgeBaseStructMapper {

    KnowledgeBaseRespVO toVO(KnowledgeBase entity);

    List<KnowledgeBaseRespVO> toVOList(List<KnowledgeBase> entities);
}
