package com.qingsong.ai.utils;

import com.qingsong.ai.entity.vo.MessageVO;
import com.openhtmltopdf.extend.FSSupplier;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * PDF生成工具类
 * 实现聊天记录导出为带格式的PDF文档功能
 * 包含页面布局、消息样式、字体处理等核心功能
 *
 * @author qingsong
 * @date 2023/10/10
 */
public class PdfUtils {

    /**
     * 生成带格式的PDF文档
     *
     * @param htmlContent 包含消息的HTML内容
     * @return 生成的PDF文档字节数组
     * @throws IOException 如果IO操作失败
     */
    public static byte[] generatePdfFromHtml(String htmlContent, String font) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(htmlContent, null);

            String fontPath = "/fonts/" + font + ".ttf";
            FSSupplier<InputStream> fontSupplier = () -> {
                try {
                    InputStream is = PdfUtils.class.getResourceAsStream(fontPath);
                    if (is == null) {
                        File fontFile = new File("src/main/resources/fonts/" + font + ".ttf");
                        if (fontFile.exists()) {
                            return new FileInputStream(fontFile);
                        } else {
                            throw new IOException("字体文件未找到：" + fontPath);
                        }
                    }
                    return is;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            };

            builder.useFont(fontSupplier, font);
            builder.toStream(os);
            builder.run();

            return os.toByteArray();
        } catch (Exception e) {
            System.err.println("PDF 生成失败：" + e.getMessage());
            e.printStackTrace();
            throw new IOException("PDF 生成失败", e);
        } finally {
            os.close();
        }
    }
}
