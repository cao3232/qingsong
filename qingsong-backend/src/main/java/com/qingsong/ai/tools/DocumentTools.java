package com.qingsong.ai.tools;

import com.qingsong.ai.aspect.MyTools;
import com.qingsong.ai.tools.document.DocColor;
import com.qingsong.ai.tools.document.DocumentPathResolver;
import com.qingsong.ai.tools.document.DocumentSaveResult;
import com.qingsong.ai.tools.document.ExcelDocumentParam;
import com.qingsong.ai.tools.document.ExcelGenerator;
import com.qingsong.ai.tools.document.ExcelMergeRange;
import com.qingsong.ai.tools.document.ExcelCell;
import com.qingsong.ai.tools.document.ExcelColumnValidation;
import com.qingsong.ai.tools.document.ExcelSheetOptions;
import com.qingsong.ai.tools.document.ExcelSheetParam;
import com.qingsong.ai.tools.document.WordBlock;
import com.qingsong.ai.tools.document.WordBulletBlock;
import com.qingsong.ai.tools.document.WordDocumentParam;
import com.qingsong.ai.tools.document.WordGenerator;
import com.qingsong.ai.tools.document.WordHeadingBlock;
import com.qingsong.ai.tools.document.WordNumberedBlock;
import com.qingsong.ai.tools.document.WordParagraphBlock;
import com.qingsong.ai.tools.document.WordQuoteBlock;
import com.qingsong.ai.tools.document.WordTableBlock;
import com.qingsong.ai.tools.document.WordTableData;
import com.qingsong.ai.tools.document.WordTextStyle;
import com.qingsong.ai.tools.document.PdfDocumentOptions;
import com.qingsong.ai.tools.document.PdfDocumentParam;
import com.qingsong.ai.tools.document.PdfGenerator;
import com.qingsong.ai.utils.MinioTemplate;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.util.WorkbookUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.List;

@Slf4j
@Component("documentTools")
@MyTools(name = "documentTools", description = "生成并保存 Word 和 Excel 文档")
public class DocumentTools {

    private static final int MAX_WORD_BLOCKS = 500;
    private static final int MAX_WORD_TEXT_LENGTH = 20_000;
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_WORD_TABLE_ROWS = 500;
    private static final int MAX_WORD_TABLE_COLS = 30;
    private static final int MAX_WORD_TABLE_CELLS = 10_000;
    private static final int MAX_EXCEL_SHEETS = 20;
    private static final int MAX_EXCEL_COLUMNS = 100;
    private static final int MAX_EXCEL_ROWS = 10_000;
    private static final int MAX_CELL_LENGTH = 32_767;
    private static final int MAX_EXCEL_CELLS = 100_000;
    private static final long MAX_EXCEL_TOTAL_CELLS = 500_000L;
    private static final long MAX_EXCEL_TEXT_LENGTH = 5_000_000L;
    private static final int MAX_PDF_TEXT_LENGTH = 300_000;
    private static final int MAX_PDF_CSS_LENGTH = 8_000;
    private static final int DEFAULT_EXPIRY_SECONDS = 3600;
    private static final String WORD_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String EXCEL_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final DocumentPathResolver pathResolver;
    private final MinioTemplate minioTemplate;

    public DocumentTools(DocumentPathResolver pathResolver, MinioTemplate minioTemplate) {
        this.pathResolver = pathResolver;
        this.minioTemplate = minioTemplate;
    }

