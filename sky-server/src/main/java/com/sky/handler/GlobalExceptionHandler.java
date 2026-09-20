package com.sky.handler;

import com.sky.constant.MessageConstant;
import com.sky.exception.BaseException;
import com.sky.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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
        log.error("数据库完整性约束异常", ex);
        // 匹配唯一键冲突，兼容中英文环境
        if (message != null && (message.contains("Duplicate entry") || message.contains("唯一键") || message.contains("重复键"))) {
            // 使用正则提取用户名，比按空格分割更可靠
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("'([^']+)'").matcher(message);
            if (matcher.find()) {
                String username = matcher.group(1);
                return Result.error(username + MessageConstant.ALREADY_EXISTS);
            }
        }
        if (message != null && message.contains("address_book_id") && message.contains("null")) {
            return Result.error("到店自提订单地址字段不兼容，请执行 V10 数据库迁移并重启后端");
        }
        // 不向客户端暴露 SQL，但给出比“未知错误”更可操作的提示；具体原因保留在 traceId 日志中。
        return Result.error("订单数据校验失败，请联系管理员按 traceId 查看后端日志");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        log.error("参数类型不匹配：{}", ex.getMessage());
        return Result.error("参数格式错误：" + ex.getName());
    }

    /**
     * 避免未处理异常只在小程序侧表现为“未知错误”。日志 MDC 已含 X-Trace-Id，
     * 可将同一次请求的前端控制台与后端异常栈对应起来。
     */
    @ExceptionHandler(Exception.class)
    public Result handleUnexpectedException(Exception ex) {
        log.error("未处理请求异常，请按 traceId 排查", ex);
        return Result.error("服务处理异常，请稍后重试或联系管理员查看 traceId");
    }

}
