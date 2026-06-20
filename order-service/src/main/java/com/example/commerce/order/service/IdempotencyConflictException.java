package com.example.commerce.order.service;

public final class IdempotencyConflictException extends RuntimeException {

  public IdempotencyConflictException(String message) {
    super(message);
  }
}