    @Tool(name = "save_word_document", description = """
            当用户明确要求生成并保存 Word 文档时调用。
            内容块为带 blockType 的多态对象：HEADING(level=1~3)/PARAGRAPH/BULLET/NUMBERED(自动编号)/QUOTE/TABLE/PAGE_BREAK/DIVIDER/TOC；
            PARAGRAPH/BULLET/NUMBERED 可带 style（字体/字号/加粗/颜色/对齐/行距等）。
            返回结果中的 filePath 是一个完整的下载链接（包含 X-Amz-Signature 签名参数）。
            回复用户时必须把 filePath 整个、原样、完整地给出，绝不能截断、改写或省略链接的任一部分
            （尤其是签名参数），否则链接无法访问。
            """)
    public DocumentSaveResult saveWordDocument(
            @ToolParam(description = "Word 文件名、主标题、有序内容块及可选页面选项") WordDocumentParam param) {
        try {
            validateWord(param);
            String objectName = nextObjectName(param.fileName(), ".docx");
            byte[] bytes = WordGenerator.generate(param);
            String signedUrl = minioTemplate.putObjectAndGetPresignedUrl(
                    objectName, new ByteArrayInputStream(bytes), bytes.length, WORD_CONTENT_TYPE, DEFAULT_EXPIRY_SECONDS);
            log.info("Word 文档上传成功: objectName={}, size={}", objectName, bytes.length);
            return DocumentSaveResult.success(objectName, signedUrl, bytes.length,
                    "Word 文档已生成，完整下载链接：" + signedUrl + "（请原样完整返回整个链接，不要省略签名参数）");
        } catch (IllegalArgumentException e) {
            return DocumentSaveResult.failure(e.getMessage());
        } catch (Exception e) {
            log.error("Word 文档生成失败", e);
            return DocumentSaveResult.failure("Word 文档生成失败，请稍后重试");
        }
    }

    @Tool(name = "save_excel_document", description = """
            当用户明确要求把表格数据生成并保存为 Excel 文件时调用。
            支持多个工作表（sheets 数组）；单元格可选 type（STRING/NUMBER/DATE/BOOLEAN/PERCENT/CURRENCY）与格式码，
            缺省自动推断；支持列宽、合并单元格（merges，1 起始）、表头底色与冻结首行。
            返回结果中的 filePath 是一个完整的下载链接（包含 X-Amz-Signature 签名参数）。
            回复用户时必须把 filePath 整个、原样、完整地给出，绝不能截断、改写或省略链接的任一部分
            （尤其是签名参数），否则链接无法访问。
            """)
    public DocumentSaveResult saveExcelDocument(
            @ToolParam(description = "Excel 文件名及工作表列表") ExcelDocumentParam param) {
        try {
            validateExcel(param);
            String objectName = nextObjectName(param.fileName(), ".xlsx");
            byte[] bytes = ExcelGenerator.generate(param);
            String signedUrl = minioTemplate.putObjectAndGetPresignedUrl(
                    objectName, new ByteArrayInputStream(bytes), bytes.length, EXCEL_CONTENT_TYPE, DEFAULT_EXPIRY_SECONDS);
            log.info("Excel 文档上传成功: objectName={}, size={}", objectName, bytes.length);
            return DocumentSaveResult.success(objectName, signedUrl, bytes.length,
                    "Excel 文档已生成，完整下载链接：" + signedUrl + "（请原样完整返回整个链接，不要省略签名参数）");
        } catch (IllegalArgumentException e) {
            return DocumentSaveResult.failure(e.getMessage());
        } catch (Exception e) {
            log.error("Excel 文档生成失败", e);
            return DocumentSaveResult.failure("Excel 文档生成失败，请稍后重试");
        }
    }

