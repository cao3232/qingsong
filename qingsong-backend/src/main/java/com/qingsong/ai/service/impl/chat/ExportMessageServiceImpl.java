package com.qingsong.ai.service.impl;

import com.qingsong.ai.service.ExportMessageService;
import com.qingsong.ai.service.chat.ChatPersistenceService;
import com.qingsong.ai.utils.PdfUtils;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.toc.TocExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * description...
 *
 * @author : caojiangjiang
 * @data : 2025/07/12 17:06
 */
@Service
public class ExportMessageServiceImpl implements ExportMessageService {

    @Autowired
    private ChatPersistenceService chatPersistenceService;

    private static final Parser PARSER;

    static {
        MutableDataSet options = new MutableDataSet();
        PARSER = Parser.builder(options).build();
    }

    @Override
    public byte[] exportMessageWithPdf(String chatId, String title) throws IOException {
        // 生成PDF
        // String font = "Unifont";
        // String font = "OPPO Sans 4.0";
        String font = "LXGWWenKai-Regular";
        return PdfUtils.generatePdfFromHtml(exportMessageWithHtml(chatId, title, font), font);

    }

    @Override
    public String exportMessageWithHtml(String chatId, String title, String font) {
        if (StringUtils.isEmpty(font)) {
            font = "LXGWWenKai-Regular";
        }
        // 获取消息内容
        List<Message> messages = chatPersistenceService.getAllMessages(chatId);

        if (messages == null || messages.isEmpty()) {
            throw new RuntimeException("没有消息");
        }

        StringBuilder htmlBuilder = new StringBuilder();

        // --- 代码修改区域 START ---

        // 1. 【安全修改】对传入的 title 进行HTML转义
        String safeTitle = title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        htmlBuilder.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/><title>与").append(safeTitle).append("的聊天记录</title>");

        // 2. 【样式修改】全宽PDF优化 + 均匀边框 + 新配色(紫/青)
        htmlBuilder.append("<style>");

        // 【关键】PDF页面设置：窄边距，A4纵向
        htmlBuilder.append("@page { margin: 6mm; size: A4 portrait; }");

        // 基础：零边距充分利用宽度，舒适行高
        htmlBuilder.append("body { font-family: '" + font + "', sans-serif; margin: 0; padding: 0; font-size: 10.5pt; line-height: 1.75; color: #1f2937; background: #fff; }");

        // Header：极简文档头，底部粗线分隔
        htmlBuilder.append(".doc-header { padding: 4mm 2mm 3mm 2mm; margin-bottom: 5mm; border-bottom: 1pt solid #1f2937; }");
        htmlBuilder.append(".doc-title { font-size: 20pt; font-weight: bold; color: #111827; margin: 0 0 2mm 0; letter-spacing: -0.3pt; }");
        htmlBuilder.append(".doc-meta { font-size: 8.5pt; color: #6b7280; }");
        htmlBuilder.append(".doc-meta strong { color: #374151; font-weight: 600; }");
        htmlBuilder.append(".doc-meta .meta-item { margin-right: 5mm; display: inline-block; }"); // 用CSS代替&nbsp;

        // 消息容器：统一细边框，顶部彩色粗条区分角色
        htmlBuilder.append(".message { margin-bottom: 4mm; padding: 3mm 4mm; border: 0.5pt solid #d1d5db; page-break-inside: avoid; }");

        // 新配色方案：用户(紫罗兰) vs 助手(青绿)
        htmlBuilder.append(".user-message { border-top: 2.5pt solid #7c3aed; background: #fafaf9; }");
        htmlBuilder.append(".assistant-message { border-top: 2.5pt solid #0d9488; background: #fafaf9; }");

        // 角色标签：小字、大写、颜色匹配顶部
        htmlBuilder.append(".role { font-size: 7.5pt; font-weight: bold; text-transform: uppercase; letter-spacing: 0.8pt; margin-bottom: 2mm; }");
        htmlBuilder.append(".user-message .role { color: #7c3aed; }");
        htmlBuilder.append(".assistant-message .role { color: #0d9488; }");
        htmlBuilder.append(".content { word-wrap: break-word; }");

        // 代码块：统一边框，浅灰背景，清晰但不突兀
        htmlBuilder.append("pre { border: 0.5pt solid #d1d5db; border-left: 2pt solid #6b7280; padding: 3mm; background: #f9fafb; font-family: '" + font + "', monospace; font-size: 9.5pt; line-height: 1.5; overflow-x: auto; white-space: pre-wrap; word-wrap: break-word; margin: 3mm 0; color: #111827; }");
        htmlBuilder.append("code { font-family: '" + font + "', monospace; font-size: 9.5pt; background: #f3f4f6; padding: 0.5mm 1.5mm; border-radius: 1pt; color: #1f2937; }");
        htmlBuilder.append("pre > code { background: transparent; padding: 0; border-radius: 0; }");

        // 表格：均匀细边框，专业三线表风格
        htmlBuilder.append("table { width: 100%; border-collapse: collapse; margin: 3mm 0; font-size: 10pt; border: 0.5pt solid #d1d5db; }");
        htmlBuilder.append("thead { border-bottom: 1pt solid #9ca3af; background: #f9fafb; }");
        htmlBuilder.append("th, td { padding: 2mm 3mm; text-align: left; border-bottom: 0.5pt solid #e5e7eb; }");
        htmlBuilder.append("th { font-weight: 600; color: #111827; }");
        htmlBuilder.append("tr:last-child td { border-bottom: none; }");

        // 标题层次：清晰的递进，统一深色
        htmlBuilder.append("h1 { font-size: 16pt; margin: 5mm 0 3mm 0; color: #111827; font-weight: bold; border-bottom: 0.5pt solid #d1d5db; padding-bottom: 2mm; }");
        htmlBuilder.append("h2 { font-size: 13pt; margin: 4mm 0 2mm 0; color: #1f2937; font-weight: bold; }");
        htmlBuilder.append("h3 { font-size: 11.5pt; margin: 3mm 0 2mm 0; color: #374151; font-weight: 600; }");
        htmlBuilder.append("h4, h5, h6 { font-size: 10.5pt; margin: 2mm 0 1mm 0; color: #4b5563; font-weight: 600; }");

        // 列表：均匀缩进
        htmlBuilder.append("ul, ol { padding-left: 5mm; margin: 2mm 0; }");
        htmlBuilder.append("li { margin-bottom: 1mm; }");
        htmlBuilder.append("li > ul, li > ol { margin: 1mm 0; }");
        htmlBuilder.append("p { margin: 0 0 2.5mm 0; }");

        // 引用块：均匀左边框，浅背景
        htmlBuilder.append("blockquote { margin: 3mm 0; padding: 2mm 4mm; border-left: 2pt solid #9ca3af; background: #f9fafb; color: #4b5563; }");

        // 水平线：极细均匀
        htmlBuilder.append("hr { border: none; border-top: 0.5pt solid #e5e7eb; margin: 4mm 0; }");

        // 打印保护
        htmlBuilder.append("h1, h2, h3, .message { page-break-inside: avoid; }");
        htmlBuilder.append("</style>");
        htmlBuilder.append("</head><body>");

        // Header区域：简洁信息展示（移除&nbsp;，改用CSS控制间距）
        htmlBuilder.append("<div class=\"doc-header\">");
        htmlBuilder.append("<div class=\"doc-title\">").append(safeTitle).append("</div>");
        htmlBuilder.append("<div class=\"doc-meta\">");
        htmlBuilder.append("<span class=\"meta-item\">会话ID: <strong>").append(chatId).append("</strong></span>");
        htmlBuilder.append("<span class=\"meta-item\">导出时间: <strong>").append(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date())).append("</strong></span>");
        htmlBuilder.append("</div>");
        htmlBuilder.append("</div>");

