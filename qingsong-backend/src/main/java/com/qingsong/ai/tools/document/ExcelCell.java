package com.qingsong.ai.tools.document;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 单元格值。value 始终为文本表示，type/format 可选。
 * <ul>
 *   <li>兼容 LLM 直接传字符串/数字的写法（等价于只填 value，类型自动推断）。</li>
 *   <li>type 缺省时按 value 内容启发式推断（数字/日期/百分比/货币/文本）。</li>
 *   <li>百分比：value 写的是百分比数值，如 "12.5" 表示 12.5%。</li>
 *   <li>format：可选的 Excel 格式码（白名单内置，非法值将报错）。</li>
 * </ul>
 */
@JsonDeserialize(using = ExcelCellDeserializer.class)
public record ExcelCell(
        @ToolParam(description = "单元格内容。想写入固定文本/示例说明（如\"示例：独处、熬夜、压力后刷手机\"）直接填该字段即可；留空字符串则生成空格子") String value,
        @ToolParam(description = "可选类型 STRING/NUMBER/DATE/BOOLEAN/PERCENT/CURRENCY；缺省自动推断，传字符串直接写值即可",
                required = false) ExcelCellType type,
        @ToolParam(description = "可选格式码（白名单），如 #,##0.00、0%、yyyy/m/d，一般无需填写",
                required = false) String format) {
}