    @Tool(name = "save_pdf_document", description = """
            当用户明确要求把文档导出/生成为 PDF 文件时调用。
            content 用 Markdown 编写（支持标题/列表/表格/代码块/引用/粗斜体/分割线），
            将渲染为版式规整的 PDF，标题可选、页面方向可选。
            返回结果中的 filePath 是一个完整的下载链接（包含 X-Amz-Signature 签名参数）。
            回复用户时必须把 filePath 整个、原样、完整地给出，绝不能截断、改写或省略链接的任一部分
            （尤其是签名参数），否则链接无法访问。
            """)
    public DocumentSaveResult savePdfDocument(
            @ToolParam(description = "PDF 文件名、标题、Markdown 正文及可选页面选项") PdfDocumentParam param) {
        try {
            validatePdf(param);
            String objectName = nextObjectName(param.fileName(), ".pdf");
            byte[] bytes = PdfGenerator.generate(param);
            String signedUrl = minioTemplate.putObjectAndGetPresignedUrl(
                    objectName, new ByteArrayInputStream(bytes), bytes.length, PDF_CONTENT_TYPE, DEFAULT_EXPIRY_SECONDS);
            log.info("PDF 文档上传成功: objectName={}, size={}", objectName, bytes.length);
            return DocumentSaveResult.success(objectName, signedUrl, bytes.length,
                    "PDF 文档已生成，完整下载链接：" + signedUrl + "（请原样完整返回整个链接，不要省略签名参数）");
        } catch (IllegalArgumentException e) {
            return DocumentSaveResult.failure(e.getMessage());
        } catch (Exception e) {
            log.error("PDF 文档生成失败", e);
            return DocumentSaveResult.failure("PDF 文档生成失败，请稍后重试");
        }
    }

    /** 复用文件名校验，同名时按 -N 递增，避免覆盖已有对象 */
    private String nextObjectName(String requestedName, String extension) {
        String base = pathResolver.resolveObjectName(requestedName, extension);
        String stem = base.substring(0, base.length() - extension.length());
        for (int version = 1; ; version++) {
            String candidate = version == 1 ? stem + extension : stem + "-" + version + extension;
            if (!minioTemplate.exists(candidate)) {
                return candidate;
            }
        }
    }

    // ---------- Word 校验 ----------

    private void validateWord(WordDocumentParam param) {
        if (param == null) {
            throw new IllegalArgumentException("Word 文档参数不能为空");
        }
        if (param.title() == null || param.title().isBlank()) {
            throw new IllegalArgumentException("Word 文档标题不能为空");
        }
        if (param.title().length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("Word 文档标题不能超过 200 个字符");
        }
        if (param.blocks() == null) {
            throw new IllegalArgumentException("Word 内容块不能为空");
        }
        if (param.blocks().size() > MAX_WORD_BLOCKS) {
            throw new IllegalArgumentException("Word 内容块不能超过 500 个");
        }
        if (param.options() != null && param.options().orientation() != null) {
            String orientation = param.options().orientation();
            if (!"PORTRAIT".equalsIgnoreCase(orientation) && !"LANDSCAPE".equalsIgnoreCase(orientation)) {
                throw new IllegalArgumentException("页面方向必须为 PORTRAIT 或 LANDSCAPE");
            }
        }
        for (int i = 0; i < param.blocks().size(); i++) {
            validateWordBlock(param.blocks().get(i), i + 1);
        }
    }

    private void validateWordBlock(WordBlock block, int index) {
        String location = "第 " + index + " 个 Word 内容块";
        if (block == null) {
            throw new IllegalArgumentException(location + "为空");
        }
        if (block instanceof WordHeadingBlock heading) {
            requireWordText(heading.text(), location);
            if (heading.level() == null || heading.level() < 1 || heading.level() > 3) {
                throw new IllegalArgumentException("Word 标题级别必须为 1 到 3（" + location + "）");
            }
        } else if (block instanceof WordParagraphBlock paragraph) {
            requireWordText(paragraph.text(), location);
            validateWordStyle(paragraph.style(), location);
        } else if (block instanceof WordBulletBlock bullet) {
            requireWordText(bullet.text(), location);
            validateWordStyle(bullet.style(), location);
        } else if (block instanceof WordNumberedBlock numbered) {
            requireWordText(numbered.text(), location);
            validateWordStyle(numbered.style(), location);
        } else if (block instanceof WordQuoteBlock quote) {
            requireWordText(quote.text(), location);
        } else if (block instanceof WordTableBlock table) {
            validateWordTable(table.data(), location);
        }
    }

