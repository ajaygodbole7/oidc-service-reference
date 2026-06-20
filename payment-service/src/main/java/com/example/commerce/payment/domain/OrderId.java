package com.example.commerce.payment.domain;

public record OrderId(String value) {

  public OrderId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("order id is required");
    }
  }
}
