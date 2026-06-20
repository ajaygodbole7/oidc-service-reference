package com.example.commerce.order.service;

public record PaymentAuthorization(String authorizationId) {

  public PaymentAuthorization {
    if (authorizationId == null || authorizationId.isBlank()) {
      throw new IllegalArgumentException("authorizationId is required");
    }
  }
}
