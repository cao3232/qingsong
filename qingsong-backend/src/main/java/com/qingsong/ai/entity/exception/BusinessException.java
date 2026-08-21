package com.qingsong.ai.entity.exception;

/**
 * description...
 *
 * @author : caojiangjiang
 * @data : 2026/02/08 21:26
 */
public class BusinessException extends RuntimeException {

    // 可以扩展错误码字段
    public BusinessException(String message) {
        super(message);
    }
}
