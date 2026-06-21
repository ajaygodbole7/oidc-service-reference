package com.example.commerce.web.error;

import org.jspecify.annotations.Nullable;

/** Maps to HTTP 422. A service's "request is well-formed but violates a business rule" extends this. */
public non-sealed class BusinessRuleException extends ApiException {

  public BusinessRuleException(String message) {
    this("business-rule-violation", message);
  }

  public BusinessRuleException(String slug, String message) {
    super(slug, message);
  }

  public BusinessRuleException(String slug, String message, @Nullable Throwable cause) {
    super(slug, message, cause);
  }
}
