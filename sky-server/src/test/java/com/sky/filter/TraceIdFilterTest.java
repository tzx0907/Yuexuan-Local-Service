package com.sky.filter;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TraceIdFilterTest {

    private final TraceIdFilter traceIdFilter = new TraceIdFilter();

    @Test
    void shouldReuseValidTraceIdAndCleanMdcAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/user/order/submit");
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "order-submit-1001");
        MockHttpServletResponse response = new MockHttpServletResponse();

        traceIdFilter.doFilter(request, response, (req, resp) ->
                assertEquals("order-submit-1001", MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)));

        assertEquals("order-submit-1001", response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
        assertNull(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));
    }

    @Test
    void shouldGenerateTraceIdWhenRequestHeaderIsInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user/shop/status");
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "bad trace id with space");
        MockHttpServletResponse response = new MockHttpServletResponse();

        traceIdFilter.doFilter(request, response, (req, resp) ->
                assertNotNull(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)));

        String responseTraceId = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        assertNotNull(responseTraceId);
        org.junit.jupiter.api.Assertions.assertTrue(responseTraceId.matches("[a-f0-9]{32}"));
        assertNull(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));
    }
}
