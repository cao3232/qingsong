package com.qingsong.ai.entity.event;

import org.springframework.context.ApplicationEvent;

/**
 * 自定义事件类，用于在应用程序中传递消息
 *
 * @author caojiangjiang
 */
public class MyEvent<T> extends ApplicationEvent {
    private final String message;

    private final T data;

    /**
     * 构造一个新的MyEvent实例
     *
     * @param source  事件源对象
     * @param message 要传递的消息内容
     */
    public MyEvent(T source, String message) {
        super(source);
        this.message = message;
        this.data = source;
    }

    /**
     * 获取事件中包含的消息
     *
     * @return 消息内容
     */
    public String getMessage() {
        return message;
    }
}
