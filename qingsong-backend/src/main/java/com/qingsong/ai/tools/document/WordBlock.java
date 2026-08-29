package com.qingsong.ai.tools.document;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Word 文档内容块顶层接口，通过 {@code blockType} 区分具体类型。
 * blockType 取值：HEADING / PARAGRAPH / BULLET / NUMBERED / QUOTE / TABLE / PAGE_BREAK / DIVIDER / TOC。
 * 模型漏发或填错 blockType 时，由 {@link WordInferredBlock}（defaultImpl）按字段宽容推断，避免整单反序列化失败。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "blockType",
        defaultImpl = WordInferredBlock.class)
@JsonSubTypes({
        @Type(value = WordHeadingBlock.class, name = "HEADING"),
        @Type(value = WordParagraphBlock.class, name = "PARAGRAPH"),
        @Type(value = WordBulletBlock.class, name = "BULLET"),
        @Type(value = WordNumberedBlock.class, name = "NUMBERED"),
        @Type(value = WordQuoteBlock.class, name = "QUOTE"),
        @Type(value = WordTableBlock.class, name = "TABLE"),
        @Type(value = WordPageBreakBlock.class, name = "PAGE_BREAK"),
        @Type(value = WordDividerBlock.class, name = "DIVIDER"),
        @Type(value = WordTocBlock.class, name = "TOC")
})
public sealed interface WordBlock permits WordHeadingBlock, WordParagraphBlock, WordBulletBlock,
        WordNumberedBlock, WordQuoteBlock, WordTableBlock, WordPageBreakBlock, WordDividerBlock, WordTocBlock,
        WordInferredBlock {
}