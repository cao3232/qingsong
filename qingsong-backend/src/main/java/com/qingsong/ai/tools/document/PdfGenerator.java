package com.qingsong.ai.tools.document;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 按 {@link PdfDocumentParam} 渲染 PDF 字节内容：Markdown → HTML（flexmark）→ PDF（openhtmltopdf）。
 * 自动尝试注册本机中文字体，避免中文乱码；找不到字体时英文仍可正常渲染。
 */
public final class PdfGenerator {

    private static final Parser PARSER = Parser.builder()
            .extensions(List.of(TablesExtension.create()))
            .build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().build();

    private static final String FONT_FAMILY = "SystemCJK";

    private static final List<String> CJK_FONT_CANDIDATES = buildFontCandidates();

    private static List<String> buildFontCandidates() {
        List<String> candidates = new ArrayList<>();
        String configured = System.getProperty("app.tools.document.pdfFont");
        if (configured != null && !configured.isBlank()) {
            candidates.add(configured);
        }
        candidates.addAll(List.of(
                "C:/Windows/Fonts/simhei.ttf",
                "C:/Windows/Fonts/msyh.ttc",
                "C:/Windows/Fonts/simsun.ttc",
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"));
        return List.copyOf(candidates);
    }

    private static final String BASE_CSS = """
            * { box-sizing: border-box; }
            body { font-family: 'SystemCJK', 'SimSun', 'SimHei', 'Microsoft YaHei', 'Noto Sans CJK SC', sans-serif;
                   font-size: 11pt; line-height: 1.6; color: #1f2328; margin: 0; }
            h1 { font-size: 20pt; text-align: center; margin: 0 0 16pt; }
            h2 { font-size: 15pt; border-bottom: 1px solid #d0d7de; padding-bottom: 3pt; margin: 18pt 0 8pt; }
            h3 { font-size: 12.5pt; margin: 14pt 0 6pt; }
            h4, h5, h6 { font-size: 11pt; margin: 12pt 0 4pt; }
            p { margin: 6pt 0; }
            ul, ol { margin: 6pt 0; padding-left: 22pt; }
            li { margin: 2pt 0; }
            table { border-collapse: collapse; width: 100%; margin: 8pt 0; }
            th, td { border: 1px solid #b6bcc4; padding: 4pt 6pt; font-size: 10pt; text-align: left; }
            th { background: #f0f3f6; font-weight: bold; }
            code { font-family: 'Consolas', 'Courier New', monospace; background: #f3f4f6;
                   padding: 1pt 3pt; font-size: 9.5pt; border-radius: 2pt; }
            pre { background: #f6f8fa; border: 1px solid #d0d7de; border-radius: 4pt;
                  padding: 8pt; font-size: 9pt; line-height: 1.4; white-space: pre-wrap; }
            pre code { background: transparent; padding: 0; }
            blockquote { border-left: 3pt solid #d0d7de; margin: 8pt 0; padding: 2pt 10pt; color: #57606a; }
            hr { border: none; border-top: 1px solid #d0d7de; margin: 12pt 0; }
            img { max-width: 100%; }
            """;

    private PdfGenerator() {
    }

    public static byte[] generate(PdfDocumentParam param) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(buildHtml(param), "");
            registerCjkFont(builder);
            boolean landscape = param.options() != null && "LANDSCAPE".equalsIgnoreCase(param.options().orientation());
            if (landscape) {
                builder.useDefaultPageSize(297f, 210f, BaseRendererBuilder.PageSizeUnits.MM);
            } else {
                builder.useDefaultPageSize(210f, 297f, BaseRendererBuilder.PageSizeUnits.MM);
            }
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("PDF 文档生成失败", e);
        }
    }

    private static String buildHtml(PdfDocumentParam param) {
        StringBuilder markdown = new StringBuilder();
        if (param.title() != null && !param.title().isBlank()) {
            String title = param.title().replaceAll("[\\r\\n]+", " ").trim();
            markdown.append("# ").append(title).append("\n\n");
        }
        markdown.append(param.content());

        Node document = PARSER.parse(markdown.toString());
        String body = RENDERER.render(document);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/><style>");
        html.append(BASE_CSS);
        if (param.options() != null && param.options().fontSize() != null) {
            html.append("body { font-size: ").append(param.options().fontSize()).append("pt; }");
        }
        if (param.options() != null && param.options().css() != null && !param.options().css().isBlank()) {
            html.append(param.options().css());
        }
        html.append("</style></head><body>");
        html.append(body);
        html.append("</body></html>");
        return html.toString();
    }

    private static void registerCjkFont(PdfRendererBuilder builder) {
        File font = findCjkFont();
        if (font == null) {
            return;
        }
        try {
            builder.useFont(font, FONT_FAMILY);
        } catch (Exception ignored) {
            // 字体注册失败时退化为无中文字体渲染
        }
    }

    private static File findCjkFont() {
        for (String candidate : CJK_FONT_CANDIDATES) {
            if (candidate == null) {
                continue;
            }
            File file = new File(candidate);
            if (file.isFile()) {
                return file;
            }
        }
        return null;
    }
}