package com.qingsong.ai.tools.document;

import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

public record WordDocumentParam(
        @ToolParam(description = "Word 文件名，不含目录，.docx 后缀可省略") String fileName,
        @ToolParam(description = "Word 文档主标题") String title,
        @ToolParam(description = "按顺序写入的内容块。每块必须带 blockType 字段区分类型：" +
                "HEADING(标题,level=1~3)、PARAGRAPH(段落,可带style)、BULLET(圆点列表)、NUMBERED(自动编号列表 1.2.3)、" +
                "QUOTE(引用)、TABLE(表格,data.headers+data.rows)、PAGE_BREAK(分页)、DIVIDER(分隔线)、TOC(目录,自动收集标题)")
        List<WordBlock> blocks,
        @ToolParam(description = "页面选项，可空", required = false) WordDocumentOptions options) {
}