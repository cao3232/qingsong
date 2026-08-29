package com.qingsong.ai.tools.document;

import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 段落/文本样式，全部字段可空，缺省使用文档默认值。
 */
public record WordTextStyle(
        @ToolParam(description = "字体名称，如 宋体、SimSun、Calibri") String fontFamily,
        @ToolParam(description = "字号（磅），如 12") Double fontSize,
        @ToolParam(description = "是否加粗") Boolean bold,
        @ToolParam(description = "是否斜体") Boolean italic,
        @ToolParam(description = "是否加下划线") Boolean underline,
        @ToolParam(description = "字体颜色，支持 #RRGGBB、RRGGBB 或颜色名，如 #FF0000、RED、红色") String color,
        @ToolParam(description = "文本高亮颜色，如 #FFFF00 或颜色名") String highlight,
        @ToolParam(description = "水平对齐：LEFT / CENTER / RIGHT / JUSTIFY") WordTextAlign align,
        @ToolParam(description = "左缩进（磅）") Double indentLeft,
        @ToolParam(description = "段前间距（磅）") Double spaceBefore,
        @ToolParam(description = "段后间距（磅）") Double spaceAfter,
        @ToolParam(description = "行距倍数，如 1.5") Double lineSpacing) {
}