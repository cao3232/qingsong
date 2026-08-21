package com.qingsong.ai.tools;

// import cn.hutool.extra.template.TemplateEngine;

import com.qingsong.ai.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.Map;

/**
 * description...
 *
 * @author : caojiangjiang
 * @data : 2026/03/22 23:25
 */
@Component
@Slf4j
public class EmailTool {
    //
    // @Autowired
    // private TemplateEngine templateEngine; // 注入 Thymeleaf 引擎

    @Autowired
    private EmailService emailService;

    @Value("${mail.agent-to}") // 收件人（配置见 application.yaml / secrets.yml）
    private String agentToEmail;

    @Tool
    public String sendEmail(String emailTemplate, Map<String, Object> varables, String subject) {
        // 模拟发送邮件
        try {
            TemplateEngine engine = new TemplateEngine();
            // 专门设置一个只处理字符串的解析器
            StringTemplateResolver resolver = new StringTemplateResolver();
            resolver.setTemplateMode("HTML");
            engine.setTemplateResolver(resolver);
            // String emailContent = templateEngine.getTemplate(emailTemplate).render(varables);
            Context context = new Context();
            context.setVariables(varables);
            String emailContent = engine.process(emailTemplate, context);
            emailService.sendTemplateMail(agentToEmail, subject, emailContent);
        } catch (Exception e) {
            log.error("Email sent failed {}", e.getMessage(), e);
            return "Email sent failed" + e.getMessage();
        }
        return "Email sent success" + System.currentTimeMillis();
    }

}
