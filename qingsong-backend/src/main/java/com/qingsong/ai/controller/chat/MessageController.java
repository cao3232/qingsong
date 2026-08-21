package com.qingsong.ai.controller.chat;

import com.qingsong.ai.entity.vo.FeishuMessageVO;
import com.qingsong.ai.entity.vo.Result;
import com.qingsong.ai.repository.ChatMemoryRepository;
import com.qingsong.ai.service.EmailService;
import com.qingsong.ai.service.ExportMessageService;
import com.qingsong.ai.service.msg.FeishuMessageService;
import jakarta.mail.MessagingException;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * description...
 *
 * @author : caojiangjiang
 * @data : 2025/07/13 12:09
 */
@RestController
@RequestMapping("/message")
@AllArgsConstructor
public class MessageController {

    private final EmailService emailService;
    private final ExportMessageService exportMessageService;
    private final FeishuMessageService feishuMessageService;
    private final ChatMemoryRepository chatMemoryRepository;

    @RequestMapping("/send-email-html/{role}/{chatId}")
    public Boolean sendEmailHtml(@PathVariable String role, @PathVariable String chatId) throws MessagingException {
        return emailService.sendEmailHtml(role, chatId, exportMessageService);
    }

    @RequestMapping(value = "/send-feishu-msg", method = RequestMethod.POST)
    public Result sendFeishuMsg(@RequestBody FeishuMessageVO messageVO) {
        feishuMessageService.sendUserMdMessage(messageVO.getMessage(), messageVO.getUser());
        return Result.ok();
    }

    // @DeleteMapping("/delete-chat/{role}/{chatId}/{}")
    // public Boolean deleteChat(@PathVariable String role, @PathVariable String chatId) {
    //     return chatMemoryRepository.deleteSequenceChat(role, chatId);
    // }


}
