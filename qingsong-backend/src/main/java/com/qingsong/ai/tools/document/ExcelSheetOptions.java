package com.qingsong.ai.tools.document;

import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

/**
 * 单个工作表的样式选项，全部可空、全部可选。
 */
public record ExcelSheetOptions(
        @ToolParam(description = "各列列宽（字符数），数量必须与表头一致；缺省按内容自动估算",
                required = false) List<Integer> columnWidths,
        @ToolParam(description = "各列默认格式码（白名单），数量必须与表头一致；设置后该列单元格无需逐格写 format",
                required = false) List<String> columnFormats,
        @ToolParam(description = "各列表头批注/填写说明（鼠标悬停显示，适合放示例说明），数量必须与表头一致，某项无说明填 null",
                required = false) List<String> columnNotes,
        @ToolParam(description = "合并单元格区域列表（1 起始）",
                required = false) List<ExcelMergeRange> merges,
        @ToolParam(description = "下拉数据验证（单元格只能选固定值），作用于数据行",
                required = false) List<ExcelColumnValidation> validations,
        @ToolParam(description = "是否冻结首行，默认 true", required = false) Boolean freezeHeader,
        @ToolParam(description = "表头底色，如 #4472C4", required = false) String headerColor) {
}