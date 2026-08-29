package com.qingsong.ai.tools.document;

import org.springframework.ai.tool.annotation.ToolParam;

public record WordParagraphBlock(
        @ToolParam(description = "段落文本") String text,
        @ToolParam(description = "段落样式，可空") WordTextStyle style) implements WordBlock {
}