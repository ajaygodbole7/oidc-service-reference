package com.example.commerce.order.service;

import com.example.commerce.web.error.ResourceNotFoundException;

/** Order (or the current user's cart) could not be resolved. Maps to HTTP 404 via the starter advice. */
public final class OrderNotFoundException extends ResourceNotFoundException {

  public OrderNotFoundException(String message) {
    super("order-not-found", message);
  }
}
