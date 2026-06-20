package com.example.commerce.order.service;

public final class OrderNotFoundException extends RuntimeException {

  public OrderNotFoundException(String message) {
    super(message);
  }
}
