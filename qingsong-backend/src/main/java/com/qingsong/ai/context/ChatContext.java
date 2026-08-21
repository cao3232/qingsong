package com.qingsong.ai.context;

/**
 * 聊天上下文持有者，用于在线程间传递聊天相关参数
 *
 * @author caojiangjiang
 * @date 2026/02/24
 */
public class ChatContext {

    private static final ThreadLocal<ChatContextData> CONTEXT_HOLDER = new ThreadLocal<>();

    /**
     * 聊天上下文数据
     */
    public static class ChatContextData {
        private String role;
        private String chatId;
        private String type = "chat"; // 默认业务类型

        public ChatContextData() {
        }

        public ChatContextData(String role, String chatId) {
            this.role = role;
            this.chatId = chatId;
        }

        public ChatContextData(String role, String chatId, String type) {
            this.role = role;
            this.chatId = chatId;
            this.type = type;
        }

        // Getters and Setters
        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getChatId() {
            return chatId;
        }

        public void setChatId(String chatId) {
            this.chatId = chatId;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        /**
         * 构建标准的conversationId格式
         *
         * @return role:chatId 格式
         */
        public String buildConversationId() {
            if (role == null || chatId == null) {
                throw new IllegalStateException("role和chatId不能为空");
            }
            return role + ":" + chatId;
        }

        /**
         * 构建Redis键
         *
         * @param keyPattern Redis键模式
         * @return 完整的Redis键
         */
        public String buildRedisKey(String keyPattern) {
            if (role == null || chatId == null) {
                throw new IllegalStateException("role和chatId不能为空");
            }
            return String.format(keyPattern, role, chatId);
        }

        @Override
        public String toString() {
            return "ChatContextData{" +
                    "role='" + role + '\'' +
                    ", chatId='" + chatId + '\'' +
                    ", type='" + type + '\'' +
                    '}';
        }
    }

    /**
     * 设置当前线程的聊天上下文
     */
    public static void setContext(ChatContextData context) {
        CONTEXT_HOLDER.set(context);
    }

    /**
     * 设置当前线程的聊天上下文
     */
    public static void setContext(String role, String chatId) {
        CONTEXT_HOLDER.set(new ChatContextData(role, chatId));
    }

    /**
     * 设置当前线程的聊天上下文
     */
    public static void setContext(String role, String chatId, String type) {
        CONTEXT_HOLDER.set(new ChatContextData(role, chatId, type));
    }

    /**
     * 获取当前线程的聊天上下文
     */
    public static ChatContextData getContext() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 获取当前线程的角色
     */
    public static String getCurrentRole() {
        ChatContextData context = getContext();
        return context != null ? context.getRole() : null;
    }

    /**
     * 获取当前线程的聊天ID
     */
    public static String getCurrentChatId() {
        ChatContextData context = getContext();
        return context != null ? context.getChatId() : null;
    }

    /**
     * 获取当前线程的业务类型
     */
    public static String getCurrentType() {
        ChatContextData context = getContext();
        return context != null ? context.getType() : "chat";
    }

    /**
     * 构建当前上下文的标准conversationId
     */
    public static String buildCurrentConversationId() {
        ChatContextData context = getContext();
        if (context == null) {
            throw new IllegalStateException("未设置聊天上下文");
        }
        return context.buildConversationId();
    }

    /**
     * 构建当前上下文的Redis键
     */
    public static String buildCurrentRedisKey(String keyPattern) {
        ChatContextData context = getContext();
        if (context == null) {
            throw new IllegalStateException("未设置聊天上下文");
        }
        return context.buildRedisKey(keyPattern);
    }

    /**
     * 清除当前线程的聊天上下文
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
    }

    /**
     * 在函数式编程中安全地执行带上下文的操作
     */
    public static <T> T executeWithContext(String role, String chatId, java.util.function.Supplier<T> supplier) {
        try {
            setContext(role, chatId);
            return supplier.get();
        } finally {
            clear();
        }
    }

    /**
     * 在函数式编程中安全地执行带上下文的操作（无返回值）
     */
    public static void executeWithContext(String role, String chatId, Runnable runnable) {
        try {
            setContext(role, chatId);
            runnable.run();
        } finally {
            clear();
        }
    }
}
