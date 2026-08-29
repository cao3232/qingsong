package com.qingsong.ai.tools.document;

import org.springframework.ai.tool.annotation.ToolParam;

/**
 * PDF 页面级选项，全部可空、全部可选。
 */
public record PdfDocumentOptions(
        @ToolParam(description = "页面方向：PORTRAIT（纵向）或 LANDSCAPE（横向），默认 PORTRAIT",
                required = false) String orientation,
        @ToolParam(description = "正文字号（pt），如 11，默认 11",
                required = false) Double fontSize,
        @ToolParam(description = "自定义附加 CSS（作用于正文，可选）",
                required = false) String css) {
}