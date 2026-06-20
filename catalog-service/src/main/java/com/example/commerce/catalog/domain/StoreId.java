package com.example.commerce.catalog.domain;

public record StoreId(String value) {

  public StoreId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("store id is required");
    }
  }
}
