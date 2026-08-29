package com.qingsong.ai.tools.document;

import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

/**
 * 某列的下拉数据验证（只能选固定值）。col 从 1 开始（表头为第 1 列），作用于数据行（不含表头）。
 */
public record ExcelColumnValidation(
        @ToolParam(description = "列号，从 1 开始") int col,
        @ToolParam(description = "可选值列表（下拉选项），如 [\"未开始\", \"已达成\", \"放弃\"]") List<String> allowedValues,
        @ToolParam(description = "是否允许留空，默认 true", required = false) Boolean allowBlank) {
}