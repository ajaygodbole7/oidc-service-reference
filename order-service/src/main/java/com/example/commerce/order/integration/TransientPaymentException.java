package com.example.commerce.order.integration;

/**
 * A payment authorization attempt failed for a reason that may succeed on retry: a connect/read
 * timeout (no response), an I/O error, or a 5xx from payment-service. The Idempotency-Key is sent on
 * every attempt, so retrying is safe — a duplicate is collapsed downstream.
 *
 * <p>Deliberately distinct from the permanent {@link PaymentClientException}: a 4xx, a declined
 * authorization, or a non-AUTHORIZED body is a settled answer and is NEVER retried.
 */
public final class TransientPaymentException extends PaymentClientException {

  public TransientPaymentException(String message, Throwable cause) {
    super(message, cause);
  }
}
