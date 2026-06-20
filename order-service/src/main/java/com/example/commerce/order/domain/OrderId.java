package com.example.commerce.order.domain;

public record OrderId(String value) {

  public OrderId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("order id is required");
    }
  }
}
