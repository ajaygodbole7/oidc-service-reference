package com.example.commerce.web.error;

import org.jspecify.annotations.Nullable;

/** Maps to HTTP 503. A service's "downstream dependency unavailable" domain exception extends this. */
public non-sealed class ServiceUnavailableException extends ApiException {

  public ServiceUnavailableException(String message) {
    this("service-unavailable", message);
  }

  public ServiceUnavailableException(String slug, String message) {
    super(slug, message);
  }

  public ServiceUnavailableException(String slug, String message, @Nullable Throwable cause) {
    super(slug, message, cause);
  }
}
