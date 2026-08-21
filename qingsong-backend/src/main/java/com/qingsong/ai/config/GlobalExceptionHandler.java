package com.qingsong.ai.config;

import cn.dev33.satoken.exception.NotLoginException;
import com.qingsong.ai.entity.exception.BusinessException;
import com.qingsong.ai.entity.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 *
 * @author : caojiangjiang
 * @data : 2026/02/08 21:25
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result> handleNotLoginException(NotLoginException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.fail("未登录，请重新登录"));
    }

    @ExceptionHandler(BusinessException.class)
    public Result handleBusinessException(BusinessException e) {
        // 将异常中的 message (例如"当前状态不可激活") 返回给前端
        return Result.fail(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result handleException(Exception e) {
        log.error("系统异常，请稍后再试", e);
        return Result.fail("系统繁忙，请稍后再试");
    }
}