        for (Message message : messages) {
            String content = message.getText() != null ? message.getText() : "";
            String messageClass;
            String roleText;

            // 3. 逻辑与安全部分保持不变
            if (message.getMessageType() == MessageType.USER) {
                messageClass = "user-message";
                roleText = "用户";
                content = content.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
            } else if (message.getMessageType() == MessageType.ASSISTANT) {
                messageClass = "assistant-message";
                roleText = "助手";
                content = markdownToPlainText(content);
            } else {
                messageClass = "";
                roleText = "未知";
                content = content.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
            }

            htmlBuilder.append("<div class=\"message ").append(messageClass).append("\">");
            htmlBuilder.append("<div class=\"role\">").append(roleText).append("</div>");
            htmlBuilder.append("<div class=\"content\">").append(content).append("</div>");
            htmlBuilder.append("</div>");
        }

        htmlBuilder.append("</body></html>");

        // --- 代码修改区域 END ---

        return htmlBuilder.toString();
    }

    /**
     * 将Markdown文本转换为纯文本，保留基本结构
     *
     * @param markdownText Markdown文本
     * @return 纯文本
     */
    private String markdownToPlainText(String markdownText) {
        if (markdownText == null || markdownText.isEmpty()) {
            return "";
        }
        MutableDataSet options = new MutableDataSet();
        // TOC目录解析,表格解析
        options.set(Parser.EXTENSIONS, Arrays.asList(TocExtension.create(), TablesExtension.create()));

        Parser parser = Parser.builder(options).build();
        Document document = parser.parse(markdownText);

        HtmlRenderer renderer = HtmlRenderer.builder(options).build();
        return renderer.render(document);
    }

}
