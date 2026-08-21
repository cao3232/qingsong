package com.qingsong.ai.service;

import java.io.IOException;

public interface ExportMessageService {
    byte[] exportMessageWithPdf(String chatId, String title) throws IOException;

    String exportMessageWithHtml(String chatId, String title, String font);
}
