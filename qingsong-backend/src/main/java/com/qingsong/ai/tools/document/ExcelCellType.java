package com.qingsong.ai.tools.document;

/**
 * Excel 单元格类型。缺失时由系统按值自动推断。
 */
public enum ExcelCellType {
    STRING,
    NUMBER,
    DATE,
    BOOLEAN,
    PERCENT,
    CURRENCY
}