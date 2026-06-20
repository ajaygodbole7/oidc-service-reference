package com.example.commerce.order.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public record Money(BigDecimal amount, String currency) {

  public static final Money ZERO = zero("USD");

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

  public static Money zero(String currency) {
    return new Money(BigDecimal.ZERO, currency);
  }

  public Money plus(Money other) {
    requireSameCurrency(other);
    return new Money(amount.add(other.amount), currency);
  }

  public Money multiply(int quantity) {
    return new Money(amount.multiply(BigDecimal.valueOf(quantity)), currency);
  }

  private void requireSameCurrency(Money other) {
    if (!currency.equals(other.currency)) {
      throw new IllegalArgumentException("currency mismatch");
    }
  }
}
