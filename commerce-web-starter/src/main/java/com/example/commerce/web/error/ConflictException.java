package com.example.commerce.web.error;

import org.jspecify.annotations.Nullable;

/** Maps to HTTP 409. A service's "state conflict / version clash" domain exception extends this. */
public non-sealed class ConflictException extends ApiException {

  public ConflictException(String message) {
    this("conflict", message);
  }

  public ConflictException(String slug, String message) {
    super(slug, message);
  }

  public ConflictException(String slug, String message, @Nullable Throwable cause) {
    super(slug, message, cause);
  }
}
