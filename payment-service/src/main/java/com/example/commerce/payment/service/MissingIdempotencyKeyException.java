package com.example.commerce.payment.service;

import com.example.commerce.web.error.BadRequestException;

/**
 * The required {@code Idempotency-Key} header was absent or blank. A required request input is
 * missing, so it maps to HTTP 400 via the starter advice's {@code BadRequestException} family.
 */
public final class MissingIdempotencyKeyException extends BadRequestException {

  public MissingIdempotencyKeyException(String message) {
    super("idempotency-key-required", message);
  }
}
