package com.qingsong.ai.service.impl.knowledge;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingsong.ai.entity.po.knowledge.KnowledgeBase;
import com.qingsong.ai.entity.vo.knowledge.KnowledgeBaseReqVO;
import com.qingsong.ai.entity.vo.knowledge.KnowledgeBaseRespVO;
import com.qingsong.ai.mapper.knowledge.DocumentMapper;
import com.qingsong.ai.mapper.knowledge.KnowledgeBaseMapper;
import com.qingsong.ai.service.knowledge.KnowledgeBaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase> implements KnowledgeBaseService {

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private com.qingsong.ai.service.knowledge.DocumentService documentService;

    @Override
    public List<KnowledgeBaseRespVO> getActiveKnowledgeBases(KnowledgeBaseReqVO reqVO) {
        Boolean active = reqVO.getActive() != null ? reqVO.getActive() : Boolean.TRUE;
        List<KnowledgeBase> list = this.lambdaQuery()
                .eq(KnowledgeBase::getActive, active)
                .orderByDesc(KnowledgeBase::getCreateDate)
                .list();

        Map<Long, Long> countMap = documentMapper.countGroupByKnowledgeId().stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row.get("knowledge_id")).longValue(),
                        row -> ((Number) row.get("cnt")).longValue()));

        List<KnowledgeBaseRespVO> voList = new ArrayList<>();
        list.forEach(knowledgeBase -> {
            KnowledgeBaseRespVO knowledgeBaseRespVO = new KnowledgeBaseRespVO();
            BeanUtils.copyProperties(knowledgeBase, knowledgeBaseRespVO);
            knowledgeBaseRespVO.setDocumentCount(countMap.getOrDefault(knowledgeBase.getId(), 0L));
            voList.add(knowledgeBaseRespVO);
        });

        return voList;
    }

    @Override
    public List<KnowledgeBase> searchByKeyword(String keyword) {
        log.info("根据关键词搜索知识库：{}", keyword);
        return this.lambdaQuery()
                .eq(KnowledgeBase::getActive, true)
                .and(wrapper -> wrapper
                        .like(KnowledgeBase::getName, keyword)
                        .or()
                        .like(KnowledgeBase::getDescription, keyword))
                .orderByDesc(KnowledgeBase::getCreateDate)
                .list();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchUpdateStatus(List<Long> ids, Boolean isActive) {
        log.info("批量更新知识库状态，ids: {}, isActive: {}", ids, isActive);
        KnowledgeBase updateEntity = new KnowledgeBase();
        updateEntity.setActive(isActive);
        updateEntity.setUpdateDate(LocalDateTime.now());

        return lambdaUpdate()
                .in(KnowledgeBase::getId, ids)
                .update(updateEntity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean logicDelete(Long id) {
        log.info("删除知识库：{}", id);
        // 级联清理该知识库下的全部文档（向量 + 原始文件 + 文档行），避免残留孤儿数据
        documentService.deleteByKnowledgeId(id);
        KnowledgeBase updateEntity = new KnowledgeBase();
        updateEntity.setId(id);
        updateEntity.setActive(false);
        updateEntity.setUpdateDate(LocalDateTime.now());
        return this.updateById(updateEntity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBase createKnowledgeBase(String name, String description) {
        log.info("创建知识库：{}", name);
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setName(name);
        knowledgeBase.setDescription(description);
        knowledgeBase.setActive(true);
        knowledgeBase.setCreateDate(LocalDateTime.now());
        knowledgeBase.setUpdateDate(LocalDateTime.now());

        this.save(knowledgeBase);
        log.info("知识库创建成功，ID: {}", knowledgeBase.getId());
        return knowledgeBase;
    }
}
