package com.qingsong.ai.tools.document;

import org.springframework.ai.tool.annotation.ToolParam;

public record WordHeadingBlock(
        @ToolParam(description = "标题文本") String text,
        @ToolParam(description = "标题级别 1 到 3") Integer level) implements WordBlock {
}