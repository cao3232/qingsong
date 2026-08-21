package com.qingsong.ai.utils;

import com.qingsong.ai.context.UserContext;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;

/**
 * 异步任务上下文传递工具类
 * 解决 ThreadLocal 在异步场景下丢失的问题
 *
 * @author : caojiangjiang
 * @data : 2026/04/26
 */
@Slf4j
public class AsyncUserContextUtils {

    /**
     * 包装 Runnable，在异步线程中传递用户上下文
     *
     * @param runnable 原始任务
     * @return 包装后的任务
     */
    public static Runnable wrap(Runnable runnable) {
        // 捕获当前线程的上下文
        UserContext.UserContextData context = UserContext.getContext();

        return () -> {
            try {
                // 在异步线程中设置上下文
                if (context != null) {
                    UserContext.setContext(context);
                    log.debug("异步任务继承用户上下文: userId={}", context.getUserId());
                }
                // 执行任务
                runnable.run();
            } finally {
                // 清理上下文，防止内存泄漏
                UserContext.clear();
            }
        };
    }

    /**
     * 包装 Callable，在异步线程中传递用户上下文
     *
     * @param callable 原始任务
     * @param <T>      返回值类型
     * @return 包装后的任务
     */
    public static <T> Callable<T> wrap(Callable<T> callable) {
        // 捕获当前线程的上下文
        UserContext.UserContextData context = UserContext.getContext();

        return () -> {
            try {
                // 在异步线程中设置上下文
                if (context != null) {
                    UserContext.setContext(context);
                    log.debug("异步任务继承用户上下文: userId={}", context.getUserId());
                }
                // 执行任务并返回结果
                return callable.call();
            } finally {
                // 清理上下文，防止内存泄漏
                UserContext.clear();
            }
        };
    }

    /**
     * 手动指定用户上下文执行异步任务
     *
     * @param userId   用户ID
     * @param userName 用户名
     * @param runnable 任务
     * @return 包装后的任务
     */
    public static Runnable wrapWithUser(Long userId, String userName, Runnable runnable) {
        return () -> {
            try {
                // 设置指定的用户上下文
                UserContext.setContext(userId, userName);
                log.debug("异步任务使用指定用户上下文: userId={}, userName={}", userId, userName);
                // 执行任务
                runnable.run();
            } finally {
                // 清理上下文
                UserContext.clear();
            }
        };
    }

    /**
     * 手动指定用户上下文执行异步任务（带返回值）
     *
     * @param userId   用户ID
     * @param userName 用户名
     * @param callable 任务
     * @param <T>      返回值类型
     * @return 包装后的任务
     */
    public static <T> Callable<T> wrapWithUser(Long userId, String userName, Callable<T> callable) {
        return () -> {
            try {
                // 设置指定的用户上下文
                UserContext.setContext(userId, userName);
                log.debug("异步任务使用指定用户上下文: userId={}, userName={}", userId, userName);
                // 执行任务并返回结果
                return callable.call();
            } finally {
                // 清理上下文
                UserContext.clear();
            }
        };
    }
}
