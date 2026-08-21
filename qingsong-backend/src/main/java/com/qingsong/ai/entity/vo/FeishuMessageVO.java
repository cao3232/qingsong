package com.qingsong.ai.entity.vo;

import lombok.Data;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Data
public class FeishuMessageVO {
    private String message;
    private String user;


    public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {
//        // 测试反射
//        Class<FeishuMessageVO> feishuMessageVOClass = FeishuMessageVO.class;
//        Field messageField = feishuMessageVOClass.getDeclaredField("message");
//        messageField.setAccessible(Boolean.TRUE);
//        // 获取字段值
//        FeishuMessageVO feishuMessageVO = new FeishuMessageVO();
//        feishuMessageVO.setMessage("hello world");
//
//        String oldMessage = (String) messageField.get(feishuMessageVO);
//        System.out.println(oldMessage);
//
//        // 设置字段值
//        messageField.set(feishuMessageVO, "hello world 2");
//        String newMessage = (String) messageField.get(feishuMessageVO);
//        System.out.println(newMessage);


        // 测试 ThreadPoolExcutor
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(0, 100, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
        threadPoolExecutor.execute(() -> {
            System.out.println("hello world");
        });


    }
}
