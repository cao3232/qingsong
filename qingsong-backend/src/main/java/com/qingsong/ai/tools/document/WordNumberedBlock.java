package com.qingsong.ai.tools.document;

import org.springframework.ai.tool.annotation.ToolParam;

public record WordNumberedBlock(
        @ToolParam(description = "编号列表项文本，将自动生成 1. 2. 3. 编号") String text,
        @ToolParam(description = "列表项样式，可空") WordTextStyle style) implements WordBlock {
}