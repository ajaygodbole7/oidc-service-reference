package com.example.commerce.cart.domain;

public record Quantity(int value) {

  public Quantity {
    if (value <= 0) {
      throw new IllegalArgumentException("quantity must be positive");
    }
  }

  public Quantity plus(Quantity other) {
    return new Quantity(value + other.value);
  }
}
