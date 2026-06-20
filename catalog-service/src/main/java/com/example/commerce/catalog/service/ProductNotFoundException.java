package com.example.commerce.catalog.service;

public final class ProductNotFoundException extends RuntimeException {

  public ProductNotFoundException(String message) {
    super(message);
  }
}
