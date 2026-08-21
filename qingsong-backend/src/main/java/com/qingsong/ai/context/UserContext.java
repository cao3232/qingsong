package com.qingsong.ai.context;

import com.qingsong.ai.entity.po.user.UserConfig;

/**
 * 用户上下文持有者，用于在线程间传递用户相关信息
 *
 * @author : caojiangjiang
 * @data : 2026/04/26
 */
public class UserContext {

    private static final ThreadLocal<UserContextData> CONTEXT_HOLDER = new ThreadLocal<>();

    /**
     * 用户上下文数据
     */
    public static class UserContextData {
        private Long userId;
        private String userName;
        private UserConfig userConfig;

        public UserContextData() {
        }

        public UserContextData(Long userId, String userName) {
            this.userId = userId;
            this.userName = userName;
        }

        public UserContextData(Long userId, String userName, UserConfig userConfig) {
            this.userId = userId;
            this.userName = userName;
            this.userConfig = userConfig;
        }

        // Getters and Setters
        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public UserConfig getUserConfig() {
            return userConfig;
        }

        public void setUserConfig(UserConfig userConfig) {
            this.userConfig = userConfig;
        }

        @Override
        public String toString() {
            return "UserContextData{" +
                    "userId=" + userId +
                    ", userName='" + userName + '\'' +
                    ", userConfig=" + (userConfig != null ? "已加载" : "未加载") +
                    '}';
        }
    }

    /**
     * 设置当前线程的用户上下文
     */
    public static void setContext(UserContextData context) {
        CONTEXT_HOLDER.set(context);
    }

    /**
     * 设置当前线程的用户上下文
     */
    public static void setContext(Long userId, String userName) {
        CONTEXT_HOLDER.set(new UserContextData(userId, userName));
    }

    /**
     * 设置当前线程的用户上下文（包含配置）
     */
    public static void setContext(Long userId, String userName, UserConfig userConfig) {
        CONTEXT_HOLDER.set(new UserContextData(userId, userName, userConfig));
    }

    /**
     * 获取当前线程的用户上下文
     */
    public static UserContextData getContext() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 获取当前线程的用户ID
     */
    public static Long getCurrentUserId() {
        UserContextData context = getContext();
        return context != null ? context.getUserId() : null;
    }

    /**
     * 获取当前线程的用户名
     */
    public static String getCurrentUserName() {
        UserContextData context = getContext();
        return context != null ? context.getUserName() : null;
    }

    /**
     * 获取当前线程的用户配置
     */
    public static UserConfig getCurrentUserConfig() {
        UserContextData context = getContext();
        return context != null ? context.getUserConfig() : null;
    }

    /**
     * 清除当前线程的用户上下文
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
    }

    /**
     * 在函数式编程中安全地执行带上下文的操作
     */
    public static <T> T executeWithContext(Long userId, String userName, java.util.function.Supplier<T> supplier) {
        try {
            setContext(userId, userName);
            return supplier.get();
        } finally {
            clear();
        }
    }

    /**
     * 在函数式编程中安全地执行带上下文的操作（无返回值）
     */
    public static void executeWithContext(Long userId, String userName, Runnable runnable) {
        try {
            setContext(userId, userName);
            runnable.run();
        } finally {
            clear();
        }
    }
}
