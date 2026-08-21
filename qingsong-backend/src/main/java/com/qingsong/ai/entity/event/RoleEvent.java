package com.qingsong.ai.entity.event;


import com.qingsong.ai.entity.po.role.Role;

/**
 * 自定义事件类，用于在应用程序中传递消息
 *
 * @author caojiangjiang
 */
public class RoleEvent extends MyEvent<Role> {
    /**
     * 构造一个新的MyEvent实例
     *
     * @param source  事件源对象
     * @param message 要传递的消息内容
     */
    public RoleEvent(Role source, String message) {
        super(source, message);
    }
}
