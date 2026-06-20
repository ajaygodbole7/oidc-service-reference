package com.example.commerce.payment.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public final class PaymentRequest {

  private PaymentRequest() {
  }

  public record Authorize(
      @NotBlank String orderId,
      @NotBlank String userSub,
      @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
      @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency) {
  }
}
