package com.example.commerce.order.service;

import com.example.commerce.web.error.BadRequestException;

/**
 * Checkout was called without the {@code Idempotency-Key} header. A required request input is
 * missing, so it maps to HTTP 400 via the starter advice (the key is required before any payment
 * side effect).
 */
public final class MissingIdempotencyKeyException extends BadRequestException {

  public MissingIdempotencyKeyException(String message) {
    super("idempotency-key-required", message);
  }
}
