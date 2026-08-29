package com.qingsong.ai.tools.document;

import org.springframework.ai.tool.annotation.ToolParam;

public record WordBulletBlock(
        @ToolParam(description = "列表项文本") String text,
        @ToolParam(description = "列表项样式，可空") WordTextStyle style) implements WordBlock {
}