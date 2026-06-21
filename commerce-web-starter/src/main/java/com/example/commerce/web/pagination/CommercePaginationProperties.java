package com.example.commerce.web.pagination;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Page-size policy for cursor pagination. Defaults: 20 per page, 100 cap. */
@ConfigurationProperties("commerce.pagination")
public class CommercePaginationProperties {

  /** Page size used when a request omits one. */
  private int defaultPageSize = 20;

  /** Hard cap; a requested size above this is clamped down (never rejected). */
  private int maxPageSize = 100;

  public int getDefaultPageSize() {
    return defaultPageSize;
  }

  public void setDefaultPageSize(int defaultPageSize) {
    this.defaultPageSize = defaultPageSize;
  }

  public int getMaxPageSize() {
    return maxPageSize;
  }

  public void setMaxPageSize(int maxPageSize) {
    this.maxPageSize = maxPageSize;
  }
}
