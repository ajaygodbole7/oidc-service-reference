package com.example.commerce.catalog.domain;

public record ProductId(String value) {

  public ProductId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("product id is required");
    }
  }
}
