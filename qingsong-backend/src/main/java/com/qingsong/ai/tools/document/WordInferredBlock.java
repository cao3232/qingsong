package com.qingsong.ai.tools.document;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * 兜底块：当 blockType 缺失或无法解析时，由 {@link WordBlockDeserializer}
 * 按字段推断为合适的实际块类型，避免整单反序列化失败。
 */
@JsonDeserialize(using = WordBlockDeserializer.class)
public record WordInferredBlock() implements WordBlock {
}