package com.qingsong.ai.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;

import java.util.Arrays;

/**
 * 切面类，用于拦截聊天消息的添加操作
 *
 * @author : caojiangjiang
 * @data : 2025/11/16 17:16
 */
// @Aspect
// @Component
public class MessageAspect {

    /**
     * 定义切点，拦截ChatMemory的add方法
     */
    @Pointcut("execution(* org.springframework.ai.chat.memory.ChatMemory+.add(..))")
    public void addMessageToChatMemory() {
    }

    /**
     * 在ChatMemory.add方法执行前进行拦截
     *
     * @param joinPoint 连接点信息
     */
    @Before("addMessageToChatMemory()")
    public void beforeExportMessage(JoinPoint joinPoint) {
        System.out.println("拦截到消息添加操作: " + joinPoint.getSignature().getName());
        for (String s : Arrays.asList("参数: " + Arrays.toString(joinPoint.getArgs()), "beforeExportMessage")) {
            System.out.println(s);
        }
    }
}
