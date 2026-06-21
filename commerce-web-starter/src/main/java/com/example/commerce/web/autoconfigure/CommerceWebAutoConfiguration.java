package com.example.commerce.web.autoconfigure;

import com.example.commerce.web.error.CommerceErrorProperties;
import com.example.commerce.web.error.GlobalExceptionHandler;
import com.example.commerce.web.pagination.CommercePaginationProperties;
import com.example.commerce.web.pagination.CursorPaginator;
import com.example.commerce.web.tsid.HypersistenceTsidGenerator;
import com.example.commerce.web.tsid.TsidGenerator;
import com.example.commerce.web.trace.TraceIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Wires the starter's cross-cutting beans. Each is
 * {@link ConditionalOnMissingBean} so a service can override any one of them.
 */
@AutoConfiguration
@ConditionalOnWebApplication
@EnableConfigurationProperties({CommerceErrorProperties.class, CommercePaginationProperties.class})
public class CommerceWebAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public GlobalExceptionHandler commerceGlobalExceptionHandler(CommerceErrorProperties properties) {
    return new GlobalExceptionHandler(properties);
  }

  @Bean
  @ConditionalOnMissingBean
  public TraceIdFilter commerceTraceIdFilter() {
    return new TraceIdFilter();
  }

  @Bean
  @ConditionalOnMissingBean
  public TsidGenerator tsidGenerator() {
    return new HypersistenceTsidGenerator();
  }

  @Bean
  @ConditionalOnMissingBean
  public CursorPaginator cursorPaginator(CommercePaginationProperties properties) {
    return new CursorPaginator(properties.getDefaultPageSize(), properties.getMaxPageSize());
  }
}
