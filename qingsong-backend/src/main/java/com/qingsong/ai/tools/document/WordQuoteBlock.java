package com.qingsong.ai.tools.document;

import org.springframework.ai.tool.annotation.ToolParam;

public record WordQuoteBlock(
        @ToolParam(description = "引用文本，将以缩进斜体灰字呈现") String text) implements WordBlock {
}