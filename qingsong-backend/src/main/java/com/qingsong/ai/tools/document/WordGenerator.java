package com.qingsong.ai.tools.document;

import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFldChar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPBdr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STHdrFtr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * 按 {@link WordDocumentParam} 渲染 .docx 字节内容。
 * 用户输入数据问题（非法颜色等）抛出 IllegalArgumentException，由工具层转为失败结果。
 */
public final class WordGenerator {

    private static final int POINTS_TO_TWIPS = 20;
    private static final int QUOTE_INDENT_TWIPS = 360;
    private static final int BULLET_INDENT_TWIPS = 360;
    private static final String QUOTE_COLOR = "595959";

    private WordGenerator() {
    }

    public static byte[] generate(WordDocumentParam param) {
        try (XWPFDocument document = new XWPFDocument()) {
            RenderState state = new RenderState(document);
            prepareNumbering(state, param.blocks());
            state.headings = collectHeadings(param.blocks());
            applyPageSetup(document, param.options());
            if (hasPageNumbers(param.options())) {
                addFooterPageNumbers(document);
            }
            addTitle(document, param.title());
            for (WordBlock block : param.blocks()) {
                addBlock(state, block);
            }
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                document.write(out);
                return out.toByteArray();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Word 文档生成失败", e);
        }
    }

    // ---------- 页面设置 ----------

    private static void applyPageSetup(XWPFDocument document, WordDocumentOptions options) {
        CTBody body = document.getDocument().getBody();
        CTSectPr sectPr = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();
        if (sectPr.isSetPgSz()) {
            sectPr.unsetPgSz();
        }
        boolean landscape = options != null && "LANDSCAPE".equalsIgnoreCase(options.orientation());
        CTPageSz pgSz = sectPr.addNewPgSz();
        pgSz.setOrient(landscape ? STPageOrientation.LANDSCAPE : STPageOrientation.PORTRAIT);
        if (landscape) {
            pgSz.setW(Long.valueOf(16838L));
            pgSz.setH(Long.valueOf(11906L));
        } else {
            pgSz.setW(Long.valueOf(11906L));
            pgSz.setH(Long.valueOf(16838L));
        }
    }

    private static boolean hasPageNumbers(WordDocumentOptions options) {
        return options != null && Boolean.TRUE.equals(options.pageNumbers());
    }

    private static void addFooterPageNumbers(XWPFDocument document) {
        XWPFHeaderFooterPolicy policy = document.createHeaderFooterPolicy();
        var footer = policy.createFooter(STHdrFtr.DEFAULT);
        XWPFParagraph paragraph = footer.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun before = paragraph.createRun();
        before.setText("第 ");
        addPageFieldRun(paragraph);
        XWPFRun after = paragraph.createRun();
        after.setText(" 页");
    }

    private static void addPageFieldRun(XWPFParagraph paragraph) {
        XWPFRun run = paragraph.createRun();
        CTR ctr = run.getCTR();
        CTFldChar begin = ctr.addNewFldChar();
        begin.setFldCharType(STFldCharType.BEGIN);
        CTText instr = ctr.addNewInstrText();
        instr.setStringValue(" PAGE ");
        CTFldChar separate = ctr.addNewFldChar();
        separate.setFldCharType(STFldCharType.SEPARATE);
        CTText placeholder = ctr.addNewT();
        placeholder.setStringValue("1");
        CTFldChar end = ctr.addNewFldChar();
        end.setFldCharType(STFldCharType.END);
    }

    // ---------- 标题与内容块 ----------

