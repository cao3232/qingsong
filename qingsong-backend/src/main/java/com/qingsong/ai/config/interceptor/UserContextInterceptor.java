package com.qingsong.ai.config.interceptor;

import cn.dev33.satoken.stp.StpUtil;
import com.qingsong.ai.context.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.DispatcherType;

/**
 * 用户上下文拦截器
 * 从请求头或参数中提取用户信息，设置到ThreadLocal中
 *
 * @author : caojiangjiang
 * @data : 2026/04/26
 */
@Slf4j
@Component
public class UserContextInterceptor implements AsyncHandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (request.getDispatcherType() != DispatcherType.REQUEST
                || "OPTIONS".equalsIgnoreCase(request.getMethod())
                || !StpUtil.isLogin()) {
            return true;
        }

        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        UserContext.setContext(userId, null);
        log.debug("根据 Sa-Token 设置用户上下文: userId={}", userId);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) throws Exception {
        // 请求完成后清除ThreadLocal，防止内存泄漏
        UserContext.clear();
        log.debug("清除用户上下文");
    }
}
