package com.qingsong.ai.entity.dto;

import lombok.Data;

@Data
public class FeishuMsgReqDTO {
    private String msg_type;
    private Content content;
    private String timestamp;
    private String sign;

    @Data
    public static class Content {
        private String text;
    }
}