    private static void addTitle(XWPFDocument document, String title) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setStyle("Title");
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontSize(20);
        run.setText(title);
    }

    private static void addBlock(RenderState state, WordBlock block) {
        XWPFDocument document = state.document;
        if (block instanceof WordHeadingBlock heading) {
            addHeading(document, heading);
        } else if (block instanceof WordParagraphBlock paragraph) {
            addRichTextParagraph(document, paragraph.text(), paragraph.style(), null);
        } else if (block instanceof WordBulletBlock bullet) {
            addRichTextParagraph(document, "• " + bullet.text(), bullet.style(), BULLET_INDENT_TWIPS);
        } else if (block instanceof WordNumberedBlock numbered) {
            addNumbered(document, numbered.text(), numbered.style(), state.numId);
        } else if (block instanceof WordQuoteBlock quote) {
            addQuote(document, quote.text());
        } else if (block instanceof WordTableBlock table) {
            addTable(document, table.data());
        } else if (block instanceof WordPageBreakBlock) {
            addPageBreak(document);
        } else if (block instanceof WordDividerBlock) {
            addDivider(document);
        } else if (block instanceof WordTocBlock) {
            addToc(document, state.headings);
        }
    }

    private static void addHeading(XWPFDocument document, WordHeadingBlock heading) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setStyle("Heading" + heading.level());
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setText(heading.text());
    }

    private static void addNumbered(XWPFDocument document, String text, WordTextStyle style, BigInteger numId) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setNumILvl(BigInteger.ZERO);
        paragraph.setNumID(numId);
        XWPFRun run = paragraph.createRun();
        applyRunStyle(run, style);
        applyParagraphStyle(paragraph, style);
        run.setText(text);
    }

    private static void addRichTextParagraph(XWPFDocument document, String text, WordTextStyle style,
                                             Integer indentTwips) {
        XWPFParagraph paragraph = document.createParagraph();
        if (indentTwips != null) {
            paragraph.setIndentationLeft(indentTwips);
        }
        XWPFRun run = paragraph.createRun();
        applyRunStyle(run, style);
        applyParagraphStyle(paragraph, style);
        run.setText(text);
    }

    private static void addQuote(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setIndentationLeft(QUOTE_INDENT_TWIPS);
        XWPFRun run = paragraph.createRun();
        run.setItalic(true);
        run.setColor(QUOTE_COLOR);
        run.setText(text);
    }

    private static void addTable(XWPFDocument document, WordTableData data) {
        int cols = data.headers().size();
        XWPFTable table = document.createTable(data.rows().size() + 1, cols);
        setTableBorders(table);
        for (int c = 0; c < cols; c++) {
            writeTableHeaderCell(table.getRow(0).getCell(c), data.headers().get(c));
        }
        for (int r = 0; r < data.rows().size(); r++) {
            List<String> rowData = data.rows().get(r);
            for (int c = 0; c < cols; c++) {
                writeTableCell(table.getRow(r + 1).getCell(c), rowData.get(c));
            }
        }
    }

    private static void writeTableHeaderCell(XWPFTableCell cell, String text) {
        cell.setColor("D9E2F3");
        XWPFParagraph paragraph = cell.getParagraphs().get(0);
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setText(text);
    }

    private static void writeTableCell(XWPFTableCell cell, String text) {
        XWPFParagraph paragraph = cell.getParagraphs().get(0);
        paragraph.createRun().setText(text == null ? "" : text);
    }

    private static void setTableBorders(XWPFTable table) {
        CTTblPr tblPr = table.getCTTbl().getTblPr();
        if (tblPr == null) {
            tblPr = table.getCTTbl().addNewTblPr();
        }
        CTTblBorders borders = tblPr.isSetTblBorders() ? tblPr.getTblBorders() : tblPr.addNewTblBorders();
        setSingleBorder(borders.addNewTop());
        setSingleBorder(borders.addNewBottom());
        setSingleBorder(borders.addNewLeft());
        setSingleBorder(borders.addNewRight());
        setSingleBorder(borders.addNewInsideH());
        setSingleBorder(borders.addNewInsideV());
    }

    private static void setSingleBorder(CTBorder border) {
        border.setVal(STBorder.SINGLE);
        border.setSz(BigInteger.valueOf(4));
        border.setColor("auto");
    }

    private static void addPageBreak(XWPFDocument document) {
        document.createParagraph().createRun().addBreak(BreakType.PAGE);
    }

    private static void addDivider(XWPFDocument document) {
        XWPFParagraph paragraph = document.createParagraph();
        CTPPr pPr = paragraph.getCTPPr();
        CTPBdr pBdr = pPr.isSetPBdr() ? pPr.getPBdr() : pPr.addNewPBdr();
        CTBorder bottom = pBdr.isSetBottom() ? pBdr.getBottom() : pBdr.addNewBottom();
        bottom.setVal(STBorder.SINGLE);
        bottom.setSz(BigInteger.valueOf(12));
        bottom.setColor("000000");
    }

    private static void addToc(XWPFDocument document, List<HeadingEntry> headings) {
        if (headings.isEmpty()) {
            return;
        }
        XWPFParagraph title = document.createParagraph();
        XWPFRun titleRun = title.createRun();
        titleRun.setBold(true);
        titleRun.setText("目录");
        for (HeadingEntry entry : headings) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setIndentationLeft((entry.level() - 1) * 200);
            paragraph.createRun().setText(entry.text());
        }
    }

    private static List<HeadingEntry> collectHeadings(List<WordBlock> blocks) {
        List<HeadingEntry> headings = new ArrayList<>();
        for (WordBlock block : blocks) {
            if (block instanceof WordHeadingBlock heading && heading.text() != null && !heading.text().isBlank()) {
                headings.add(new HeadingEntry(heading.level() == null ? 1 : heading.level(), heading.text()));
            }
        }
        return headings;
    }

    // ---------- 样式 ----------

    private static void applyRunStyle(XWPFRun run, WordTextStyle style) {
        if (style == null) {
            return;
        }
        if (style.fontFamily() != null) {
            run.setFontFamily(style.fontFamily());
            setEastAsianFont(run, style.fontFamily());
        }
        if (style.fontSize() != null) {
            run.setFontSize(style.fontSize());
        }
        if (style.bold() != null) {
            run.setBold(style.bold());
        }
        if (style.italic() != null) {
            run.setItalic(style.italic());
        }
        if (style.underline() != null) {
            run.setUnderline(style.underline() ? UnderlinePatterns.SINGLE : UnderlinePatterns.NONE);
        }
        if (style.color() != null) {
            run.setColor(DocColor.toHex6(style.color()));
        }
        if (style.highlight() != null) {
            run.setTextHighlightColor(DocColor.toHex6(style.highlight()));
        }
    }

    private static void setEastAsianFont(XWPFRun run, String fontFamily) {
        CTRPr rpr = run.getCTR().isSetRPr() ? run.getCTR().getRPr() : run.getCTR().addNewRPr();
        CTFonts fonts = rpr.sizeOfRFontsArray() > 0 ? rpr.getRFontsArray(0) : rpr.addNewRFonts();
        fonts.setEastAsia(fontFamily);
    }

    private static void applyParagraphStyle(XWPFParagraph paragraph, WordTextStyle style) {
        if (style == null) {
            return;
        }
        if (style.align() != null) {
            paragraph.setAlignment(toParagraphAlignment(style.align()));
        }
        if (style.indentLeft() != null) {
            paragraph.setIndentationLeft(Math.max(0, (int) Math.round(style.indentLeft() * POINTS_TO_TWIPS)));
        }
        if (style.spaceBefore() != null) {
            paragraph.setSpacingBefore(Math.max(0, (int) Math.round(style.spaceBefore() * POINTS_TO_TWIPS)));
        }
        if (style.spaceAfter() != null) {
            paragraph.setSpacingAfter(Math.max(0, (int) Math.round(style.spaceAfter() * POINTS_TO_TWIPS)));
        }
        if (style.lineSpacing() != null) {
            CTPPr pPr = paragraph.getCTPPr();
            CTSpacing spacing = pPr.isSetSpacing() ? pPr.getSpacing() : pPr.addNewSpacing();
            spacing.setLine(BigInteger.valueOf(Math.max(1, Math.round(style.lineSpacing() * 240))));
            spacing.setLineRule(STLineSpacingRule.AUTO);
        }
    }

    private static ParagraphAlignment toParagraphAlignment(WordTextAlign align) {
        return switch (align) {
            case LEFT -> ParagraphAlignment.LEFT;
            case CENTER -> ParagraphAlignment.CENTER;
            case RIGHT -> ParagraphAlignment.RIGHT;
            case JUSTIFY -> ParagraphAlignment.BOTH;
        };
    }

    // ---------- 编号列表 ----------

    private static void prepareNumbering(RenderState state, List<WordBlock> blocks) {
        boolean requiresNumbering = blocks.stream().anyMatch(block -> block instanceof WordNumberedBlock);
        if (!requiresNumbering) {
            return;
        }
        XWPFNumbering numbering = state.document.createNumbering();
        CTAbstractNum ctAbstractNum = CTAbstractNum.Factory.newInstance();
        ctAbstractNum.setAbstractNumId(BigInteger.ONE);
        CTLvl lvl = ctAbstractNum.addNewLvl();
        lvl.setIlvl(BigInteger.ZERO);
        lvl.addNewStart().setVal(BigInteger.ONE);
        lvl.addNewNumFmt().setVal(STNumberFormat.DECIMAL);
        lvl.addNewLvlText().setVal("%1.");
        XWPFAbstractNum abstractNum = new XWPFAbstractNum(ctAbstractNum);
        BigInteger abstractNumId = numbering.addAbstractNum(abstractNum);
        state.numId = numbering.addNum(abstractNumId);
    }

    // ---------- 内部结构 ----------

    private record HeadingEntry(int level, String text) {
    }

    private static final class RenderState {
        final XWPFDocument document;
        BigInteger numId = BigInteger.ONE;
        List<HeadingEntry> headings = List.of();

        RenderState(XWPFDocument document) {
            this.document = document;
        }
    }
}