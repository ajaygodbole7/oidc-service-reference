/**
 * Correlation: {@link com.example.commerce.web.trace.TraceIdFilter} copies the gateway's
 * {@code X-Trace-Id} request header into MDC key {@code traceId} for the request, so every log line
 * and the {@code traceId} problem-detail extension carry the same id. Removed in a finally block.
 */
@org.jspecify.annotations.NullMarked
package com.example.commerce.web.trace;
