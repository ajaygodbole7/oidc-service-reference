package com.example.commerce.payment.domain;

/**
 * Outcome of a payment authorization. Stored as the enum {@code name()} (VARCHAR) and serialized as
 * the same name on the wire, so the cross-service contract with order-service (which checks
 * {@code "AUTHORIZED".equals(status)}) stays a stable string.
 */
public enum PaymentStatus {
  AUTHORIZED,
  DECLINED
}
