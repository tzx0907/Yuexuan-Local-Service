package com.sky.filter;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 为每个 HTTP 请求建立日志追踪标识。
 * 上游服务可透传 X-Trace-Id；不合法或缺失时由本服务生成，避免请求头内容污染日志。
 */
@Component
@Slf4j
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";
    private static final Pattern VALID_TRACE_ID = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader(TRACE_ID_HEADER));
        long startTime = System.currentTimeMillis();
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            log.info("请求开始 method={}, uri={}", request.getMethod(), request.getRequestURI());
            filterChain.doFilter(request, response);
        } finally {
            log.info("请求结束 method={}, uri={}, status={}, elapsedMs={}", request.getMethod(),
                    request.getRequestURI(), response.getStatus(), System.currentTimeMillis() - startTime);
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    private String resolveTraceId(String requestTraceId) {
        if (requestTraceId != null && VALID_TRACE_ID.matcher(requestTraceId).matches()) {
            return requestTraceId;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
