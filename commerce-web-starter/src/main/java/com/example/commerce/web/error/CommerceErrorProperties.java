package com.example.commerce.web.error;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config for the problem-detail {@code type} URI. Each service sets
 * {@code commerce.error.base-url} (e.g. {@code https://errors.example.com/catalog}); the advice
 * appends {@code /<slug>} per exception type.
 */
@ConfigurationProperties("commerce.error")
public class CommerceErrorProperties {

  /** Base URI for problem {@code type}. The per-exception slug is appended as {@code <base>/<slug>}. */
  private String baseUrl = "about:blank";

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }
}
