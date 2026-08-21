package com.qingsong.ai.config;

import cn.dev33.satoken.fun.SaParamFunction;
import cn.dev33.satoken.interceptor.SaInterceptor;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Sa-Token 的 Servlet 上下文默认只在初始请求派发中可用。
 */
public class RequestOnlySaInterceptor extends SaInterceptor {

    public RequestOnlySaInterceptor(SaParamFunction<Object> auth) {
        super(auth);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            return true;
        }
        return super.preHandle(request, response, handler);
    }
}
