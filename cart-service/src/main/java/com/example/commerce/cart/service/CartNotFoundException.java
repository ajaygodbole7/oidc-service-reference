package com.example.commerce.cart.service;

public final class CartNotFoundException extends RuntimeException {

  public CartNotFoundException(String message) {
    super(message);
  }
}
