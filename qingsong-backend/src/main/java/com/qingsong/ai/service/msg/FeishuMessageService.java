package com.qingsong.ai.service.msg;

import com.qingsong.ai.entity.vo.FeishuMsgRespVO;

/**
 * 飞书消息服务接口
 *
 * @author : caojiangjiang
 * @since 2025/07/13
 */
public interface FeishuMessageService {

    /**
     * 发送单条消息
     *
     * @param message 消息内容
     * @return 响应结果
     */
    FeishuMsgRespVO sendSingleMessage(String message);

    /**
     * 发送用户消息
     *
     * @param message 消息内容
     * @param user    用户ID
     * @return 响应结果
     */
    FeishuMsgRespVO sendUserMessage(String message, String user);

    /**
     * 发送用户 Markdown 消息
     *
     * @param message 消息内容
     * @param user    用户ID
     * @return 响应结果
     */
    FeishuMsgRespVO sendUserMdMessage(String message, String user);

    /**
     * 发送聊天消息
     *
     * @param role   角色
     * @param chatId 会话ID
     * @return 是否成功
     */
    Boolean sendChatMsg(String role, String chatId);
}
