package com.qingsong.ai.tools.document;

import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 合并单元格区域，行列均从 1 开始（表头为第 1 行）。系统会将其裁剪到数据边界内。
 */
public record ExcelMergeRange(
        @ToolParam(description = "起始行（含，从1开始）") int rowStart,
        @ToolParam(description = "结束行（含，从1开始）") int rowEnd,
        @ToolParam(description = "起始列（含，从1开始）") int colStart,
        @ToolParam(description = "结束列（含，从1开始）") int colEnd) {
}