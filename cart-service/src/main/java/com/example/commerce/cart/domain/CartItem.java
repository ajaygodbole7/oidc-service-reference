package com.example.commerce.cart.domain;

public record CartItem(ProductId productId, Quantity quantity, Money unitPrice) {

  public CartItem {
    if (productId == null) {
      throw new IllegalArgumentException("productId is required");
    }
    if (quantity == null) {
      throw new IllegalArgumentException("quantity is required");
    }
    if (unitPrice == null) {
      throw new IllegalArgumentException("unitPrice is required");
    }
  }

  public Money lineTotal() {
    return unitPrice.multiply(quantity.value());
  }

  CartItem withAdditionalQuantity(Quantity additionalQuantity) {
    return new CartItem(productId, quantity.plus(additionalQuantity), unitPrice);
  }
}
