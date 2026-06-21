package com.example.commerce.cart.service;

import com.example.commerce.web.error.ResourceNotFoundException;

/**
 * Cart-not-found domain exception. Extends the starter's {@link ResourceNotFoundException} so the
 * auto-configured RFC 9457 advice maps it to HTTP 404 with
 * {@code type = ${commerce.error.base-url}/cart-not-found} and {@code errorCode = CART_NOT_FOUND};
 * no per-service handler code.
 */
public final class CartNotFoundException extends ResourceNotFoundException {

  public CartNotFoundException(String message) {
    super("cart-not-found", message);
  }
}
