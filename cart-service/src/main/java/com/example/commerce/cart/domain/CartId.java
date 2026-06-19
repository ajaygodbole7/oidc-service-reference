package com.example.commerce.cart.domain;

public record CartId(String value) {

  public CartId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("cart id is required");
    }
  }
}
