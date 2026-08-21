package com.qingsong.ai.entity.vo;


import lombok.Data;

import java.util.Map;

@Data
public class FeishuMsgRespVO {
    private int StatusCode;

    private String StatusMessage;

    private int code;

    private Map<String, Object> data;

    private String msg;
}