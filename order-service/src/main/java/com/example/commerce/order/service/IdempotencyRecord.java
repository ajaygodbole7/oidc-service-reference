package com.example.commerce.order.service;

import com.example.commerce.order.domain.IdempotencyKey;
import com.example.commerce.order.domain.OrderId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record IdempotencyRecord(
    String subject,
    IdempotencyKey key,
    String requestFingerprint,
    @Nullable OrderId orderId) {

  public IdempotencyRecord {
    if (subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("subject is required");
    }
    Objects.requireNonNull(key, "key");
    if (requestFingerprint == null || requestFingerprint.isBlank()) {
      throw new IllegalArgumentException("requestFingerprint is required");
    }
  }
}
