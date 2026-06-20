package com.example.commerce.order.web;

import jakarta.validation.constraints.NotBlank;

public final class OrderRequest {

  private OrderRequest() {
  }

  public record Checkout(
      @NotBlank String paymentMethodId,
      @NotBlank String shippingPostalCode) {
  }
}
