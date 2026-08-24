package com.qingsong.ai.entity.vo.dict;

import lombok.Data;

/**
 * 下发前端的字典项
 */
@Data
public class DictItemVO {

    /**
     * 字典项值，前端表单存这个
     */
    private String key;

    /**
     * 展示文案
     */
    private String label;

    /**
     * 附加JSON(可选)
     */
    private String extra;

    /**
     * 排序
     */
    private Integer sort;
}
