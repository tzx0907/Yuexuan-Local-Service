package com.sky.exception;

/**
 * 请求频率超过接口允许范围。
 */
public class RateLimitException extends BaseException {

    public RateLimitException(String message) {
        super(message);
    }
}
