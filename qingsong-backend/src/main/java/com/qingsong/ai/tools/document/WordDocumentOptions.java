package com.qingsong.ai.tools.document;

import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Word 页面级别的选项，全部可空、全部可选。
 */
public record WordDocumentOptions(
        @ToolParam(description = "页面方向：PORTRAIT（纵向）或 LANDSCAPE（横向）", required = false) String orientation,
        @ToolParam(description = "是否在页脚显示页码（第 X 页）", required = false) Boolean pageNumbers) {
}