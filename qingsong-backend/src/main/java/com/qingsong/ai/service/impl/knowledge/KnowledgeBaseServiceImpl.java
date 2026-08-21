package com.qingsong.ai.service.impl.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingsong.ai.entity.po.knowledge.DocumentBase;
import com.qingsong.ai.entity.po.knowledge.KnowledgeBase;
import com.qingsong.ai.entity.vo.knowledge.KnowledgeBaseReqVO;
import com.qingsong.ai.entity.vo.knowledge.KnowledgeBaseRespVO;
import com.qingsong.ai.entity.vo.mapper.KnowledgeBaseStructMapper;
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

@Slf4j
@Service
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase> implements KnowledgeBaseService {

    @Autowired
    private DocumentMapper documentMapper;

    @Override
    public List<KnowledgeBaseRespVO> getActiveKnowledgeBases(KnowledgeBaseReqVO reqVO) {
        List<KnowledgeBase> list = this.lambdaQuery()
                .eq(false, KnowledgeBase::getActive, reqVO.getActive())
                .orderByDesc(KnowledgeBase::getCreateDate)
                .list();
        List<KnowledgeBaseRespVO> voList = new ArrayList<>();
        list.forEach(knowledgeBase -> {
            LambdaQueryWrapper<DocumentBase> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(DocumentBase::getKnowledgeId, knowledgeBase.getId());
            Long count = documentMapper.selectCount(queryWrapper);
            KnowledgeBaseRespVO knowledgeBaseRespVO = new KnowledgeBaseRespVO();
            knowledgeBaseRespVO.setDocumentCount(count);
            BeanUtils.copyProperties(knowledgeBase, knowledgeBaseRespVO);
            voList.add(knowledgeBaseRespVO);
        });

        return voList;
    }

    @Override
    public List<KnowledgeBase> searchByKeyword(String keyword) {
        log.info("根据关键词搜索知识库：{}", keyword);
        return this.lambdaQuery()
                .like(KnowledgeBase::getName, keyword)
                .or()
                .like(KnowledgeBase::getDescription, keyword)
                .orderByDesc(KnowledgeBase::getCreateDate)
                .list();
    }

    @Override
    public Long countDocuments(Long knowledgeId) {
        log.info("统计知识库文档数量：{}", knowledgeId);
        return documentMapper.countByKnowledgeId(knowledgeId);
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
        log.info("逻辑删除知识库：{}", id);
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
