package com.example.commerce.order.service;

public record CheckoutCommand(String paymentMethodId, String shippingPostalCode) {

  public CheckoutCommand {
    if (paymentMethodId == null || paymentMethodId.isBlank()) {
      throw new IllegalArgumentException("paymentMethodId is required");
    }
    if (shippingPostalCode == null || shippingPostalCode.isBlank()) {
      throw new IllegalArgumentException("shippingPostalCode is required");
    }
  }

  String requestFingerprint(CartSnapshot cart) {
    return "paymentMethodId=%s|shippingPostalCode=%s|cart=%s|total=%s|items=%s".formatted(
        paymentMethodId,
        shippingPostalCode,
        cart.id().value(),
        cart.total().amount().toPlainString(),
        cart.lines().stream()
            .map(line -> "%s:%d:%s".formatted(
                line.productId().value(),
                line.quantity(),
                line.unitPrice().amount().toPlainString()))
            .sorted()
            .toList());
  }
}
