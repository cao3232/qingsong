package com.qingsong.ai.entity.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Result<T> {
    private Integer ok;
    private String msg;
    private T data;

    private Result(Integer ok, String msg) {
        this.ok = ok;
        this.msg = msg;
    }

    public static Result ok() {
        return new Result(1, "ok");
    }

    public static <T> Result<T> ok(T data) {
        Result result = new Result(1, "ok");
        result.setData(data);
        return result;
    }

    public static Result fail(String msg) {
        return new Result(0, msg);
    }
}
