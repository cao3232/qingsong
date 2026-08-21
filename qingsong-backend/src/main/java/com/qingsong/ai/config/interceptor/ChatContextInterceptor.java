package com.qingsong.ai.config.interceptor;

import cn.dev33.satoken.stp.StpUtil;
import com.qingsong.ai.context.ChatContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Map;

/**
 * 聊天上下文拦截器
 * 从请求参数中提取role和chatId，设置到ThreadLocal中
 *
 * @author caojiangjiang
 * @date 2026/02/24
 */
@Slf4j
@Component
public class ChatContextInterceptor implements AsyncHandlerInterceptor {

    // 请求参数名称
    private static final String ROLE_PARAM = "role";
    private static final String CHAT_ID_PARAM = "chatId";
    private static final String TYPE_PARAM = "type";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        System.out.println("请求的路径URI为:"+request.getRequestURI());
        System.out.println("请求的路径URL为:"+request.getRequestURL());
        // System.out.println("loginId:" + StpUtil.getLoginId());
        if (!request.getRequestURI().equals("chat")) {
            return true;
        }

        // 1. 首先尝试从请求参数中获取
        String role = request.getParameter(ROLE_PARAM);
        String chatId = request.getParameter(CHAT_ID_PARAM);
        String type = request.getParameter(TYPE_PARAM);

        // 2. 如果参数中没有，则尝试从路径变量中获取
        if (StringUtils.isBlank(role) || StringUtils.isBlank(chatId)) {
            Map<String, String> pathVariables = (Map<String, String>) request
                    .getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

            if (pathVariables != null) {
                // 从路径变量中提取
                if (StringUtils.isBlank(role)) {
                    role = pathVariables.get("role");
                }
                if (StringUtils.isBlank(chatId)) {
                    chatId = pathVariables.get("chatId");
                }
                if (StringUtils.isBlank(type)) {
                    type = pathVariables.get("type");
                }

                // 特殊处理：如果URL格式为 /ai/history/{type}/{role}/{chatId}
                if (StringUtils.isBlank(type) && StringUtils.isBlank(role) && StringUtils.isNotBlank(chatId)) {
                    // 可能是三段式URL，需要重新解析
                    String requestUri = request.getRequestURI();
                    String contextPath = request.getContextPath();
                    String servletPath = requestUri.substring(contextPath.length());

                    // 解析类似 /ai/history/chat/javaCURD练习列表/1769391927970 的路径
                    String[] pathParts = servletPath.split("/");
                    if (pathParts.length >= 5) { // /ai/history/type/role/chatId
                        type = pathParts[3];
                        role = pathParts[4];
                        if (pathParts.length > 5) {
                            chatId = pathParts[5];
                        }
                    }
                }
            }
        }

        // 3. 设置上下文
        if (StringUtils.isNotBlank(role) && StringUtils.isNotBlank(chatId)) {
            if (StringUtils.isBlank(type)) {
                type = "chat"; // 默认业务类型
            }

            ChatContext.setContext(role, chatId, type);
            log.debug("设置聊天上下文: role={}, chatId={}, type={}", role, chatId, type);
        } else {
            log.debug("请求中缺少聊天上下文参数: role={}, chatId={}", role, chatId);
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) throws Exception {
        // 请求完成后清除ThreadLocal，防止内存泄漏
        ChatContext.clear();
        log.debug("清除聊天上下文");
    }
}
