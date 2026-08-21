package com.qingsong.ai.controller.knowledge;

import com.qingsong.ai.entity.po.knowledge.OriginFileSource;
import com.qingsong.ai.service.knowledge.OriginFileSourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 原始文件源 Controller
 *
 * @author AI Architect
 * @since 2026-03-20
 */
@RestController
@RequestMapping("/api/knowledge/files")
public class OriginFileSourceController {

    @Autowired
    private OriginFileSourceService originFileSourceService;


    @PostMapping(value = "/{knowledgeId}", headers = "content-type=multipart/form-data")
    public boolean uploadKnowledgeFile(@RequestParam(name = "file") MultipartFile file,
                                       @PathVariable(name = "knowledgeId") String knowledgeId) {
        return originFileSourceService.uploadFile(file, knowledgeId);
    }


    /**
     * 根据 MD5 查询文件
     */
    @GetMapping("/md5/{md5}")
    public OriginFileSource getFileByMd5(@PathVariable String md5) {
        return originFileSourceService.getByMd5(md5);
    }

    /**
     * 统计文件数量
     */
    @GetMapping("/count")
    public Long getTotalFilesCount() {
        return originFileSourceService.countTotalFiles();
    }

    /**
     * 检查文件是否存在
     */
    @GetMapping("/exists/md5/{md5}")
    public boolean checkFileExists(@PathVariable String md5) {
        return originFileSourceService.existsByMd5(md5);
    }
}
