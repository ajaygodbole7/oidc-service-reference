package com.example.commerce.order.domain;

import java.util.Objects;

public record OrderLine(ProductId productId, int quantity, Money unitPrice) {

  public OrderLine {
    Objects.requireNonNull(productId, "productId");
    Objects.requireNonNull(unitPrice, "unitPrice");
    if (quantity < 1) {
      throw new IllegalArgumentException("quantity must be positive");
    }
  }

  public Money lineTotal() {
    return unitPrice.multiply(quantity);
  }
}
