package com.example.yin.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Object handleJsonError(HttpMessageNotReadableException e) {
        log.error("请求体解析失败: {}", e.getMessage());
        // 递归获取最底层的 cause
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        log.error("根因: {}", cause.toString());
        return com.example.yin.common.R.error("参数格式错误: " + cause.getMessage());
    }
}
