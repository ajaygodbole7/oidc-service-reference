package com.example.commerce.web.error;

import org.jspecify.annotations.Nullable;

/**
 * Base for every domain exception the {@link GlobalExceptionHandler} maps to a problem detail.
 *
 * <p>Each concrete subtype carries a fixed {@code slug} (lower-kebab, e.g. {@code product-not-found})
 * that drives the problem-detail {@code type} URI ({@code ${commerce.error.base-url}/<slug>}) and the
 * {@code errorCode} extension (slug upper-snake). Subtypes do not pick an HTTP status; the four
 * sealed status families below do, so the single advice maps any service exception with zero
 * per-service handler code.
 */
public abstract sealed class ApiException extends RuntimeException
    permits ResourceNotFoundException, BadRequestException, ConflictException, BusinessRuleException, ServiceUnavailableException {

  private final String slug;

  protected ApiException(String slug, String message) {
    super(message);
    this.slug = slug;
  }

  protected ApiException(String slug, String message, @Nullable Throwable cause) {
    super(message, cause);
    this.slug = slug;
  }

  /** Lower-kebab slug, e.g. {@code product-not-found}. Drives the problem {@code type} and errorCode. */
  public final String slug() {
    return slug;
  }
}
