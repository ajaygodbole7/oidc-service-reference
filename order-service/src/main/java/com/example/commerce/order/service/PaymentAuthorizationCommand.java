package com.example.commerce.order.service;

import com.example.commerce.order.domain.CartId;
import com.example.commerce.order.domain.IdempotencyKey;
import com.example.commerce.order.domain.Money;
import com.example.commerce.order.domain.OrderId;
import java.util.Objects;

public record PaymentAuthorizationCommand(
    OrderId orderId,
    String userSub,
    CartId cartId,
    Money amount,
    IdempotencyKey idempotencyKey,
    String paymentMethodId) {

  public PaymentAuthorizationCommand {
    Objects.requireNonNull(orderId, "orderId");
    if (userSub == null || userSub.isBlank()) {
      throw new IllegalArgumentException("userSub is required");
    }
    Objects.requireNonNull(cartId, "cartId");
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    if (paymentMethodId == null || paymentMethodId.isBlank()) {
      throw new IllegalArgumentException("paymentMethodId is required");
    }
  }
}
