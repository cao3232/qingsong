package com.qingsong.ai.tools.document;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 按 {@link ExcelDocumentParam} 渲染 .xlsx 字节内容（底层为 POI XSSFWorkbook）。
 * 支持多工作表、单元格类型、表头样式、冻结首行、列宽与合并单元格。
 * 用户输入数据问题（非法数字/日期/格式码等）抛出 IllegalArgumentException，由工具层转为失败结果。
 */
public final class ExcelGenerator {

    public static final Set<String> ALLOWED_FORMATS = Set.of(
            "General", "0", "0.0", "0.00", "0.##",
            "0%", "0.0%", "0.00%", "0.##%",
            "#,##0", "#,##0.0", "#,##0.00", "#,##0.##",
            "¥#,##0", "¥#,##0.00", "￥#,##0", "￥#,##0.00", "$#,##0", "$#,##0.00",
            "yyyy/m/d", "yyyy/m/d hh:mm", "yyyy/mm/dd", "m/d", "m/d/yy",
            "yyyy年m月d日", "m月d日", "hh:mm");

    private static final String DEFAULT_HEADER_COLOR = "4472C4";
    private static final int MAX_COLUMN_WIDTH = 255;

    private ExcelGenerator() {
    }

    public static byte[] generate(ExcelDocumentParam param) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (ExcelSheetParam sheet : param.sheets()) {
                writeSheet(workbook, sheet);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Excel 文档生成失败", e);
        }
    }

    private static void writeSheet(XSSFWorkbook workbook, ExcelSheetParam sheet) {
        XSSFSheet xssfSheet = workbook.createSheet(sheet.sheetName());
        Map<String, CellStyle> styleCache = new HashMap<>();
        List<Integer> widths = resolveColumnWidths(sheet);
        for (int c = 0; c < sheet.headers().size(); c++) {
            xssfSheet.setColumnWidth(c, widths.get(c) * 256);
        }

        XSSFRow headerRow = xssfSheet.createRow(0);
        CellStyle headerStyle = createHeaderStyle(workbook, sheet.options());
        for (int c = 0; c < sheet.headers().size(); c++) {
            XSSFCell cell = headerRow.createCell(c);
            cell.setCellValue(sheet.headers().get(c));
            cell.setCellStyle(headerStyle);
        }

        boolean freeze = sheet.options() == null || sheet.options().freezeHeader() == null
                || sheet.options().freezeHeader();
        if (freeze) {
            xssfSheet.createFreezePane(0, 1);
        }

        ExcelCellType[] columnTypes = inferColumnTypes(sheet);
        List<String> columnFormats = resolveColumnFormats(sheet);
        for (int r = 0; r < sheet.rows().size(); r++) {
            XSSFRow xssfRow = xssfSheet.createRow(r + 1);
            List<ExcelCell> rowData = sheet.rows().get(r);
            for (int c = 0; c < sheet.headers().size(); c++) {
                writeCell(workbook, xssfRow, c, rowData.get(c), columnTypes[c], columnFormats.get(c),
                        styleCache, sheet.sheetName());
            }
        }

        applyMerges(xssfSheet, sheet);
        applyValidations(xssfSheet, sheet);
        applyColumnNotes(xssfSheet, sheet);
    }

    // ---------- 单元格 ----------

    private static void writeCell(XSSFWorkbook workbook, XSSFRow xssfRow, int colIdx, ExcelCell cell,
                              ExcelCellType columnType, String columnFormat, Map<String, CellStyle> styleCache,
                              String sheetName) {
        XSSFCell xCell = xssfRow.createCell(colIdx);
        if (cell == null || cell.value() == null || cell.value().isBlank()) {
            xCell.setCellValue("");
            return;
        }
        String value = cell.value();
        ExcelCellType type = cell.type() != null ? cell.type() : columnType;
        String location = "工作表 '" + sheetName + "' " + (xssfRow.getRowNum() + 1) + " 行 " + (colIdx + 1) + " 列 ";
        String format = resolveFormat(type, cell.format(), columnFormat, location);
        try {
            switch (type) {
                case STRING -> {
                    xCell.setCellValue(value);
                    applyStyle(xCell, workbook, format, styleCache);
                }
                case NUMBER -> {
                    xCell.setCellValue(parseNumber(value, location));
                    applyStyle(xCell, workbook, format, styleCache);
                }
                case DATE -> {
                    xCell.setCellValue(parseDate(value, location));
                    applyStyle(xCell, workbook, format, styleCache);
                }
                case BOOLEAN -> xCell.setCellValue(parseBoolean(value, location));
                case PERCENT -> {
                    xCell.setCellValue(parseNumber(value, location) / 100.0);
                    applyStyle(xCell, workbook, format, styleCache);
                }
                case CURRENCY -> {
                    xCell.setCellValue(parseNumber(value, location));
                    applyStyle(xCell, workbook, format, styleCache);
                }
            }
        } catch (IllegalArgumentException e) {
            if (cell.type() != null) {
                throw e;
            }
            xCell.setCellValue(value);
        }
    }

    private static void applyStyle(XSSFCell cell, XSSFWorkbook workbook, String format,
                                   Map<String, CellStyle> styleCache) {
        if (format != null) {
            cell.setCellStyle(cellStyle(workbook, format, styleCache));
        }
    }

    private static CellStyle cellStyle(XSSFWorkbook workbook, String format, Map<String, CellStyle> cache) {
        return cache.computeIfAbsent(format, f -> {
            CellStyle style = workbook.createCellStyle();
            if (!"General".equals(f)) {
                style.setDataFormat(workbook.createDataFormat().getFormat(f));
            }
            return style;
        });
    }

    private static String resolveFormat(ExcelCellType type, String explicit, String columnDefault, String location) {
        String chosen = explicit != null && !explicit.isBlank()
                ? explicit
                : (columnDefault != null && !columnDefault.isBlank() ? columnDefault : null);
        if (chosen != null) {
            if (!ALLOWED_FORMATS.contains(chosen)) {
                throw new IllegalArgumentException(location + "不允许的格式码：" + chosen
                        + "，可用格式：" + ALLOWED_FORMATS);
            }
            return chosen;
        }
        return switch (type) {
            case DATE -> "yyyy/m/d";
            case PERCENT -> "0.##%";
            case CURRENCY -> "¥#,##0.00";
            default -> null;
        };
    }

    private static List<String> resolveColumnFormats(ExcelSheetParam sheet) {
        List<String> configured = sheet.options() == null ? null : sheet.options().columnFormats();
        List<String> formats = new ArrayList<>(sheet.headers().size());
        boolean use = configured != null && configured.size() == sheet.headers().size();
        for (int c = 0; c < sheet.headers().size(); c++) {
            String f = use ? configured.get(c) : null;
            formats.add(f == null || f.isBlank() ? null : f);
        }
        return formats;
    }

    // ---------- 类型推断与值解析 ----------

    private static ExcelCellType[] inferColumnTypes(ExcelSheetParam sheet) {
        ExcelCellType[] types = new ExcelCellType[sheet.headers().size()];
        for (int c = 0; c < types.length; c++) {
            types[c] = inferType(sheet.rows(), c);
        }
        return types;
    }

    private static ExcelCellType inferType(List<List<ExcelCell>> rows, int col) {
        for (List<ExcelCell> row : rows) {
            if (row == null || col >= row.size()) {
                continue;
            }
            ExcelCell cell = row.get(col);
            if (cell == null) {
                continue;
            }
            if (cell.type() != null) {
                return cell.type();
            }
            if (cell.value() != null && !cell.value().isBlank()) {
                return inferFromValue(cell.value());
            }
        }
        return ExcelCellType.STRING;
    }

    private static ExcelCellType inferFromValue(String value) {
        String v = value.trim();
        if (v.matches("^[-+]?\\d+(\\.\\d+)?$")) {
            return ExcelCellType.NUMBER;
        }
        if (v.matches("^\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}(\\s+\\d{1,2}:\\d{2}(:\\d{2})?)?$")) {
            return ExcelCellType.DATE;
        }
        if (v.matches("^[-+]?\\d+(\\.\\d+)?%$")) {
            return ExcelCellType.PERCENT;
        }
        if (v.matches("^[¥￥$€]\\s*[-+]?\\d+(\\.\\d+)?$")) {
            return ExcelCellType.CURRENCY;
        }
        return ExcelCellType.STRING;
    }

    private static double parseNumber(String value, String location) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(location + "不是有效数字：" + value);
        }
    }

    private static boolean parseBoolean(String value, String location) {
        if ("true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(value.trim()) || "0".equals(value.trim())) {
            return false;
        }
        throw new IllegalArgumentException(location + "不是有效布尔值：" + value + "，请使用 true/false");
    }

    private static Date parseDate(String value, String location) {
        String v = value.trim();
        String[] withTime = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd HH:mm",
                "yyyy年M月d日 HH:mm", "yyyy年M月d日 HH:mm:ss"};
        for (String pattern : withTime) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(v, DateTimeFormatter.ofPattern(pattern));
                return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
            } catch (DateTimeParseException ignored) {
                // try next pattern
            }
        }
        String[] dateOnly = {"yyyy-MM-dd", "yyyy/MM/dd", "yyyy年M月d日"};
        for (String pattern : dateOnly) {
            try {
                LocalDate ld = LocalDate.parse(v, DateTimeFormatter.ofPattern(pattern));
                return Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
            } catch (DateTimeParseException ignored) {
                // try next pattern
            }
        }
        throw new IllegalArgumentException(location + "日期格式无法识别：" + value
                + "，请使用 yyyy-MM-dd、yyyy/MM/dd 或 yyyy-MM-dd HH:mm:ss");
    }

    // ---------- 样式 ----------

    private static CellStyle createHeaderStyle(XSSFWorkbook workbook, ExcelSheetOptions options) {
        byte[] fill = options == null || options.headerColor() == null
                ? DocColor.toRgb(DEFAULT_HEADER_COLOR)
                : DocColor.toRgb(options.headerColor());
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        font.setColor(new XSSFColor(new byte[]{(byte) 255, (byte) 255, (byte) 255}, null));
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(fill, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    // ---------- 列宽 ----------

    private static List<Integer> resolveColumnWidths(ExcelSheetParam sheet) {
        List<Integer> explicit = sheet.options() == null ? null : sheet.options().columnWidths();
        if (explicit != null && explicit.size() == sheet.headers().size()) {
            List<Integer> widths = new ArrayList<>(explicit.size());
            for (Integer width : explicit) {
                widths.add(Math.max(1, Math.min(MAX_COLUMN_WIDTH, width == null ? 10 : width)));
            }
            return widths;
        }
        List<Integer> widths = new ArrayList<>(sheet.headers().size());
        for (int c = 0; c < sheet.headers().size(); c++) {
            widths.add(Math.max(1, Math.min(MAX_COLUMN_WIDTH, estimateWidth(sheet, c))));
        }
        return widths;
    }

    private static int estimateWidth(ExcelSheetParam sheet, int col) {
        int max = displayWidth(sheet.headers().get(col));
        int limit = Math.min(50, sheet.rows().size());
        for (int r = 0; r < limit; r++) {
            ExcelCell cell = sheet.rows().get(r).get(col);
            if (cell != null && cell.value() != null) {
                max = Math.max(max, displayWidth(cell.value()));
            }
        }
        return (int) Math.ceil(max * 1.25);
    }

    private static int displayWidth(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += text.charAt(i) < 0x80 ? 1 : 2;
        }
        return width;
    }

    // ---------- 合并单元格 ----------

    private static void applyMerges(XSSFSheet sheet, ExcelSheetParam param) {
        if (param.options() == null || param.options().merges() == null) {
            return;
        }
        int maxRow = param.rows().size() + 1;
        int maxCol = param.headers().size();
        List<CellRangeAddress> existing = new ArrayList<>(sheet.getMergedRegions());
        for (ExcelMergeRange m : param.options().merges()) {
            int firstRow = clamp(m.rowStart(), 1, maxRow) - 1;
            int lastRow = clamp(m.rowEnd(), 1, maxRow) - 1;
            int firstCol = clamp(m.colStart(), 1, maxCol) - 1;
            int lastCol = clamp(m.colEnd(), 1, maxCol) - 1;
            if (firstRow > lastRow) {
                int t = firstRow;
                firstRow = lastRow;
                lastRow = t;
            }
            if (firstCol > lastCol) {
                int t = firstCol;
                firstCol = lastCol;
                lastCol = t;
            }
            if (firstRow == lastRow && firstCol == lastCol) {
                continue;
            }
            CellRangeAddress region = new CellRangeAddress(firstRow, lastRow, firstCol, lastCol);
            if (overlaps(region, existing)) {
                throw new IllegalArgumentException("工作表 '" + param.sheetName()
                        + "' 合并区域与已有区域重叠：row " + m.rowStart() + "-" + m.rowEnd()
                        + ", col " + m.colStart() + "-" + m.colEnd());
            }
            sheet.addMergedRegion(region);
            existing.add(region);
        }
    }

    private static boolean overlaps(CellRangeAddress a, List<CellRangeAddress> existing) {
        for (CellRangeAddress e : existing) {
            boolean disjoint = a.getLastRow() < e.getFirstRow()
                    || e.getLastRow() < a.getFirstRow()
                    || a.getLastColumn() < e.getFirstColumn()
                    || e.getLastColumn() < a.getFirstColumn();
            if (!disjoint) {
                return true;
            }
        }
        return false;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ---------- 下拉数据验证 ----------

    private static void applyValidations(XSSFSheet sheet, ExcelSheetParam param) {
        if (param.options() == null || param.options().validations() == null) {
            return;
        }
        int maxCol = param.headers().size();
        int lastDataRow = param.rows().size();
        if (lastDataRow < 1) {
            return;
        }
        DataValidationHelper helper = sheet.getDataValidationHelper();
        for (ExcelColumnValidation v : param.options().validations()) {
            if (v == null || v.allowedValues() == null || v.allowedValues().isEmpty()) {
                continue;
            }
            List<String> options = v.allowedValues().stream().filter(Objects::nonNull).map(String::trim)
                    .filter(s -> !s.isEmpty()).toList();
            if (options.isEmpty()) {
                continue;
            }
            long formulaLength = 2;
            for (String option : options) {
                formulaLength += option.replace("\"", "\"\"").length() + 3;
            }
            if (formulaLength > 255) {
                throw new IllegalArgumentException("工作表 '" + param.sheetName()
                        + "' 第 " + v.col() + " 列的下拉选项总长度超过 255 字符限制");
            }
            int col0 = clamp(v.col(), 1, maxCol) - 1;
            DataValidationConstraint constraint = helper.createExplicitListConstraint(
                    options.toArray(new String[0]));
            DataValidation validation = helper.createValidation(constraint,
                    new CellRangeAddressList(1, lastDataRow, col0, col0));
            validation.setEmptyCellAllowed(v.allowBlank() == null || v.allowBlank());
            validation.setShowErrorBox(true);
            sheet.addValidationData(validation);
        }
    }

    // ---------- 表头批注 ----------

    private static void applyColumnNotes(XSSFSheet sheet, ExcelSheetParam param) {
        List<String> notes = param.options() == null ? null : param.options().columnNotes();
        if (notes == null || notes.size() != param.headers().size()) {
            return;
        }
        XSSFDrawing drawing = null;
        for (int c = 0; c < notes.size(); c++) {
            String note = notes.get(c);
            if (note == null || note.isBlank()) {
                continue;
            }
            XSSFRow headerRow = sheet.getRow(0);
            XSSFCell cell = headerRow == null ? null : headerRow.getCell(c);
            if (cell == null) {
                continue;
            }
            if (drawing == null) {
                drawing = sheet.createDrawingPatriarch();
            }
            XSSFComment comment = drawing.createCellComment(
                    new XSSFClientAnchor(0, 0, 0, 0, c, 0, c + 3, 1));
            comment.setString(new XSSFRichTextString(note));
            comment.setAuthor("");
            cell.setCellComment(comment);
        }
    }
}