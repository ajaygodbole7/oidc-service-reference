package com.example.commerce.order.service;

import com.example.commerce.web.error.ConflictException;

/**
 * The order is already cancelled, so the cancel command is a no-op state conflict. Maps to HTTP 409
 * via the starter advice. Translates the {@code Order.cancel()} domain invariant (an
 * {@link IllegalStateException}) into the shared conflict type so the domain stays free of any
 * web-starter dependency.
 */
public final class OrderAlreadyCancelledException extends ConflictException {

  public OrderAlreadyCancelledException(String message, Throwable cause) {
    super("order-already-cancelled", message, cause);
  }
}
