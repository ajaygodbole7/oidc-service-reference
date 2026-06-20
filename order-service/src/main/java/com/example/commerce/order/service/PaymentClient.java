package com.example.commerce.order.service;

public interface PaymentClient {

  PaymentAuthorization authorize(PaymentAuthorizationCommand command);
}
