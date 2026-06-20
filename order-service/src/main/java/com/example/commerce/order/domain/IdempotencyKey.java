package com.example.commerce.order.domain;

public record IdempotencyKey(String value) {

  public IdempotencyKey {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("idempotency key is required");
    }
    if (value.length() > 120) {
      throw new IllegalArgumentException("idempotency key is too long");
    }
  }
}
