package com.qingsong.ai.service;

import jakarta.mail.MessagingException;
import org.springframework.scheduling.annotation.Async;

import java.util.Map;

public interface EmailService {
    boolean sendSimpleMail(String to, String subject, String content);

    boolean sendSimpleMail(String to, String subject, boolean isHtml, String content) throws MessagingException;

    Boolean sendEmailHtml(String role, String chatId, ExportMessageService exportMessageService) throws MessagingException;

    void sendTemplateMail(String to, String subject, Map<String, Object> variables);

    void sendTemplateMail(String to, String subject, String emailContent) throws MessagingException;
}
