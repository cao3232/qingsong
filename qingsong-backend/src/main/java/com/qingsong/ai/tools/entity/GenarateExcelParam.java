package com.qingsong.ai.tools.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;


public record GenarateExcelParam(
        @ToolParam(description = "Excel文件名，不带后缀，例如 '2024财务报表'")
        @NotBlank
        String fileName,

        @ToolParam(description = "Sheet页名称")
        @NotBlank
        String sheetName,

        @ToolParam(description = "表头列表，例如 ['姓名', '年龄', '职位']")
        @NotNull
        List<String> headers,

        @ToolParam(description = "数据行列表，每一行是一个字符串列表，顺序必须与表头对应，与rows.get(0)与header的size相等")
        @NotNull
        List<List<String>> rows,

        @ToolParam(description = "接收文件的邮箱地址，默认15836208068@139.com")
        String targetEmail
) {
}

