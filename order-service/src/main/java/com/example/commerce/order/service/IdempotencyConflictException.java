package com.example.commerce.order.service;

import com.example.commerce.web.error.ConflictException;

/**
 * The same idempotency key was reused for a materially different request. Maps to HTTP 409 via the
 * starter advice.
 */
public final class IdempotencyConflictException extends ConflictException {

  public IdempotencyConflictException(String message) {
    super("idempotency-conflict", message);
  }
}
