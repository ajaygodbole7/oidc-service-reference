package com.example.commerce.order.domain;

public record CartId(String value) {

  public CartId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("cart id is required");
    }
  }
}
