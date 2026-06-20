package com.example.commerce.payment.service;

public final class PaymentIdempotencyConflictException extends RuntimeException {

  public PaymentIdempotencyConflictException(String message) {
    super(message);
  }
}
