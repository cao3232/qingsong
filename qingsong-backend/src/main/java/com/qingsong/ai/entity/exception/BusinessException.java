package com.qingsong.ai.entity.exception;

/**
 * description...
 *
 * @author : caojiangjiang
 * @data : 2026/02/08 21:26
 */
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String message) {
        this(message, null);
    }

    public BusinessException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
