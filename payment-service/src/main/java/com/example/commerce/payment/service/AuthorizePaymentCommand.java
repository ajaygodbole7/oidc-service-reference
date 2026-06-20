package com.example.commerce.payment.service;

import com.example.commerce.payment.domain.Money;
import com.example.commerce.payment.domain.OrderId;

public record AuthorizePaymentCommand(OrderId orderId, String userSub, Money amount) {

  public AuthorizePaymentCommand {
    if (userSub == null || userSub.isBlank()) {
      throw new IllegalArgumentException("user subject is required");
    }
  }
}
