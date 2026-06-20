package com.example.commerce.payment.domain;

import java.time.Instant;

public record PaymentAuthorization(
    String paymentId,
    OrderId orderId,
    String userSub,
    Money amount,
    String status,
    String idempotencyKey,
    String commandFingerprint,
    Instant authorizedAt) {

  public PaymentAuthorization {
    if (paymentId == null || paymentId.isBlank()) {
      throw new IllegalArgumentException("payment id is required");
    }
    if (userSub == null || userSub.isBlank()) {
      throw new IllegalArgumentException("user subject is required");
    }
    if (status == null || status.isBlank()) {
      throw new IllegalArgumentException("payment status is required");
    }
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("idempotency key is required");
    }
    if (commandFingerprint == null || commandFingerprint.isBlank()) {
      throw new IllegalArgumentException("command fingerprint is required");
    }
    if (authorizedAt == null) {
      throw new IllegalArgumentException("authorized timestamp is required");
    }
  }
}
