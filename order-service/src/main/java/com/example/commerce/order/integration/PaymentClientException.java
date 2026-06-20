package com.example.commerce.order.integration;

/**
 * Raised when the order-to-payment service-to-service authorization call cannot be completed.
 * Checkout treats this as a hard failure and never persists an order.
 */
public final class PaymentClientException extends RuntimeException {

  public PaymentClientException(String message) {
    super(message);
  }

  public PaymentClientException(String message, Throwable cause) {
    super(message, cause);
  }
}
