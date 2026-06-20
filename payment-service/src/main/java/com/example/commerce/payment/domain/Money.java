package com.example.commerce.payment.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Money(BigDecimal amount, String currency) {

  public Money {
    if (amount == null) {
      throw new IllegalArgumentException("amount is required");
    }
    if (amount.signum() <= 0) {
      throw new IllegalArgumentException("amount must be positive");
    }
    if (currency == null || currency.isBlank()) {
      throw new IllegalArgumentException("currency is required");
    }
    currency = currency.toUpperCase();
    if (!currency.matches("[A-Z]{3}")) {
      throw new IllegalArgumentException("currency must be a 3-letter ISO code");
    }
    amount = amount.setScale(2, RoundingMode.UNNECESSARY);
  }
}
