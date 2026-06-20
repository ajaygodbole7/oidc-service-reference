package com.example.commerce.catalog.domain;

public record ProductName(String value) {

  public ProductName {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("product name is required");
    }
  }
}
