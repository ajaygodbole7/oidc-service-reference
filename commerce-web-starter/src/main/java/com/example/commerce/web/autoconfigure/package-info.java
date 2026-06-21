/**
 * Boot 4 auto-configuration wiring for the commerce web starter: registers the problem-detail
 * advice, the trace-id filter, the TSID generator, the cursor paginator, and the
 * {@code commerce.error.*} / {@code commerce.pagination.*} properties. A service adds the dependency
 * and these wire themselves; each bean is {@link org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean}
 * so a service can override any one.
 */
@org.jspecify.annotations.NullMarked
package com.example.commerce.web.autoconfigure;
