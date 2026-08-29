package com.qingsong.ai.tools.document;

import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

public record ExcelDocumentParam(
        @ToolParam(description = "Excel 文件名，不含目录，.xlsx 后缀可省略") String fileName,
        @ToolParam(description = "工作表列表，至少 1 个，最多 20 个") List<ExcelSheetParam> sheets) {
}