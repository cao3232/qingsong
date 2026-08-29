package com.qingsong.ai.tools.document;

import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

public record ExcelSheetParam(
        @ToolParam(description = "工作表名称，最多 31 个字符") String sheetName,
        @ToolParam(description = "非空表头列表，最多 100 列") List<String> headers,
        @ToolParam(description = "数据行，每行与表头同列数，最多 10000 行；单元格对象可省略 type/format 自动推断") List<List<ExcelCell>> rows,
        @ToolParam(description = "工作表样式选项，可空", required = false) ExcelSheetOptions options) {
}