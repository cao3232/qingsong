package com.qingsong.ai.context;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话级上下文窗口大小持有者。
 * <p>用于在请求线程与 Reactor 线程之间传递当前会话的上下文窗口大小，
 * 避免 ThreadLocal 在异步/流式场景下失效。</p>
 *
 * @author caojiangjiang
 * @date 2026/08/25
 */
@Component
public class ChatContextSizeHolder {

    private final ConcurrentHashMap<String, Integer> sessionContextSizes = new ConcurrentHashMap<>();

    /**
     * 设置指定会话的上下文窗口大小
     *
     * @param sessionNo   会话编号
     * @param contextSize 上下文窗口大小
     */
    public void setContextSize(String sessionNo, Integer contextSize) {
        if (sessionNo == null || contextSize == null) {
            return;
        }
        sessionContextSizes.put(sessionNo, contextSize);
    }

    /**
     * 获取指定会话的上下文窗口大小
     *
     * @param sessionNo 会话编号
     * @return 上下文窗口大小，未设置返回 null
     */
    public Integer getContextSize(String sessionNo) {
        if (sessionNo == null) {
            return null;
        }
        return sessionContextSizes.get(sessionNo);
    }

    /**
     * 移除指定会话的上下文窗口大小
     *
     * @param sessionNo 会话编号
     */
    public void removeContextSize(String sessionNo) {
        if (sessionNo == null) {
            return;
        }
        sessionContextSizes.remove(sessionNo);
    }
}
