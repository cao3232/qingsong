package com.qingsong.ai.entity.vo.knowledge;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 文档分页响应（含整库聚合统计）
 *
 * @author caojiangjiang
 */
@Data
@Builder
public class DocumentPageResp {

    /**
     * 匹配文档总数
     */
    private Long total;

    /**
     * 匹配文档总大小（字节）
     */
    private Long totalSize;

    /**
     * 匹配文档中已嵌入向量的数量
     */
    private Long embeddedCount;

    /**
     * 当前页文档列表
     */
    private List<DocumentRespVO> records;
}
