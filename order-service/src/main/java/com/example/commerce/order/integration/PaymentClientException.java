package com.example.commerce.order.integration;

/**
 * Raised when the order-to-payment service-to-service authorization call cannot be completed.
 * Checkout treats this as a hard failure and never persists an order.
 *
 * <p>Non-final so {@link TransientPaymentException} can specialize it: a plain instance is a
 * permanent / settled failure (declined, 4xx) that is never retried, while the subtype marks a
 * retryable transient failure.
 */
public class PaymentClientException extends RuntimeException {

  public PaymentClientException(String message) {
    super(message);
  }

  public PaymentClientException(String message, Throwable cause) {
    super(message, cause);
  }
}