    private void requireWordText(String text, String location) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(location + "文本不能为空");
        }
        if (text.length() > MAX_WORD_TEXT_LENGTH) {
            throw new IllegalArgumentException(location + "文本超过 20000 个字符");
        }
    }

    private void validateWordStyle(WordTextStyle style, String location) {
        if (style == null) {
            return;
        }
        if (style.fontSize() != null && (style.fontSize() < 6 || style.fontSize() > 72)) {
            throw new IllegalArgumentException(location + "字号必须在 6 到 72 之间");
        }
        if (style.lineSpacing() != null && (style.lineSpacing() < 0.5 || style.lineSpacing() > 5)) {
            throw new IllegalArgumentException(location + "行距倍数必须在 0.5 到 5 之间");
        }
        if (style.indentLeft() != null && style.indentLeft() < 0) {
            throw new IllegalArgumentException(location + "左缩进不能为负数");
        }
    }

    private void validateWordTable(WordTableData data, String location) {
        if (data == null) {
            throw new IllegalArgumentException(location + "表格数据不能为空");
        }
        if (data.headers() == null || data.headers().isEmpty()) {
            throw new IllegalArgumentException(location + "表格表头不能为空");
        }
        if (data.headers().size() > MAX_WORD_TABLE_COLS) {
            throw new IllegalArgumentException(location + "表格列数不能超过 " + MAX_WORD_TABLE_COLS + " 列");
        }
        if (data.rows() == null) {
            throw new IllegalArgumentException(location + "表格数据行不能为空");
        }
        if (data.rows().size() > MAX_WORD_TABLE_ROWS) {
            throw new IllegalArgumentException(location + "表格行数不能超过 " + MAX_WORD_TABLE_ROWS + " 行");
        }
        long cells = (long) (data.rows().size() + 1) * data.headers().size();
        if (cells > MAX_WORD_TABLE_CELLS) {
            throw new IllegalArgumentException(location + "表格单元格总数不能超过 " + MAX_WORD_TABLE_CELLS + " 个");
        }
        for (int r = 0; r < data.rows().size(); r++) {
            List<String> row = data.rows().get(r);
            if (row == null || row.size() != data.headers().size()) {
                int columns = row == null ? 0 : row.size();
                throw new IllegalArgumentException(location + "表格第 " + (r + 1) + " 行有 " + columns
                        + " 列，但表头有 " + data.headers().size() + " 列");
            }
            for (String value : row) {
                if (value != null && value.length() > MAX_WORD_TEXT_LENGTH) {
                    throw new IllegalArgumentException(location + "表格单元格文本超过 20000 个字符");
                }
            }
        }
    }

    // ---------- PDF 校验 ----------

    private void validatePdf(PdfDocumentParam param) {
        if (param == null) {
            throw new IllegalArgumentException("PDF 文档参数不能为空");
        }
        if (param.title() != null && param.title().length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("PDF 文档标题不能超过 200 个字符");
        }
        if (param.content() == null || param.content().isBlank()) {
            throw new IllegalArgumentException("PDF 文档正文不能为空");
        }
        if (param.content().length() > MAX_PDF_TEXT_LENGTH) {
            throw new IllegalArgumentException("PDF 文档正文不能超过 300000 个字符");
        }
        if (param.options() != null) {
            PdfDocumentOptions options = param.options();
            if (options.orientation() != null) {
                String orientation = options.orientation();
                if (!"PORTRAIT".equalsIgnoreCase(orientation) && !"LANDSCAPE".equalsIgnoreCase(orientation)) {
                    throw new IllegalArgumentException("页面方向必须为 PORTRAIT 或 LANDSCAPE");
                }
            }
            if (options.fontSize() != null && (options.fontSize() < 7 || options.fontSize() > 40)) {
                throw new IllegalArgumentException("正文字号必须在 7 到 40 之间");
            }
            if (options.css() != null && options.css().length() > MAX_PDF_CSS_LENGTH) {
                throw new IllegalArgumentException("自定义 CSS 不能超过 8000 个字符");
            }
        }
    }

    // ---------- Excel 校验 ----------

    private void validateExcel(ExcelDocumentParam param) {
        if (param == null) {
            throw new IllegalArgumentException("Excel 文档参数不能为空");
        }
        if (param.sheets() == null || param.sheets().isEmpty()) {
            throw new IllegalArgumentException("Excel 工作表不能为空");
        }
        if (param.sheets().size() > MAX_EXCEL_SHEETS) {
            throw new IllegalArgumentException("Excel 工作表不能超过 " + MAX_EXCEL_SHEETS + " 个");
        }
        long totalCells = 0L;
        long totalTextLength = 0L;
        for (int i = 0; i < param.sheets().size(); i++) {
            long[] totals = validateSheet(param.sheets().get(i), i + 1, totalCells, totalTextLength);
            totalCells = totals[0];
            totalTextLength = totals[1];
        }
    }

    private long[] validateSheet(ExcelSheetParam sheet, int sheetIndex, long totalCells, long totalTextLength) {
        if (sheet == null) {
            throw new IllegalArgumentException("第 " + sheetIndex + " 个工作表为空");
        }
        if (sheet.sheetName() == null || sheet.sheetName().isBlank()) {
            throw new IllegalArgumentException("Excel 工作表名称不能为空");
        }
        if (sheet.sheetName().length() > 31) {
            throw new IllegalArgumentException("Excel 工作表名称不符合要求");
        }
        try {
            WorkbookUtil.validateSheetName(sheet.sheetName());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Excel 工作表名称不符合要求");
        }
        if (sheet.headers() == null || sheet.headers().isEmpty()) {
            throw new IllegalArgumentException("Excel 表头不能为空");
        }
        if (sheet.headers().size() > MAX_EXCEL_COLUMNS) {
            throw new IllegalArgumentException("Excel 表头不能超过 100 列");
        }
        if (sheet.rows() == null) {
            throw new IllegalArgumentException("Excel 数据行不能为空");
        }
        if (sheet.rows().size() > MAX_EXCEL_ROWS) {
            throw new IllegalArgumentException("Excel 数据不能超过 10000 行");
        }
        long cellCount = (long) sheet.headers().size() * sheet.rows().size();
        if (cellCount > MAX_EXCEL_CELLS) {
            throw new IllegalArgumentException("Excel 数据单元格总数不能超过 100000 个");
        }
        long newTotalCells = totalCells + cellCount;
        if (newTotalCells > MAX_EXCEL_TOTAL_CELLS) {
            throw new IllegalArgumentException("Excel 全部工作表单元格总数不能超过 500000 个");
        }
        long textLength = validateHeaderCells(sheet.headers(), totalTextLength);
        for (int i = 0; i < sheet.rows().size(); i++) {
            List<ExcelCell> row = sheet.rows().get(i);
            if (row == null || row.size() != sheet.headers().size()) {
                int columns = row == null ? 0 : row.size();
                throw new IllegalArgumentException("第 " + (i + 1) + " 行有 " + columns
                        + " 列，但表头有 " + sheet.headers().size() + " 列");
            }
            textLength = validateDataCells(row, "第 " + (i + 1) + " 行", textLength);
        }
        validateSheetOptions(sheet, sheetIndex);
        return new long[]{newTotalCells, textLength};
    }

    private long validateHeaderCells(List<String> cells, long totalTextLength) {
        for (int i = 0; i < cells.size(); i++) {
            String value = cells.get(i);
            if (value != null && value.length() > MAX_CELL_LENGTH) {
                throw new IllegalArgumentException("表头第 " + (i + 1) + " 列超过 32767 个字符");
            }
            if (value != null) {
                totalTextLength += value.length();
                checkTotalText(totalTextLength);
            }
        }
        return totalTextLength;
    }

    private long validateDataCells(List<ExcelCell> cells, String location, long totalTextLength) {
        for (int i = 0; i < cells.size(); i++) {
            ExcelCell cell = cells.get(i);
            if (cell == null) {
                throw new IllegalArgumentException(location + "第 " + (i + 1) + " 列单元格为空");
            }
            String value = cell.value();
            if (value != null && value.length() > MAX_CELL_LENGTH) {
                throw new IllegalArgumentException(location + "第 " + (i + 1) + " 列超过 32767 个字符");
            }
            if (value != null) {
                totalTextLength += value.length();
                checkTotalText(totalTextLength);
            }
        }
        return totalTextLength;
    }

    private void checkTotalText(long totalTextLength) {
        if (totalTextLength > MAX_EXCEL_TEXT_LENGTH) {
            throw new IllegalArgumentException("Excel 文本总量不能超过 5000000 个字符");
        }
    }

    private void validateSheetOptions(ExcelSheetParam sheet, int sheetIndex) {
        if (sheet.options() == null) {
            return;
        }
        ExcelSheetOptions options = sheet.options();
        int headerCount = sheet.headers().size();
        if (options.columnWidths() != null && options.columnWidths().size() != headerCount) {
            throw new IllegalArgumentException("工作表 " + sheetIndex + " 的列宽数量与表头列数不一致");
        }
        if (options.columnFormats() != null) {
            if (options.columnFormats().size() != headerCount) {
                throw new IllegalArgumentException("工作表 " + sheetIndex + " 的列格式数量与表头列数不一致");
            }
            for (String format : options.columnFormats()) {
                if (format != null && !format.isBlank() && !ExcelGenerator.ALLOWED_FORMATS.contains(format)) {
                    throw new IllegalArgumentException("工作表 " + sheetIndex + " 不允许的列格式码：" + format
                            + "，可用格式：" + ExcelGenerator.ALLOWED_FORMATS);
                }
            }
        }
        if (options.columnNotes() != null) {
            if (options.columnNotes().size() != headerCount) {
                throw new IllegalArgumentException("工作表 " + sheetIndex + " 的列批注数量与表头列数不一致");
            }
            for (String note : options.columnNotes()) {
                if (note != null && note.length() > 500) {
                    throw new IllegalArgumentException("工作表 " + sheetIndex + " 的列批注不能超过 500 个字符");
                }
            }
        }
        if (options.headerColor() != null) {
            DocColor.toHex6(options.headerColor());
        }
        if (options.validations() != null) {
            int maxCol = headerCount;
            for (ExcelColumnValidation v : options.validations()) {
                if (v == null) {
                    continue;
                }
                if (v.col() < 1 || v.col() > maxCol) {
                    throw new IllegalArgumentException("工作表 " + sheetIndex + " 下拉验证的列号超出范围：" + v.col());
                }
                if (v.allowedValues() == null || v.allowedValues().isEmpty()) {
                    throw new IllegalArgumentException("工作表 " + sheetIndex + " 第 " + v.col() + " 列的下拉选项不能为空");
                }
                long length = 2;
                for (String s : v.allowedValues()) {
                    if (s != null) {
                        length += s.replace("\"", "\"\"").length() + 3;
                    }
                }
                if (length > 255) {
                    throw new IllegalArgumentException("工作表 " + sheetIndex + " 第 " + v.col() + " 列的下拉选项总长度超过 255 字符限制");
                }
            }
        }
        if (options.merges() != null) {
            int maxRow = sheet.rows().size() + 1;
            int maxCol = sheet.headers().size();
            for (ExcelMergeRange m : options.merges()) {
                if (m == null) {
                    throw new IllegalArgumentException("工作表 " + sheetIndex + " 的合并区域不能为空");
                }
                boolean invalid = m.rowStart() < 1 || m.rowEnd() < 1 || m.rowStart() > m.rowEnd()
                        || m.rowStart() > maxRow || m.rowEnd() > maxRow
                        || m.colStart() < 1 || m.colEnd() < 1 || m.colStart() > m.colEnd()
                        || m.colStart() > maxCol || m.colEnd() > maxCol;
                if (invalid) {
                    throw new IllegalArgumentException("工作表 " + sheetIndex + " 的合并区域超出数据范围：row "
                            + m.rowStart() + "-" + m.rowEnd() + ", col " + m.colStart() + "-" + m.colEnd());
                }
            }
        }
    }
}