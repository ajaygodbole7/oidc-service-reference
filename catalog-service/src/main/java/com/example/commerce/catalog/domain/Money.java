package com.example.commerce.catalog.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public record Money(BigDecimal amount, String currency) {

  public Money {
    if (amount == null) {
      throw new IllegalArgumentException("amount is required");
    }
    if (currency == null || currency.isBlank()) {
      throw new IllegalArgumentException("currency is required");
    }
    try {
      amount = amount.setScale(2, RoundingMode.UNNECESSARY);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("amount must have at most two decimal places", exception);
    }
    currency = currency.toUpperCase(Locale.ROOT);
  }

  public static Money usd(String amount) {
    return new Money(new BigDecimal(amount), "USD");
  }
}
