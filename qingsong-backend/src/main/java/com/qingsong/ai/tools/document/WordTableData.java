package com.qingsong.ai.tools.document;

import org.springframework.ai.tool.annotation.ToolParam;

public record WordTableData(
        @ToolParam(description = "表头，每列一个标题") java.util.List<String> headers,
        @ToolParam(description = "数据行，每行列数必须与表头一致") java.util.List<java.util.List<String>> rows) {
}