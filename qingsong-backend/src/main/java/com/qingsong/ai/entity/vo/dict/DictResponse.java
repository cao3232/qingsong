package com.qingsong.ai.entity.vo.dict;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 字典全量下发响应：version 用于前端本地缓存变化检测
 */
@Data
public class DictResponse {

    /**
     * 缓存版本号，写操作后递增；前端 version 不同才重新拉取
     */
    private Long version;

    /**
     * 已按 dict_code 分组、按 sort 排序的启用项
     */
    private Map<String, List<DictItemVO>> items;
}
