package com.example.commerce.web.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Copies the gateway-supplied {@code X-Trace-Id} request header into MDC key {@code traceId} for the
 * duration of the request, then clears it in a finally block so the worker thread never leaks a
 * trace id into the next request. The advice reads the same MDC key for the problem-detail
 * {@code traceId} extension.
 */
public final class TraceIdFilter extends OncePerRequestFilter {

  /** Inbound header set by the gateway. */
  public static final String TRACE_ID_HEADER = "X-Trace-Id";

  /** MDC key; matches the {@code logging.pattern.correlation} each service configures. */
  public static final String TRACE_ID_MDC_KEY = "traceId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String traceId = request.getHeader(TRACE_ID_HEADER);
    boolean present = traceId != null && !traceId.isBlank();
    if (present) {
      MDC.put(TRACE_ID_MDC_KEY, traceId);
    }
    try {
      filterChain.doFilter(request, response);
    } finally {
      if (present) {
        MDC.remove(TRACE_ID_MDC_KEY);
      }
    }
  }
}
