package com.example.commerce.payment.web;

import com.example.commerce.payment.domain.PaymentAuthorization;

record PaymentResponse(String paymentId, String orderId, String status, String currency, long amountCents) {

  static PaymentResponse from(PaymentAuthorization authorization) {
    return new PaymentResponse(
        authorization.paymentId(),
        authorization.orderId().value(),
        authorization.status(),
        authorization.amount().currency(),
        authorization.amount().amount().movePointRight(2).longValueExact());
  }
}
