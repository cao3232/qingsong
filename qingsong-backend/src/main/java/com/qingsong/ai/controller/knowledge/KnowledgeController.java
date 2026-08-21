package com.qingsong.ai.controller.knowledge;

import com.qingsong.ai.entity.po.knowledge.KnowledgeBase;
import com.qingsong.ai.entity.vo.knowledge.KnowledgeBaseReqVO;
import com.qingsong.ai.entity.vo.knowledge.KnowledgeBaseRespVO;
import com.qingsong.ai.entity.vo.mapper.KnowledgeBaseStructMapper;
import com.qingsong.ai.service.knowledge.KnowledgeBaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库 Controller
 *
 * @author AI Architect
 * @since 2026-03-20
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    /**
     * 获取所有激活的知识库
     */
    @GetMapping("/bases")
    public List<KnowledgeBaseRespVO> getActiveKnowledgeBases(@ModelAttribute KnowledgeBaseReqVO reqVO) {
        return knowledgeBaseService.getActiveKnowledgeBases(reqVO);
    }


    /**
     * 搜索知识库
     */
    @GetMapping("/bases/search")
    public List<KnowledgeBase> searchKnowledgeBases(@RequestParam String keyword) {
        return knowledgeBaseService.searchByKeyword(keyword);
    }

    /**
     * 创建知识库
     */
    @PostMapping("/bases")
    public KnowledgeBase createKnowledgeBase(
            @RequestParam String name,
            @RequestParam(required = false) String description) {
        return knowledgeBaseService.createKnowledgeBase(name, description);
    }


    @PutMapping("/bases")
    public KnowledgeBase updateKnowledgeBase(@RequestBody KnowledgeBase knowledgeBase) {
        return knowledgeBaseService.updateById(knowledgeBase) ? knowledgeBase : null;
    }

    /**
     * 更新知识库状态
     */
    @PutMapping("/bases/status")
    public boolean updateKnowledgeBaseStatus(
            @RequestBody List<Long> ids,
            @RequestParam Boolean isActive) {
        return knowledgeBaseService.batchUpdateStatus(ids, isActive);
    }

    /**
     * 删除知识库
     */
    @DeleteMapping("/bases/{id}")
    public boolean deleteKnowledgeBase(@PathVariable Long id) {
        return knowledgeBaseService.logicDelete(id);
    }
}
