package com.qingsong.ai.tools.document;

import org.springframework.ai.tool.annotation.ToolParam;

public record WordTableBlock(
        @ToolParam(description = "表格数据（表头 + 数据行），表头行自动加粗置灰底") WordTableData data) implements WordBlock {
}