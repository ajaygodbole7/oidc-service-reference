package com.example.commerce.payment.service;

import com.example.commerce.web.error.ConflictException;

/** An {@code Idempotency-Key} was reused with a different command body. Maps to HTTP 409. */
public final class PaymentIdempotencyConflictException extends ConflictException {

  public PaymentIdempotencyConflictException(String message) {
    super("idempotency-conflict", message);
  }
}
