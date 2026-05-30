package com.sky.handler;

import com.sky.constant.MessageConstant;
import com.sky.exception.BaseException;
import com.sky.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器，处理项目中抛出的业务异常
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获业务异常
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(BaseException ex){
        log.error("异常信息：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause().getMessage();
        // 匹配唯一键冲突，兼容中英文环境
        if (message != null && (message.contains("Duplicate entry") || message.contains("唯一键") || message.contains("重复键"))) {
            // 使用正则提取用户名，比按空格分割更可靠
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("'([^']+)'").matcher(message);
            if (matcher.find()) {
                String username = matcher.group(1);
                return Result.error(username + MessageConstant.ALREADY_EXISTS);
            }
        }
        // 其他数据完整性异常或解析失败，返回通用错误
        return Result.error(MessageConstant.UNKNOWN_ERROR);
    }

}
