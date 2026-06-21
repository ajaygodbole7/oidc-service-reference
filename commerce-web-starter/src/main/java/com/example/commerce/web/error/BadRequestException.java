package com.example.commerce.web.error;

import org.jspecify.annotations.Nullable;

/** Maps to HTTP 400. A service's "request is malformed / fails an input precondition" extends this. */
public non-sealed class BadRequestException extends ApiException {

  public BadRequestException(String message) {
    this("bad-request", message);
  }

  public BadRequestException(String slug, String message) {
    super(slug, message);
  }

  public BadRequestException(String slug, String message, @Nullable Throwable cause) {
    super(slug, message, cause);
  }
}
