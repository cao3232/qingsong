package com.qingsong.ai.service.impl;

import com.qingsong.ai.service.EmailService;
import com.qingsong.ai.service.ExportMessageService;
import com.qingsong.ai.utils.DateUtils;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Date;
import java.util.Map;

/**
 * description...
 *
 * @author : caojiangjiang
 * @data : 2025/05/12 22:20
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    @Autowired
    private JavaMailSender javaMailSender;

    @Autowired
    private TemplateEngine templateEngine;

    @Value("${spring.mail.username}") // 从配置文件读取发件人邮箱
    private String fromEmail;

    @Value("${mail.export-to}") // 聊天「发送邮件」导出收件人（配置见 application.yaml / secrets.yml）
    private String exportToEmail;

    @Override
    public boolean sendSimpleMail(String to, String subject, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        // 设置发件人邮箱
        message.setFrom(fromEmail);
        // 设置收件人邮箱
        message.setTo(to);
        // 设置邮件主题
        message.setSubject(subject);
        // 设置邮件内容
        message.setText(content);

        try {
            javaMailSender.send(message);
            log.info("简单邮件发送成功: From '{}' To '{}', Subject '{}'", fromEmail, to, subject);
            return true;
        } catch (MailException e) {
            log.error("发送简单邮件时发生异常！From '{}' To '{}', Subject '{}'", fromEmail, to, subject, e);
            return false;
        }
    }

    /**
     * 发送简单的文本邮件
     *
     * @param to      收件人邮箱地址
     * @param subject 邮件主题
     * @param content 邮件内容（纯文本）
     */
    @Override
    public boolean sendSimpleMail(String to, String subject, boolean isHtml, String content) throws MessagingException {

        // true表示支持复杂类型
        MimeMessageHelper messageHelper = new MimeMessageHelper(javaMailSender.createMimeMessage(), true);
        // 邮件发信人
        messageHelper.setFrom(fromEmail);
        // 邮件收信人
        messageHelper.setTo(to.split(","));
        // 邮件主题
        messageHelper.setSubject(subject);
        // 邮件内容
        messageHelper.setText(content, isHtml);
        // 邮件发送时间
        messageHelper.setSentDate(new Date());

        try {
            javaMailSender.send(messageHelper.getMimeMessage());
            log.info("简单邮件发送成功: From '{}' To '{}', Subject '{}'", fromEmail, to, subject);
            return true;
        } catch (MailException e) {
            log.error("发送简单邮件时发生异常！From '{}' To '{}', Subject '{}'", fromEmail, to, subject, e);
            return false;
        }
    }

    @Override
    public Boolean sendEmailHtml(String role, String chatId, ExportMessageService exportMessageService) throws MessagingException {
        String htmlContent = exportMessageService.exportMessageWithHtml(chatId, role, null);
        return this.sendSimpleMail(exportToEmail, role + "-" + DateUtils.getCurrentFormatDateTime() + "-导出", true, htmlContent);
    }

    /**
     * 发送 HTML 模板邮件
     *
     * @param to        接收人
     * @param subject   主题
     * @param variables 模板变量映射
     */
    @Override
    public void sendTemplateMail(String to, String subject, Map<String, Object> variables) {
        try {
            // 1. 构建 Thymeleaf 上下文
            Context context = new Context();
            context.setVariables(variables);

            // 2. 解析模板，生成 HTML 字符串
            String emailContent = templateEngine.process("email-template", context);

            sendTemplateMail(to, subject, emailContent);
        } catch (MessagingException e) {
            log.error("发送模板邮件失败: {}", e.getMessage());
            // 实际生产中这里应有重试逻辑或死信队列处理
        }
    }

    @Override
    public void sendTemplateMail(String to, String subject, String emailContent) throws MessagingException {
        // 3. 构建 MimeMessage
        MimeMessage message = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(emailContent, true); // true 表示这是 HTML

        // 4. 发送
        javaMailSender.send(message);
        log.info("邮件已成功发送至: {}", to);
    }

}
