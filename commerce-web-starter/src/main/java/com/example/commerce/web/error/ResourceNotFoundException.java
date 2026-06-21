package com.example.commerce.web.error;

import org.jspecify.annotations.Nullable;

/** Maps to HTTP 404. A service's "not found" domain exception extends this. */
public non-sealed class ResourceNotFoundException extends ApiException {

  /** Convenience for ad-hoc throws; slug defaults to {@code resource-not-found}. */
  public ResourceNotFoundException(String message) {
    this("resource-not-found", message);
  }

  public ResourceNotFoundException(String slug, String message) {
    super(slug, message);
  }

  public ResourceNotFoundException(String slug, String message, @Nullable Throwable cause) {
    super(slug, message, cause);
  }
}
