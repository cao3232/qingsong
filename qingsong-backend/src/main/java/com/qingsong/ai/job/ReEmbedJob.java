package com.qingsong.ai.job;

import com.qingsong.ai.entity.po.knowledge.DocumentBase;
import com.qingsong.ai.service.knowledge.DocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 待嵌入文档定时重试任务：定期扫描 embedding=false 的文档并重新向量化。
 *
 * @author caojiangjiang
 */
@Component
@Slf4j
public class ReEmbedJob {

    @Autowired
    private DocumentService documentService;

    @Value("${qingsong.rag.reembed-batch:10}")
    private int batchSize;

    /**
     * 每 10 分钟处理一批待嵌入文档
     */
    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT5M")
    public void retryPendingEmbedding() {
        List<DocumentBase> pending = documentService.listNotEmbedded(null).stream()
                .limit(Math.max(batchSize, 1))
                .toList();
        if (pending.isEmpty()) {
            return;
        }
        long success = 0;
        for (DocumentBase doc : pending) {
            try {
                if (documentService.reEmbedDocument(doc.getId())) {
                    success++;
                }
            } catch (Exception e) {
                log.error("定时重试嵌入失败，documentId={}", doc.getId(), e);
            }
        }
        log.info("定时重试嵌入完成，批次={}, 成功={}", pending.size(), success);
    }
}
