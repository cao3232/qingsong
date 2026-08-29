package com.qingsong.ai.tools.document;

import org.springframework.ai.tool.annotation.ToolParam;

public record PdfDocumentParam(
        @ToolParam(description = "PDF 文件名，不含目录，.pdf 后缀可省略") String fileName,
        @ToolParam(description = "文档标题（可选），渲染为首页主标题", required = false) String title,
        @ToolParam(description = "Markdown 格式的文档正文，支持标题/列表/表格/代码块/引用/粗斜体/分割线等") String content,
        @ToolParam(description = "页面选项，可空", required = false) PdfDocumentOptions options) {
}